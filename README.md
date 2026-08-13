# Alert Triage Agent V0.2

面向企业安全运营的防御性告警研判示例。V0.2 将历史案例与制度规则写入 pgvector，通过独立 Embedding 模型完成语义召回，再由 DeepSeek 生成结构化研判结果，最后由 Java 规则执行置信度、证据和人工复核校验。

```text
告警请求
  -> 构造检索 Query
  -> DashScope Embedding
  -> pgvector Top-K 召回
  -> Spring AI Alibaba ReactAgent / ChatClient
  -> DeepSeek 结构化研判
  -> Java 治理规则
```

## 主要能力

- DeepSeek OpenAI-compatible Chat API
- DashScope `text-embedding-v4`
- Query 与 Document 使用不同的 Embedding task
- PostgreSQL + pgvector + HNSW + cosine distance
- 历史案例、制度规则 Metadata 隔离
- 知识 Upsert、搜索、删除和种子重建 API
- Retrieval Trace：查询、模型、维度、阈值和召回分数
- Spring AI Alibaba `ReactAgent` 与 Spring AI `ChatClient` 双引擎
- 低置信度、证据不足和高等级事件强制人工复核
- GitHub Actions 单元测试

V0.2 是 Dense-only RAG 基线。BM25、RRF、Reranker、StateGraph Checkpoint 和持久化人工审批放在后续版本。

## 技术栈

- Java 21
- Spring Boot 3.5.10
- Spring AI 1.1.2
- Spring AI Alibaba 1.1.2.3
- PostgreSQL 16 + pgvector

## 快速启动

### 1. 配置环境变量

```bash
cp .env.example .env
```

至少设置：

```dotenv
DEEPSEEK_API_KEY=replace-me
AI_DASHSCOPE_API_KEY=replace-me
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

也可以运行完整容器环境：

```bash
docker compose --profile app up --build
```

## API

### 告警研判

```bash
curl -X POST 'http://localhost:8080/api/v1/alert-analyses' \
  -H 'Content-Type: application/json' \
  -d '{
    "alertId": "EVENT-001",
    "alertType": "AUTH_ANOMALY",
    "title": "Unusual sign-in sequence",
    "description": "Several failed attempts were followed by a successful sign-in.",
    "severity": "HIGH",
    "source": "iam",
    "assetId": "user:demo",
    "attributes": {"failedAttempts": 12}
  }'
```

### 向量检索

```bash
curl --get 'http://localhost:8080/api/v1/knowledge/search' \
  --data-urlencode 'query=unusual sign-in sequence' \
  --data-urlencode 'type=HISTORICAL_CASE' \
  --data-urlencode 'topK=5'
```

### 重建内置知识

```bash
curl -X POST 'http://localhost:8080/api/v1/knowledge/reindex-seed'
```

内置知识以 `src/main/resources/knowledge/seed.b64` 保存，应用启动时解码为 JSON 并执行幂等 Upsert。设置 `RAG_SEED_ON_STARTUP=false` 可关闭自动写入。

## 常用配置

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `DEEPSEEK_API_KEY` | 无 | Chat API Key |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | 研判模型 |
| `AI_DASHSCOPE_API_KEY` | 无 | Embedding API Key |
| `EMBEDDING_MODEL` | `text-embedding-v4` | 向量模型 |
| `EMBEDDING_DIMENSIONS` | `1024` | pgvector 向量维度 |
| `RAG_SIMILARITY_THRESHOLD` | `0.35` | 最低相似度 |
| `RAG_INGEST_BATCH_SIZE` | `10` | 入库批大小 |
| `ALERT_AGENT_ENGINE` | `react-agent` | 可切换为 `chat-client` |
| `HUMAN_REVIEW_THRESHOLD` | `0.85` | 人工复核阈值 |

更换 Embedding 模型或维度后，不应与旧向量混用。生产环境应创建新索引并执行迁移，而不是直接覆盖原表。

## 测试

```bash
mvn clean verify
```

单元测试不调用外部模型或数据库；端到端运行需要 DeepSeek、DashScope 和 pgvector。

## 安全边界

- 项目只生成研判结果和处置建议，不自动执行高风险动作。
- 检索由 Java 确定执行，模型不能跳过案例或制度召回。
- 输入告警按不可信内容处理，不能改变系统角色或权限。
- 模型输出经过 JSON 解析、字段校验和治理规则二次校验。
- 生产环境仍需补充租户隔离、权限控制、脱敏、审计和人工审批。

设计文档：

- [`docs/architecture.md`](docs/architecture.md)
- [`docs/evolution-roadmap.md`](docs/evolution-roadmap.md)
