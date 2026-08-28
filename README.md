# Alert Triage Agent V0.3

面向企业安全运营的防御性告警研判示例。V0.3 在原有 pgvector RAG 与 DeepSeek 研判链路之上，加入 Elasticsearch 持久化任务状态和 Java 21 虚拟线程异步执行能力。

```text
POST 告警
  -> Elasticsearch CREATE(PENDING)
  -> 有界并发调度器
  -> Java 21 Virtual Thread
  -> ES 乐观锁抢占(RUNNING + runId + lease)
  -> pgvector RAG
  -> DeepSeek 结构化研判
  -> Java 治理规则
  -> ES 乐观锁回写(SUCCEEDED / RETRY_WAIT / DEAD)
```

Elasticsearch 保存的是可恢复的任务状态，不使用 `@Transactional` 伪装跨资源事务。应用在“ES 已写入、线程尚未启动”或“AI 正在执行时”宕机后，恢复扫描会重新调度 `PENDING`、到期的 `RETRY_WAIT` 和租约过期的 `RUNNING` 任务。

## 主要能力

- Java 21 虚拟线程执行阻塞式 RAG 与模型调用
- `Semaphore` 独立限制 AI 在途并发，避免无限打满下游
- Elasticsearch 固定业务 ID 与 `op_type=create`，重复提交不产生重复文档
- `SeqNoPrimaryTerm` 乐观锁保护状态迁移
- `runId + lease` 防止旧任务覆盖新任务
- 指数退避重试、最大尝试次数和 `DEAD` 终态
- 定时恢复应用宕机、线程丢失及超时任务
- 保留同步接口，便于调试与兼容旧调用方
- DeepSeek OpenAI-compatible Chat API
- DashScope `text-embedding-v4`
- PostgreSQL + pgvector + HNSW + cosine distance
- 历史案例、制度规则 Metadata 隔离
- Retrieval Trace 与人工复核治理

## 技术栈

- Java 21
- Spring Boot 3.5.10
- Spring Data Elasticsearch 5.5.x
- Elasticsearch 8.18.8
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

### 2. 启动 pgvector 与 Elasticsearch

```bash
docker compose up -d postgres elasticsearch
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

### 1. 异步提交告警研判

```bash
curl -i -X POST 'http://localhost:8080/api/v1/alert-analyses' \
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

接口立即返回 `202 Accepted`：

```json
{
  "alertId": "EVENT-001",
  "status": "PENDING",
  "created": true,
  "dispatched": true,
  "submittedAt": "2026-08-29T00:00:00Z",
  "statusUrl": "/api/v1/alert-analyses/EVENT-001"
}
```

重复提交同一个 `alertId` 不会覆盖已有任务；返回中的 `created` 会变为 `false`。

### 2. 查询任务状态与结果

```bash
curl 'http://localhost:8080/api/v1/alert-analyses/EVENT-001'
```

状态可能为：

- `PENDING`：已经持久化，等待调度。
- `RUNNING`：某个 Worker 已持有租约并执行研判。
- `RETRY_WAIT`：临时失败，等待退避时间到达。
- `SUCCEEDED`：研判成功，`result` 包含完整结果。
- `DEAD`：超过最大尝试次数，需要人工检查。

### 3. 同步研判兼容接口

```bash
curl -X POST 'http://localhost:8080/api/v1/alert-analyses/sync' \
  -H 'Content-Type: application/json' \
  -d @alert.json
```

该接口会等待模型完成后返回 `201 Created`，仅建议用于调试或兼容旧调用方。

### 4. 向量检索

```bash
curl --get 'http://localhost:8080/api/v1/knowledge/search' \
  --data-urlencode 'query=unusual sign-in sequence' \
  --data-urlencode 'type=HISTORICAL_CASE' \
  --data-urlencode 'topK=5'
```

### 5. 重建内置知识

```bash
curl -X POST 'http://localhost:8080/api/v1/knowledge/reindex-seed'
```

## 异步任务正确性

### 为什么不使用 `@Transactional`

当前链路只持久化 Elasticsearch。Spring 数据库事务不能回滚 ES，因此 V0.3 使用 ES 单文档原子写和乐观锁，而不是数据库事务。

### 如何避免任务丢失

1. 告警先以 `PENDING` 写入 ES。
2. 只有持久化成功后才尝试创建虚拟线程。
3. 没有并发名额时，任务继续留在 `PENDING`。
4. 恢复扫描会重新提交未完成任务。

### 如何避免多实例重复有效执行

多个实例可能同时扫描到一个告警，但都必须通过 ES 乐观锁执行状态迁移。只有一个实例能把当前版本更新为 `RUNNING`。

### 如何避免旧结果覆盖新结果

每次抢占都会生成新的 `runId`。完成、重试和失败回写都要求文档仍处于 `RUNNING`，且 `runId` 与当前 Worker 相同。租约过期后重新抢占的任务会获得新 `runId`，旧 Worker 的结果会被拒绝。

## 常用配置

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `ELASTICSEARCH_URIS` | `http://localhost:9200` | ES 节点地址 |
| `ALERT_ANALYSIS_MAX_IN_FLIGHT` | `64` | 单实例最大 AI 在途任务数 |
| `ALERT_ANALYSIS_LEASE` | `90s` | RUNNING 租约，应大于模型硬超时 |
| `ALERT_ANALYSIS_MAX_ATTEMPTS` | `5` | 最大执行次数 |
| `ALERT_ANALYSIS_BASE_RETRY_DELAY` | `2s` | 首次退避 |
| `ALERT_ANALYSIS_MAX_RETRY_DELAY` | `5m` | 最大退避 |
| `ALERT_ANALYSIS_RECOVERY_BATCH_SIZE` | `200` | 每轮恢复扫描上限 |
| `ALERT_ANALYSIS_RECOVERY_INTERVAL_MS` | `5000` | 恢复扫描间隔 |
| `ALERT_ANALYSIS_OPTIMISTIC_RETRIES` | `5` | ES 乐观锁冲突重试次数 |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | 研判模型 |
| `EMBEDDING_MODEL` | `text-embedding-v4` | 向量模型 |
| `EMBEDDING_DIMENSIONS` | `1024` | pgvector 向量维度 |
| `RAG_SIMILARITY_THRESHOLD` | `0.35` | 最低相似度 |
| `ALERT_AGENT_ENGINE` | `react-agent` | 可切换为 `chat-client` |
| `HUMAN_REVIEW_THRESHOLD` | `0.85` | 人工复核阈值 |

`ALERT_ANALYSIS_MAX_IN_FLIGHT` 不是虚拟线程数量上限，而是模型、HTTP 连接池、RAG 和 ES 共同允许的业务并发上限。应通过压测逐步调整。

## 测试

```bash
mvn clean verify
```

单元测试不调用外部模型、PostgreSQL 或 Elasticsearch；端到端运行需要 DeepSeek、DashScope、pgvector 和 Elasticsearch。

## 安全边界

- 项目只生成研判结果和处置建议，不自动执行高风险动作。
- 检索由 Java 确定执行，模型不能跳过案例或制度召回。
- 输入告警按不可信内容处理，不能改变系统角色或权限。
- 模型输出经过 JSON 解析、字段校验和治理规则二次校验。
- 生产环境仍需补充租户隔离、权限控制、脱敏、审计和人工审批。

设计文档：

- [`docs/architecture.md`](docs/architecture.md)
- [`docs/evolution-roadmap.md`](docs/evolution-roadmap.md)
