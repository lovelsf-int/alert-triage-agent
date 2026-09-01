# V0.3 告警研判时序图

本文与 `main` 分支 V0.3 实现一致，覆盖正常请求、重复提交、多实例竞争、宕机恢复、旧结果拒绝、重试和状态查询。

## 1. 正常异步研判

```mermaid
sequenceDiagram
    autonumber
    actor Client as 调用方
    participant Controller as AlertAnalysisController
    participant Async as AsyncAlertAnalysisService
    participant Store as ES JobStore
    participant ES as Elasticsearch
    participant Dispatcher as TaskDispatcher
    participant VT as Virtual Thread
    participant Worker as AlertAnalysisWorker
    participant RAG as pgvector RAG
    participant LLM as DeepSeek
    participant Gov as Java Governance

    Client->>Controller: POST /api/v1/alert-analyses
    Controller->>Async: submit(request)
    Async->>Store: createPending(alertId)
    Store->>ES: CREATE document, status=PENDING
    ES-->>Store: created
    Async->>Store: findById(alertId)
    Store->>ES: GET document
    ES-->>Store: PENDING
    Async->>Dispatcher: dispatch(alertId)
    Dispatcher->>Dispatcher: Semaphore.tryAcquire()
    Dispatcher->>VT: execute(worker.process)
    Dispatcher-->>Async: dispatched=true
    Async-->>Controller: submission metadata
    Controller-->>Client: 202 Accepted

    VT->>Worker: process(alertId)
    Worker->>Store: tryClaim(runId, leaseUntil)
    Store->>ES: GET + conditional SAVE
    ES-->>Store: RUNNING, attempt+1
    Store-->>Worker: claimed job
    Worker->>RAG: 检索案例与制度
    RAG-->>Worker: EnrichedAlertContext
    Worker->>LLM: 结构化研判
    LLM-->>Worker: raw result
    Worker->>Gov: parse + enforce governance
    Gov-->>Worker: AlertAnalysisResponse
    Worker->>Store: complete(alertId, runId, result)
    Store->>ES: conditional SAVE status=SUCCEEDED
    ES-->>Store: updated
    Worker-->>VT: done
    VT->>Dispatcher: release Semaphore
```

关键点：`202 Accepted` 通常先于 AI 完成返回；任务可靠性依赖 ES 中的持久化状态，不依赖内存 Future。

## 2. 重复提交同一个 alertId

```mermaid
sequenceDiagram
    autonumber
    actor Client as 调用方
    participant API as Controller
    participant Async as AsyncService
    participant Store as ES JobStore
    participant ES as Elasticsearch
    participant Dispatcher as Dispatcher

    Client->>API: 第一次 POST alertId=EVENT-001
    API->>Async: submit(request)
    Async->>Store: createPending(EVENT-001)
    Store->>ES: CREATE _id=EVENT-001
    ES-->>Store: 201 Created
    Async->>Dispatcher: dispatch(EVENT-001)
    API-->>Client: 202, created=true

    Client->>API: 重复 POST alertId=EVENT-001
    API->>Async: submit(request)
    Async->>Store: createPending(EVENT-001)
    Store->>ES: CREATE _id=EVENT-001
    ES-->>Store: version conflict
    Store->>ES: existsById(EVENT-001)
    ES-->>Store: true
    Store-->>Async: created=false
    Async->>Store: findById(EVENT-001)
    Store-->>Async: 已有任务状态
    opt 已有任务仍可被抢占
        Async->>Dispatcher: dispatch(EVENT-001)
    end
    API-->>Client: 202, created=false
```

同一 `alertId` 被视为同一不可变任务。重复请求中的新 payload 不会覆盖原 payload。

## 3. 多实例同时抢占同一任务

```mermaid
sequenceDiagram
    autonumber
    participant SchedulerA as App A Scheduler
    participant WorkerA as App A Worker
    participant SchedulerB as App B Scheduler
    participant WorkerB as App B Worker
    participant ES as Elasticsearch

    par 两个实例扫描
        SchedulerA->>ES: 查询 PENDING
        SchedulerB->>ES: 查询 PENDING
    end
    ES-->>SchedulerA: EVENT-001
    ES-->>SchedulerB: EVENT-001

    par 两个 Worker 尝试抢占
        WorkerA->>ES: GET EVENT-001
        WorkerB->>ES: GET EVENT-001
    end
    ES-->>WorkerA: PENDING, seqNo=10, primaryTerm=2
    ES-->>WorkerB: PENDING, seqNo=10, primaryTerm=2

    WorkerA->>ES: SAVE RUNNING, if_seq_no=10, if_primary_term=2
    ES-->>WorkerA: success, seqNo=11

    WorkerB->>ES: SAVE RUNNING, if_seq_no=10, if_primary_term=2
    ES-->>WorkerB: optimistic lock conflict
    WorkerB->>ES: 重新 GET EVENT-001
    ES-->>WorkerB: RUNNING, runId=A
    WorkerB-->>WorkerB: 当前不可抢占，停止

    WorkerA-->>WorkerA: 执行 AI 研判
```

允许多个实例看到同一个候选任务，但 ES 乐观锁保证只有一个状态迁移成功。

## 4. ES 已持久化、线程调度前宕机

```mermaid
sequenceDiagram
    autonumber
    actor Client as 调用方
    participant AppA as App A
    participant ES as Elasticsearch
    participant AppB as 重启后实例 / App B
    participant Recovery as Recovery Scheduler
    participant Worker as Worker

    Client->>AppA: POST 告警
    AppA->>ES: CREATE status=PENDING
    ES-->>AppA: created
    AppA--xAppA: 在 dispatch 前进程宕机

    Note over ES: 文档仍为 PENDING

    AppB->>Recovery: 定时触发
    Recovery->>ES: 查询 PENDING
    ES-->>Recovery: EVENT-001
    Recovery->>Worker: dispatch(EVENT-001)
    Worker->>ES: 抢占为 RUNNING
    ES-->>Worker: success
    Worker-->>Worker: 继续执行 AI
```

这是“先写 ES、后创建虚拟线程”的主要价值。

## 5. AI 执行过程中实例宕机

```mermaid
sequenceDiagram
    autonumber
    participant WorkerA as App A Worker
    participant ES as Elasticsearch
    participant RecoveryB as App B Recovery
    participant WorkerB as App B Worker
    participant AI as RAG + DeepSeek

    WorkerA->>ES: claim RUNNING, runId=A, leaseUntil=T1
    ES-->>WorkerA: success
    WorkerA->>AI: 执行研判
    WorkerA--xWorkerA: 进程宕机

    Note over ES: status=RUNNING 且 leaseUntil=T1
    Note over RecoveryB: 当前时间超过 T1

    RecoveryB->>ES: 查询 leaseUntil <= now 的 RUNNING
    ES-->>RecoveryB: EVENT-001
    RecoveryB->>WorkerB: dispatch(EVENT-001)
    WorkerB->>ES: claim RUNNING, runId=B, new lease
    ES-->>WorkerB: success
    WorkerB->>AI: 重新执行研判
    AI-->>WorkerB: result
    WorkerB->>ES: complete with runId=B
    ES-->>WorkerB: SUCCEEDED
```

任务可能被重新执行，因此 RAG 和模型调用必须无副作用。

## 6. 旧 Worker 迟到，不能覆盖新结果

```mermaid
sequenceDiagram
    autonumber
    participant WorkerA as 旧 Worker A
    participant ES as Elasticsearch
    participant WorkerB as 新 Worker B
    participant AI as AI Services

    WorkerA->>ES: claim runId=A, lease=T1
    ES-->>WorkerA: RUNNING A
    WorkerA->>AI: 慢请求

    Note over ES: T1 已过期
    WorkerB->>ES: re-claim runId=B, lease=T2
    ES-->>WorkerB: RUNNING B
    WorkerB->>AI: 执行研判
    AI-->>WorkerB: 新结果
    WorkerB->>ES: complete runId=B
    ES-->>WorkerB: SUCCEEDED

    AI-->>WorkerA: 旧结果迟到
    WorkerA->>ES: complete runId=A
    ES-->>WorkerA: 当前状态/runId 不匹配
    WorkerA-->>WorkerA: 丢弃旧结果并记录日志
```

`runId` 是业务所有权令牌，不能只依赖线程是否还活着。

## 7. 临时失败、指数退避与 DEAD

```mermaid
sequenceDiagram
    autonumber
    participant Recovery as Recovery Scheduler
    participant Worker as Worker
    participant ES as Elasticsearch
    participant AI as AI Services

    Worker->>ES: claim, attempt=1
    ES-->>Worker: RUNNING
    Worker->>AI: 调用模型
    AI-->>Worker: 429 / timeout / 5xx
    Worker->>ES: markRetry(nextRetryAt, lastError)
    ES-->>Worker: RETRY_WAIT

    loop nextRetryAt 到期且 attempt < maxAttempts
        Recovery->>ES: 查询到期 RETRY_WAIT
        ES-->>Recovery: EVENT-001
        Recovery->>Worker: dispatch
        Worker->>ES: claim, attempt+1
        ES-->>Worker: RUNNING
        Worker->>AI: 再次调用
        AI-->>Worker: 临时失败
        Worker->>ES: markRetry 或 markDead
    end

    alt 最终成功
        Worker->>ES: complete
        ES-->>Worker: SUCCEEDED
    else 达到 maxAttempts
        Worker->>ES: markDead
        ES-->>Worker: DEAD
    end
```

`DEAD` 后当前自动流程停止，需要人工检查错误、修复依赖并通过后续显式 re-run 能力重新发起。

## 8. 状态查询

```mermaid
sequenceDiagram
    autonumber
    actor Client as 调用方
    participant Controller as AlertAnalysisController
    participant Async as AsyncAlertAnalysisService
    participant Store as ES JobStore
    participant ES as Elasticsearch

    Client->>Controller: GET /api/v1/alert-analyses/{alertId}
    Controller->>Async: get(alertId)
    Async->>Store: findById(alertId)
    Store->>ES: GET _id=alertId

    alt 文档存在
        ES-->>Store: job document
        Store-->>Async: AlertAnalysisJob
        Async-->>Controller: AlertAnalysisJobView
        Controller-->>Client: 200 + status/result/error
    else 文档不存在
        ES-->>Store: not found
        Async-->>Controller: AlertAnalysisJobNotFoundException
        Controller-->>Client: 404 ProblemDetail
    end
```

## 9. 时序约束总结

- `CREATE(PENDING)` 必须发生在 `dispatch` 之前；
- `202` 不等待 `tryClaim`、RAG、模型或结果回写；
- 每次有效执行必须先取得新的 `runId` 和租约；
- 结果回写必须验证当前 `runId`；
- 恢复调度仍需通过同一个 Dispatcher，不得绕过 `Semaphore`；
- 重试只改变任务状态和下一次执行时间，不在调用线程中无限循环；
- 所有外部 I/O 必须配置硬超时，租约必须大于完整研判链路的最大合理耗时。
