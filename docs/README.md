# 文档导航

| 文档 | 内容 |
|---|---|
| [`architecture.md`](architecture.md) | V0.3 总体架构、组件、状态机、部署拓扑、一致性、容量与安全边界 |
| [`sequence-diagrams.md`](sequence-diagrams.md) | 正常研判、重复提交、多实例抢占、宕机恢复、旧结果拒绝和重试时序 |
| [`operations-runbook.md`](operations-runbook.md) | 部署、配置、容量规划、监控告警、故障处理、发布与回滚 |
| [`evolution-roadmap.md`](evolution-roadmap.md) | V0.2 到 V1.0 的版本演进路线 |

## 推荐阅读顺序

```text
README
  ↓
architecture.md
  ↓
sequence-diagrams.md
  ↓
operations-runbook.md
  ↓
evolution-roadmap.md
```

所有架构图和时序图使用 Mermaid 保存为文本源文件，GitHub 会按矢量图渲染；放大后不会出现位图模糊，也便于随代码变更审查和维护。
