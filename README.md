# Alert Triage Agent V0.2

企业安全告警研判 Agent。V0.2 将 V0.1 的内存关键词检索替换为真正的 Dense Vector RAG：

```text
知识文档 / 历史案例
        ↓
DashScope text-embedding-v4（document）
        ↓
PostgreSQL + pgvector
        ↓
告警检索 Query
        ↓
DashScope text-embedding-v4（query）
        ↓
Top-K 相似度检索 + Metadata 过滤
        ↓
Spring AI Alibaba ReactAgent
        ↓
DeepSeek 研判
        ↓
Java 结构校验与治理规则
```

DeepSeek 是生成与推理模型；Embedding 使用独立的向量模型。两者不需要来自同一个供应商。

## 技术栈

- Java 21
- Spring Boot 3.5.10
- Spring AI 1.1.2
- Spring AI Alibaba 1.1.2.3
- DeepSeek OpenAI-compatible Chat API
- Alibaba Cloud Model Studio `text-embedding-v4`
- PostgreSQL + pgvector HNSW

## V0.2 已实现

- DeepSeek 告警研判，默认 `deepseek-v4-flash`
- DashScope `text-embedding-v4`，默认 1024 维
- 区分 `text_type=query` 与 `text_type=document`
- pgvector 持久化、HNSW、余弦相似度
- 历史案例与制度规则分别进行 Metadata 过滤
- 知识新增、批量写入、删除、搜索、种子重建 API
- 确定性文档 ID，重复写入执行 Upsert
- 应用启动时幂等写入示例知识
- 告警响应返回召回 Query、模型、向量维度、阈值和每条召回分数
- Spring AI Alibaba `ReactAgent` 与 Spring AI `ChatClient` 双引擎
- CRITICAL、证据不足、低置信度告警强制人工复核
- GitHub Actions 单元测试

V0.2 暂时是 Dense-only RAG。BM25/Sparse、RRF、Reranker 和 StateGraph 放在后续版本。

## 快速启动

### 1. 准备环境变量

```bash
cp .env.example .env
```

至少填写：

```dotenv
DEEPSEEK_API_KEY=你的-DeepSeek-Key
AI_DASHSCOPE_API_KEY=你的-DashScope-Key
```

### 2. 启动 pgvector

```bash
docker compose up -d postgres
```

### 3. 启动应用

```bash
set -a
source .env
set +a
mvn spring-boot:run
```

应用启动时会：

1. 创建 pgvector 扩展和 `public.alert_knowledge` 表；
2. 使用 `text-embedding-v4` 为 `knowledge/seed.json` 生成向量；
3. 幂等写入 4 条历史案例和 4 条制度规则。

不希望自动写入种子数据时：

```bash
export RAG_SEED_ON_STARTUP=false
```

### 完整 Docker Compose

```bash
cp .env.example .env
# 编辑 .env

docker compose --profile app up --build
```

## API

### 告警研判

```bash
bash scripts/demo.sh
```

或：

```bash
curl -X POST 'http://localhost:8080/api/v1/alert-analyses' \
  -H 'Content-Type: application/json' \
  -d '{
    "alertId": "ALT-20260813-001",
    "alertType": "AUTH_BRUTE_FORCE",
    "title": "单账户短时间内连续登录失败",
    "description": "5 分钟内发生 137 次登录失败，来源为多个异常 IP，随后出现一次成功登录。",
    "severity": "HIGH",
    "source": "iam",
    "assetId": "user:demo-account",
    "occurredAt": "2026-08-13T05:30:00Z",
    "attributes": {
      "failedAttempts": 137,
      "sourceIpCount": 19,
      "followedBySuccess": true
    }
  }'
```

响应中的 `retrieval` 会保留本次 RAG 证据链：

```json
{
  "retrieval": {
    "query": "AUTH_BRUTE_FORCE ...",
    "embeddingModel": "text-embedding-v4",
    "embeddingDimensions": 1024,
    "vectorStore": "pgvector",
    "similarityThreshold": 0.35,
    "historicalCases": [
      {
        "sourceType": "HISTORICAL_CASE",
        "sourceId": "CASE-2026-001",
        "title": "分布式暴力破解后账户被接管",
        "score": 0.91
      }
    ],
    "policyRules": []
  }
}
```

### 直接测试向量检索

```bash
curl --get 'http://localhost:8080/api/v1/knowledge/search' \
  --data-urlencode 'query=多个异常IP连续登录失败后成功登录' \
  --data-urlencode 'type=HISTORICAL_CASE' \
  --data-urlencode 'topK=5'
```

### 新增或更新知识

同一个 `type + sourceId + version` 会得到相同 UUID，因此重复提交是 Upsert：

```bash
curl -X POST 'http://localhost:8080/api/v1/knowledge' \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "POLICY_RULE",
    "sourceId": "POL-CLOUD-005",
    "title": "云访问密钥泄露处置规范",
    "content": "发现访问密钥疑似泄露时，应立即轮换密钥、核查调用记录并评估影响范围。",
    "keywords": ["AccessKey", "密钥泄露", "异常调用"],
    "recommendedActions": ["轮换密钥", "核查调用记录", "评估影响范围"],
    "version": "1",
    "active": true
  }'
```

### 重建内置种子

```bash
curl -X POST 'http://localhost:8080/api/v1/knowledge/reindex-seed'
```

### 删除某个版本

```bash
curl -X DELETE \
  'http://localhost:8080/api/v1/knowledge/POLICY_RULE/POL-CLOUD-005?version=1'
```

## 关键配置

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `DEEPSEEK_API_KEY` | 无 | DeepSeek Chat API Key |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | 研判模型 |
| `AI_DASHSCOPE_API_KEY` | 无 | Embedding API Key |
| `EMBEDDING_MODEL` | `text-embedding-v4` | 向量模型 |
| `EMBEDDING_DIMENSIONS` | `1024` | pgvector 表维度；修改后需重建表 |
| `RAG_SIMILARITY_THRESHOLD` | `0.35` | 最低相似度 |
| `RAG_INGEST_BATCH_SIZE` | `10` | V4 单批写入上限 |
| `RAG_SEED_ON_STARTUP` | `true` | 启动时写入种子知识 |
| `MAX_HISTORICAL_CASES` | `3` | 告警研判召回案例数 |
| `MAX_POLICY_RULES` | `3` | 告警研判召回制度数 |
| `ALERT_AGENT_ENGINE` | `react-agent` | 可切换 `chat-client` |
| `HUMAN_REVIEW_THRESHOLD` | `0.85` | 低于阈值强制人工复核 |

> 更换 Embedding 模型或维度时，旧向量不可混用。开发环境可删除 Docker Volume 后重建；生产环境应创建新表/新索引并执行蓝绿迁移。

## 目录结构

```text
src/main/java/com/example/alertagent
├── api              告警和知识 REST API
├── application      检索编排、Prompt、模型调用、输出校验
├── config           Agent、Embedding 与 RAG 配置
├── domain           告警领域模型和 Retrieval Trace
├── infrastructure   pgvector 案例/制度 Repository
├── knowledge        ETL、Embedding、Index、Seed、Search
└── support          业务异常
```

## 测试

```bash
mvn clean verify
```

单元测试不会调用 DeepSeek、DashScope 或 PostgreSQL。端到端测试需要准备两个 API Key 和 pgvector。

## 安全边界

- 模型只输出研判与建议，不自动执行封禁、隔离、删除等高风险动作。
- 告警文本被视为不可信输入，不能改变系统角色或工具权限。
- 检索由 Java 确定执行，模型不能跳过案例/制度召回。
- 模型输出必须经过 JSON 解析、枚举/范围校验和治理规则二次校验。
- 生产环境还应增加租户过滤、部门权限、知识生效时间、脱敏、审计和人工审批。

更多设计见：

- [`docs/architecture.md`](docs/architecture.md)
- [`docs/evolution-roadmap.md`](docs/evolution-roadmap.md)
