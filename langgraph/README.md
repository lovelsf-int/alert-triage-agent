# Alert Triage Agent — LangGraph 版

这是 Java/Spring AI Alibaba V0.2 的 Python/LangGraph 对照实现。它不是把控制器代码逐行翻译成 Python，而是把告警研判真正建模为一个可持久化、可中断、可恢复的状态图。

```text
START
  ↓
normalize_alert
  ├───────────────┐
  ↓               ↓
retrieve_cases   retrieve_policies
  └───────┬───────┘
          ↓
       assess
          ↓
       govern
      ┌───┴───────────────┐
      ↓                   ↓
human_review          finalize
  interrupt()             ↓
      ↓                   END
Command(resume=...)
      ↓
   finalize
      ↓
     END
```

## 核心能力

- LangGraph `StateGraph` 显式节点、条件边和并行 RAG 召回
- LangGraph PostgreSQL Checkpointer，按 `analysisId/thread_id` 保存每一步状态
- `interrupt()` + `Command(resume=...)` 实现真正的人工复核暂停和恢复
- DeepSeek V4 `deepseek-v4-flash`，通过 `langchain-deepseek` 生成 Pydantic 结构化结果
- DeepSeek 默认关闭 thinking mode，避免把内部推理当成业务审计内容，并提高 JSON 输出稳定性
- DashScope `text-embedding-v4`，查询使用 `text_type=query`，入库使用 `text_type=document`
- PostgreSQL 16 + pgvector HNSW + cosine similarity
- 历史案例与制度规则分支独立召回，随后在图中汇合
- 证据 ID 白名单校验、严重度不可降级、低置信度/CRITICAL/证据不足强制人工复核
- 知识 Upsert、批量入库、搜索、删除和种子重建 API
- 单元测试覆盖图路由、人工恢复、治理规则和 Embedding 请求格式

## 技术版本

- Python 3.12–3.14（Docker 使用 3.13）
- LangGraph 1.2.9
- LangGraph PostgreSQL Checkpoint 3.1.0
- LangChain DeepSeek 1.1.0
- FastAPI 0.128.2
- PostgreSQL 16 + pgvector

## 快速启动

### 1. 配置

```bash
cp .env.example .env
```

至少填写：

```dotenv
DEEPSEEK_API_KEY=你的-DeepSeek-Key
DASHSCOPE_API_KEY=你的-DashScope-Key
```

### 2. 启动 PostgreSQL/pgvector

```bash
docker compose up -d postgres
```

默认映射到本机 `5433`，因此本地运行应用时把 `.env` 中的连接改为：

```dotenv
DATABASE_URL=postgresql://alert_agent:alert_agent@localhost:5433/alert_agent
```

### 3. 安装并启动

```bash
python -m venv .venv
source .venv/bin/activate
python -m pip install -e ".[dev]"
uvicorn alert_triage_langgraph.main:app --reload --port 8081
```

完整容器方式：

```bash
docker compose --profile app up --build
```

## 告警研判 API

```bash
bash scripts/demo.sh
```

也可以直接调用：

```bash
curl -X POST 'http://localhost:8081/api/v1/alert-analyses' \
  -H 'Content-Type: application/json' \
  -d '{
    "alertId": "ALT-20260814-001",
    "alertType": "AUTH_BRUTE_FORCE",
    "title": "多来源登录失败后出现成功登录",
    "description": "5 分钟内发生 137 次登录失败，来自 19 个异常 IP，随后出现一次成功登录。",
    "severity": "HIGH",
    "source": "iam",
    "assetId": "user:demo-account",
    "attributes": {
      "failedAttempts": 137,
      "sourceIpCount": 19,
      "followedBySuccess": true
    }
  }'
```

高置信度且无需人工复核时返回：

```json
{
  "analysisId": "...",
  "status": "COMPLETED",
  "assessment": { "requiresHumanReview": false }
}
```

需要人工复核时 HTTP 状态为 `202`，图停在 `human_review` 节点：

```json
{
  "analysisId": "...",
  "status": "WAITING_FOR_REVIEW",
  "interrupt": {
    "question": "请复核该安全告警研判结果",
    "assessment": {}
  }
}
```

使用同一个 `analysisId` 恢复：

```bash
curl -X POST \
  'http://localhost:8081/api/v1/alert-analyses/ANALYSIS_ID/reviews' \
  -H 'Content-Type: application/json' \
  -d '{
    "approved": true,
    "comment": "证据与登录审计一致，同意研判结论"
  }'
```

拒绝结论：

```json
{
  "approved": false,
  "comment": "需要补充终端和 MFA 日志"
}
```

人工也可以提交 `adjustedAssessment`。调整后的证据引用仍会经过 ID 白名单和治理规则校验。

查询运行状态：

```bash
curl 'http://localhost:8081/api/v1/alert-analyses/ANALYSIS_ID'
```

## 知识 API

向量搜索：

```bash
curl --get 'http://localhost:8081/api/v1/knowledge/search' \
  --data-urlencode 'query=多个异常IP登录失败后成功登录' \
  --data-urlencode 'type=HISTORICAL_CASE' \
  --data-urlencode 'topK=5'
```

重建内置知识：

```bash
curl -X POST 'http://localhost:8081/api/v1/knowledge/reindex-seed'
```

新增或更新知识：

```bash
curl -X POST 'http://localhost:8081/api/v1/knowledge' \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "POLICY_RULE",
    "sourceId": "POL-CLOUD-005",
    "title": "云访问密钥泄露处置规范",
    "content": "发现访问密钥疑似泄露时，应立即轮换密钥并核查调用记录。",
    "keywords": ["AccessKey", "密钥泄露"],
    "recommendedActions": ["轮换密钥", "核查调用记录"],
    "version": "1",
    "active": true
  }'
```

## 为什么检索节点不是 Tool Calling

告警研判属于高审计要求流程。此版本让图强制执行案例和制度检索，模型不能跳过、重复调用或改变检索权限。LangGraph负责流程编排，DeepSeek负责非确定性研判。

后续确实需要 MCP/Tools 时，可在独立只读子图中加入 `ToolNode` 或 MCP Client，但高风险写操作仍应放在 `interrupt()` 之后，并要求 RBAC、幂等键和审计。

## 测试

```bash
ruff check .
pytest -q
```

单元测试不访问 DeepSeek、DashScope 或 PostgreSQL。端到端运行才需要外部服务。

## 安全边界

- 不自动封禁、隔离、删除或修改生产资源。
- 告警正文被标记为不可信数据，不能修改系统指令。
- 不把 DeepSeek 的 `reasoning_content` 暴露为审计依据。
- 只接受召回结果中真实存在的案例、制度和告警 ID。
- Checkpoint 建议设置 `LANGGRAPH_STRICT_MSGPACK=true`。
- 生产环境仍需增加租户过滤、脱敏、知识有效期、RBAC/ABAC 和不可抵赖审计。
