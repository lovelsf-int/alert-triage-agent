# V0.3 部署、容量与故障处理手册

## 1. 本地启动

复制配置：

```bash
cp .env.example .env
```

至少填写：

```dotenv
DEEPSEEK_API_KEY=replace-me
AI_DASHSCOPE_API_KEY=replace-me
```

启动依赖：

```bash
docker compose up -d postgres elasticsearch
```

启动应用：

```bash
set -a
source .env
set +a
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:9200/_cluster/health
```

## 2. 生产部署检查

### 2.1 应用实例

- 使用 Java 21 或更高版本；
- 每个实例使用独立 `Semaphore` 和虚拟线程执行器；
- 设置优雅停机，并确保负载均衡先摘流量再终止实例；
- 进程重启不能依赖内存队列恢复，未完成任务由 ES 状态和租约恢复；
- `ALERT_ANALYSIS_MAX_IN_FLIGHT` 按整个集群容量拆分，而不是每个实例都填写下游总并发上限。

### 2.2 Elasticsearch

- 为任务索引配置副本、磁盘水位、快照和容量告警；
- `status`、`runId` 使用 keyword，时间字段使用 date，`requestJson/resultJson/lastError` 不建立全文索引；
- 对 `status + nextRetryAt`、`status + leaseUntil`、`updatedAt` 的查询负载进行压测；
- 控制 Recovery Scheduler 的频率和批量，避免每个实例高频大范围扫描；
- 生产环境使用显式索引模板和受控建索引流程，不依赖运行时自动创建；
- 迁移到写别名时必须同步修改代码索引坐标，并完成兼容性验证。

### 2.3 PostgreSQL + pgvector

- 数据库连接池大小按数据库有效并发配置，不随虚拟线程数量增长；
- HNSW 参数、Embedding 维度和距离类型必须与知识数据一致；
- 更换 Embedding 模型或维度时创建新知识索引并重建，不与旧向量混用；
- 慢查询、连接池等待和召回结果为空必须进入监控。

### 2.4 模型与 Embedding 服务

- 设置连接超时、读取超时和整体调用硬超时；
- 对 429、502、503、504 和网络超时进行可重试分类；
- 对输入过长、认证失败、结构永久不合法等错误设置不可重试或有限重试策略；
- 不在日志中打印完整敏感告警、API Key 或模型内部推理内容；
- 模型硬超时必须小于任务租约。

## 3. 核心配置关系

| 配置 | 默认值 | 生产含义 |
|---|---:|---|
| `ALERT_ANALYSIS_MAX_IN_FLIGHT` | `64` | 单实例 AI 在途上限 |
| `ALERT_ANALYSIS_LEASE` | `90s` | Worker 所有权租约 |
| `ALERT_ANALYSIS_MAX_ATTEMPTS` | `5` | 最大抢占执行次数 |
| `ALERT_ANALYSIS_BASE_RETRY_DELAY` | `2s` | 首次重试退避 |
| `ALERT_ANALYSIS_MAX_RETRY_DELAY` | `5m` | 最大退避 |
| `ALERT_ANALYSIS_RECOVERY_BATCH_SIZE` | `200` | 每轮候选查询上限 |
| `ALERT_ANALYSIS_RECOVERY_INTERVAL_MS` | `5000` | 扫描间隔 |
| `ALERT_ANALYSIS_OPTIMISTIC_RETRIES` | `5` | 单次状态迁移冲突重读次数 |
| `ELASTICSEARCH_CONNECTION_TIMEOUT` | `3s` | 建连超时 |
| `ELASTICSEARCH_SOCKET_TIMEOUT` | `30s` | ES 请求读取超时 |

推荐约束：

```text
modelHardTimeout < lease
recoveryInterval << acceptableRecoveryDelay
recoveryBatchSize >= typicalAvailableSlots
replicas × maxInFlightPerInstance <= downstreamSafeConcurrency
```

## 4. 容量估算

### 4.1 Little's Law 估算 AI 在途量

```text
所需平均并发 ≈ 目标吞吐量 × 平均研判耗时
```

例如：

```text
目标吞吐量：10 个告警/秒
平均研判耗时：5 秒
平均在途量：50
```

考虑 P95/P99、重试和抖动后，可以从 64 开始压测，但最终值必须同时受以下资源约束：

```text
模型并发
Embedding 并发
HTTP 连接池
pgvector 查询并发
ES 更新并发
应用内存
```

### 4.2 集群并发

```text
集群理论在途量 = 应用副本数 × 单实例 maxInFlight
```

建议为下游至少保留 20%～30% 余量，用于流量波动、重试和单实例故障后的重新分配。

### 4.3 租约估算

```text
lease = P99(RAG + LLM + Governance + ES writeback) + safetyMargin
```

租约过短会导致同一任务被并行重跑；租约过长会延迟故障恢复。模型客户端硬超时是租约设计的前提。

## 5. 监控指标与告警建议

### 5.1 任务状态

| 指标 | 建议告警 |
|---|---|
| `PENDING` 总量 | 连续增长且完成吞吐没有同步增长 |
| 最老 `PENDING` 年龄 | 超过业务可接受排队时间 |
| `RUNNING` 总量 | 长期接近集群 maxInFlight |
| 已过期 `RUNNING` 数量 | 大于 0 持续多个扫描周期 |
| `RETRY_WAIT` 总量 | 短时间快速增长 |
| `DEAD` 新增量 | 任何非预期增长都应告警 |
| `attempt >= 3` 比例 | 明显高于历史基线 |
| 旧 `runId` 结果丢弃数 | 突增表示租约或超时配置不合理 |

### 5.2 下游依赖

- DeepSeek：QPS、并发、429、5xx、超时、P95/P99；
- DashScope：Embedding 延迟、错误率、限流；
- pgvector：查询延迟、连接池 active/waiting、慢 SQL；
- Elasticsearch：集群健康、磁盘水位、写入拒绝、查询延迟、乐观锁冲突、GC；
- 应用：CPU、堆、虚拟线程任务数、Semaphore 使用率、异常率。

### 5.3 端到端指标

```text
submit_to_success_seconds
submit_to_first_claim_seconds
claim_to_complete_seconds
retry_count_per_job
recovery_lag_seconds
```

## 6. 故障处理手册

### 6.1 PENDING 持续积压

检查顺序：

1. 查看应用实例是否健康，Recovery Scheduler 是否运行；
2. 查看 `availableSlots` 是否长期为 0；
3. 查看模型、Embedding、pgvector 和 ES 延迟是否上升；
4. 查看是否存在大量长耗时 `RUNNING`；
5. 对比集群实际并发与下游限额；
6. 必要时先降低入口速率，不要直接无限提高 `maxInFlight`。

### 6.2 RUNNING 大量租约过期

可能原因：

- 模型硬超时大于 lease；
- HTTP 客户端没有硬超时；
- 应用频繁重启或 OOM；
- ES 回写失败；
- RAG 查询被数据库连接池阻塞。

处理：

1. 对比实际 P99 与 lease；
2. 检查进程重启、OOM 和网络错误；
3. 检查旧 `runId` 丢弃日志；
4. 修复依赖后再调整 lease；
5. 不要仅把 lease 无限增大，否则会拖慢真正宕机任务的恢复。

### 6.3 RETRY_WAIT 快速增长

1. 按 `errorType` 聚合错误；
2. 区分 429、5xx、网络超时、解析失败和输入问题；
3. 429 时降低并发或入口速率；
4. 下游故障时保持指数退避，避免重试风暴；
5. 永久性错误应尽快进入 `DEAD`，不要反复消耗模型额度。

### 6.4 DEAD 出现

1. 查询 `lastError`、`attempt`、请求摘要和依赖日志；
2. 判断是输入数据、权限、模型配置、知识库还是代码缺陷；
3. 修复根因后使用新的 `alertId` 重新提交；
4. 在显式 re-run API 上线前，不要直接修改 ES 文档强行改回 `PENDING`，以免破坏审计和幂等语义。

### 6.5 乐观锁冲突升高

少量冲突是多实例竞争的正常现象。异常升高时检查：

- Recovery 扫描间隔是否过短；
- 应用副本是否过多；
- 同一批任务是否被多次重复 dispatch；
- 热点 `alertId` 是否被客户端持续重试；
- ES 更新延迟是否升高。

## 7. 发布步骤

1. 先部署或确认 Elasticsearch、pgvector 和模型依赖；
2. 检查任务索引 mapping 与应用版本兼容；
3. 小流量部署 1 个实例，验证提交、状态查询、成功、重试和恢复；
4. 人为终止一个正在执行的实例，验证租约到期后能被其他实例接管；
5. 扩容应用副本，同时按下游容量重新计算每实例 `maxInFlight`；
6. 观察至少一个业务高峰周期；
7. 再逐步扩大入口流量。

## 8. 回滚策略

- 回滚应用前确认新旧版本都能读取当前 ES 文档字段；
- 不删除任务索引，不清空 `PENDING/RUNNING/RETRY_WAIT`；
- 如果回滚到只支持同步接口的旧版本，应先停止异步入口并处理未完成任务，否则旧版本不会恢复这些任务；
- 优先回滚流量和应用版本，不回滚已经成功写入的研判结果；
- 保留合并前镜像和配置，记录变更时间点。

## 9. 上线验收清单

- [ ] 异步 POST 返回 `202`；
- [ ] ES 中先出现 `PENDING`；
- [ ] Worker 抢占后出现 `RUNNING + runId + leaseUntil`；
- [ ] 成功后出现 `SUCCEEDED + resultJson`；
- [ ] 重复 `alertId` 不覆盖原请求；
- [ ] 两个实例同时抢占时只有一个有效执行；
- [ ] 进程在持久化后、调度前终止，任务能恢复；
- [ ] 进程在 AI 执行中终止，租约到期后能恢复；
- [ ] 旧 Worker 迟到不能覆盖新结果；
- [ ] 临时异常进入 `RETRY_WAIT` 并按退避重试；
- [ ] 达到上限进入 `DEAD`；
- [ ] 模型、Embedding、pgvector 和 ES 均配置硬超时；
- [ ] 任务积压、租约过期、重试和 DEAD 均有告警；
- [ ] 敏感请求与结果已完成脱敏、权限和审计检查。
