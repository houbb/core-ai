# P5-P10 Remaining Runtimes Implementation Notes

## Metadata

- **Task / Feature:** `design-docs/005` through `010`
- **Date started:** 2026-07-16
- **Implementation owner:** Codex
- **Related Unknowns Report:** `docs/unknowns/p5-p10-remaining-runtimes.md`
- **Related plan / issue / PR:** 当前任务

## Confirmed Discoveries

### Discovery D-001

- **What was discovered:** 六个阶段共享的核心约束不是 CRUD，而是执行安全、可替换外部依赖和统一 Trace/Event。
- **Evidence:** Tool/Gateway/Agent/Analytics 设计及现有 Preview ports。
- **Why it matters:** 分别实现私有执行和统计会形成不可追踪的孤岛。
- **Affected scope:** P5-P10。
- **Action taken:** 冻结统一执行端口、traceId 和 Analytics Event 契约。

## Decisions

### Decision DEC-001

- **Decision:** 默认仅执行 Mock/Local 安全能力；外部 HTTP/MCP/LLM/Vector/Planner 通过 ports。
- **Alternatives considered:** 直接调用外部系统、全部返回 503。
- **Reason:** 核心能力独立可用，同时不伪造外部成功。
- **Evidence:** 用户明确要求外部服务不阻塞。
- **Owner / approver:** Architecture/Security。
- **Reversibility:** High。
- **Follow-up:** 各 Core 系统提供 adapter。

### Decision DEC-002

- **Decision:** Dangerous/Manual Confirm/Approval 操作先持久化等待状态，显式授权后才执行。
- **Alternatives considered:** UI 二次确认后直接执行、测试模式绕过。
- **Reason:** API/Agent/Replay 均必须共享同一安全门禁。
- **Evidence:** Tool 和 Agent 企业审批要求。
- **Owner / approver:** Security。
- **Reversibility:** Medium。
- **Follow-up:** 外部 Workflow/Approval 可替换授权端口。

## Assumptions

### Assumption A-001

- **Assumption:** 本地 Keyword/Hybrid 检索、确定性 Summary/Planner 足以验证 Runtime 工程能力。
- **Why it is currently acceptable:** 响应明确 Preview/Local，不宣称真实模型质量。
- **Risk:** 质量不代表生产 LLM/Vector 效果。
- **How it will be validated:** 单元/E2E 只断言确定性行为和边界。
- **Reversal plan:** 替换对应 ports。

## Deviations

- HTTP/MCP/Database/Shell/Python Tool 默认不直接执行，统一返回明确 `PREVIEW`；
  `MOCK` 为唯一默认真实本地执行器。
- Gateway Provider 默认 Preview，但 Scene、Conversation、Agent 已真实统一接入 Gateway Pipeline。
- Embedding 表保存本地哈希/外部 vector reference，不直接存高维向量。
- Agent 默认 Planner 为确定性 order planner，LLM Planner 留在 `AgentPlannerPort`。

## Unresolved Risks

| Risk | Impact | Current mitigation | Owner | Review trigger |
|---|---:|---|---|---|
| 真实外部协议方言 | 3 | port + Mock/Preview | Integrations | adapter 接入 |
| MySQL 无最终实证 | 2 | 可移植 SQL、无外键 | Deployment | MySQL 环境 |
| 大规模日志和搜索 | 3 | 分页、保留策略、索引 | Platform | 数据量阈值 |

## Tests Added or Updated

| Test | Purpose | Result |
|---|---|---|
| `SafeToolExecutionAdapterTest` | Mock/Preview 安全边界 | Passed |
| `PreviewGatewayProviderAdapterTest` | Gateway 默认非伪造执行 | Passed |
| `DeterministicConversationSummaryAdapterTest` | 确定性摘要 | Passed |
| `PreviewKnowledgeSourceAdapterTest` | Inline/External Source 降级 | Passed |
| `DeterministicAgentPlannerAdapterTest` | Task 排序、过滤、限制 | Passed |
| `AnalyticsServiceTest` | 洞察和时间范围断言 | Passed |
| `RemainingRuntimesE2ETest` | P5-P10 跨运行时主链 | Passed |
| `SceneApiE2ETest` | Scene → Gateway 回归 | Passed |
| Vue 14 files / 19 tests | 控制台与既有组件 | Passed |
| `mvn clean verify` | 编译、36 Java、19 Vue、生产 JAR | Passed |
| JAR smoke | Flyway、Health、SPA、Gateway、Analytics | Passed |

## Rollback Notes

- Code rollback: 恢复 P4 JAR。
- Data rollback: 备份后按 V10→V5 顺序删除新表。
- Configuration rollback: 移除 tool/gateway/memory/knowledge/agent/analytics scopes 和 adapter 配置。
- External-system rollback: 默认无外部强依赖。
- Recovery validation: P1-P4 页面和 E2E 继续运行。

## Knowledge Capture

- [x] Tests
- [x] Documentation
- [ ] Architecture decision record
- [x] Schema constraint
- [ ] Static analysis rule
- [x] Reusable component
- [x] AGENTS.md rule
- [ ] Another Skill
