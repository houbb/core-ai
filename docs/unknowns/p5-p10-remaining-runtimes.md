# P5-P10 Remaining AI Runtimes Unknowns Report

## Metadata

- **Task / Feature:** `005-ai-tool-runtime.md` through `010-ai-analysis.md`
- **Mode:** Deep
- **Date:** 2026-07-16
- **Prepared by:** Codex
- **Scope:** Tool、Gateway、Conversation/Memory、Knowledge、Agent、Analytics 六个 Runtime 及跨阶段联动

## Intent

### User-visible problem

P1-P4 已能定义 Provider、Model、Scene 和 Prompt，但 AI 还不能安全调用企业能力、稳定执行请求、保持上下文、检索知识、自主完成任务或提供生产分析。

### Desired behavior change

一次性交付剩余 Runtime，使 `core-ai` 形成可独立运行的企业 AI OS：外部能力可注册但不阻塞本地核心；危险操作可审批；请求统一经过 Gateway；会话、记忆、知识、Agent 和 Analytics 全链路可追踪。

### Success criteria

- P5-P10 每个设计文档的核心数据模型、生命周期、API、Vue 页面和权限均实现。
- 所有新表包含 `id/create_time/update_time/create_user/update_user`、注释和索引，无外键。
- Tool、Gateway、Knowledge 外部依赖使用可替换端口；默认 Mock/Preview/Local 不伪造真实外部结果。
- Scene、Conversation、Agent 请求统一进入 Gateway。
- Tool 危险操作和 Agent 高风险任务必须等待确认/审批。
- Memory/Knowledge 权限在读取或检索前过滤。
- Analytics 使用统一事件模型采集 Gateway、Tool、Conversation、Knowledge 和 Agent 数据。
- 最终统一通过 Java/JUnit5、HTTP E2E、Vitest、Vue 构建和独立 JAR 冒烟。

### Non-goals

- 不在默认配置执行 Shell/Python/SQL 或连接真实 MCP、向量数据库、邮件、支付等外部系统。
- 不伪造真实 LLM、Embedding、Reranker 或 Agent 推理质量。
- 不引入 Redis、Milvus、OpenSearch、Kafka 等强依赖。
- 不实现外部 Identity/审批中心同步；使用本地/JWT 上下文和可替换端口。

## Evidence Reviewed

| Source | Location | What it confirms | Confidence |
|---|---|---|---|
| Design | `design-docs/005`-`010` | 六个 Runtime 的功能、数据模型和 UX | High |
| Existing architecture | Provider/Model/Scene/Prompt services and ports | 端口隔离、生命周期、Preview 和 JDBC 模式 | High |
| Security | `SecurityConfig` and permission adapters | local/JWT 双模式、scope 和 claims 读取 | High |
| Tests | P1-P4 unit/E2E/Vitest | 隔离 SQLite 和最终单 JAR 门禁 | High |
| User direction | 当前任务 | 外部服务不得阻塞，一口气完成且不再确认 | High |

## Confirmed Facts

| Fact | Evidence | Relevance |
|---|---|---|
| 每个文件按 P 阶段顺序交付 | 用户前置说明 | 本次对应 P5-P10 |
| Gateway 必须成为唯一 AI 执行入口 | 006 设计 | Scene 默认执行适配器需要升级 |
| Tool/Knowledge 依赖大量外部系统 | 005/008 设计 | 必须使用 ports 和本地默认实现 |
| Dangerous/Approval 是企业硬边界 | 005/009 设计 | 未审批不能执行 |
| Memory 与 Knowledge 生命周期不同 | 007/008 设计 | 数据模型和权限不可混合 |
| Analytics 应事件驱动 | 010 设计 | 各 Runtime 不维护私有统计孤岛 |

## Critical Unknowns

| Unknown | Category | Evidence / Reasoning | Impact | Probability | Irreversibility | Late cost | Priority | Disposition | Resolution |
|---|---|---|---:|---:|---:|---:|---:|---|---|
| 默认 Tool 是否允许执行 Shell/SQL/Python | Known unknown | 设计列出能力，但默认执行有高风险 | 5 | 5 | 5 | 5 | 625 | Decision | 默认禁止；仅 Mock 和受限 HTTP adapter 可执行，其他返回 PREVIEW |
| Manual Confirm 与 Approval 如何独立运行 | Known unknown | 无外部审批中心 | 5 | 5 | 3 | 5 | 375 | Decision | execution log 保存 WAITING_CONFIRM/WAITING_APPROVAL，显式 confirm/approve endpoint 继续执行 |
| Gateway 无真实模型时如何成为唯一入口 | Known unknown | Provider adapter 尚无 chat 协议 | 5 | 5 | 2 | 5 | 250 | Decision | Gateway Pipeline 真实执行路由/限流/缓存/重试/熔断/Trace；Provider 端口默认 PREVIEW |
| Conversation 自动摘要是否调用 LLM | Known unknown | Gateway 默认无真实模型 | 3 | 5 | 2 | 3 | 90 | Decision | 默认确定性摘要，摘要端口可替换 |
| Knowledge Embedding/Vector 如何本地运行 | Known unknown | 不应引入外部向量库强依赖 | 5 | 5 | 3 | 5 | 375 | Decision | Embedding port 默认生成内容哈希引用；本地 Keyword/Hybrid 索引，外部 vector_id 接口保留 |
| Agent Planner 如何避免伪造智能 | Unknown known | 设计期待自主规划，默认无 LLM | 5 | 5 | 3 | 5 | 375 | Decision | 确定性 Planner 根据已配置 Task 生成计划；响应标记 PREVIEW，Planner port 可替换 |
| Analytics 事件表尚不存在时依赖顺序 | Unknown unknown | 前序服务需发事件 | 4 | 3 | 3 | 4 | 144 | Decision | 最终 V10 总是迁移；统一 `AnalyticsEventPort`，默认 JDBC adapter，写入失败不反向阻塞核心 |
| 跨 Runtime 删除/归档引用 | Unknown unknown | 无数据库外键 | 4 | 4 | 3 | 4 | 192 | Monitor | 发布/执行时动态解析；归档资源对业务目录不可见，历史日志保留稳定 ID |
| 60+ 新表的 MySQL 兼容 | Unknown unknown | 当前只有 SQLite 实证 | 4 | 3 | 4 | 5 | 240 | Monitor | 使用现有可移植类型和无外键模式；SQLite E2E，MySQL 部署补契约测试 |

## Implicit Expectations

| Expectation | Why it may exist | How to surface it |
|---|---|---|
| 危险 Tool/Agent 不能因“测试模式”绕过审批 | 企业安全设计反复强调 | 单元和 E2E 断言状态停在 WAITING_* |
| Trace 应串起所有 Runtime | P6-P10 均强调 Trace | 统一 traceId、父子 span 和事件 metadata |
| 外部不可用时管理和测试仍可用 | 用户明确要求 | Mock/Preview/Local adapter 和独立 JAR smoke |
| UI 首页应按阶段升级 | Gateway/Analytics 设计强调 Dashboard | 最终根路由进入 Analytics，总导航保留所有 Runtime |

## Blind-Spot Candidates

| Candidate | Why it may matter | Validation method |
|---|---|---|
| Tool 输入日志泄露 Secret/身份证 | 执行日志是高风险数据 | 深度脱敏器单测 |
| Rate Limit/Cache key 跨用户串数据 | 可能造成越权和数据泄露 | key 包含用户/部门/项目/Scene |
| Memory/Knowledge 检索后再过滤 | 设计明确会泄露 | Repository query 前应用权限 scope |
| Agent 递归/循环委派 | 可无限执行 | 深度/任务数/重试上限 |
| Analytics 写入失败拖垮业务 | 观测不能反向阻塞 | safe event publisher 单测 |
| Replay 重放危险 Tool | 会重复副作用 | Replay 默认 Preview，必须重新审批 |

## Decisions Required

| Decision | Options | Trade-offs | Owner | Status |
|---|---|---|---|---|
| 外部执行默认策略 | 实际执行 / Preview | Preview 更安全且独立 | Security | Frozen |
| Gateway provider adapter | 直接耦合 Provider / port | port 可扩展 | Architecture | Frozen |
| Knowledge 默认检索 | 外部向量 / 本地 Keyword-Hybrid | 本地可独立 | Architecture | Frozen |
| Agent Planner | 伪造智能 / 确定性计划 | 确定性可测试 | Architecture | Frozen |
| Analytics 失败语义 | 阻塞 / 降级 | 降级避免反向阻塞 | Architecture | Frozen |

## Safe Assumptions

| Assumption | Why it is safe | Reversal plan |
|---|---|---|
| SQLite 本地索引适合当前管理规模 | 可逆且已有 Profile | 替换 KnowledgeIndexPort |
| Dashboard 可实时聚合日志 | 当前数据量有限 | 后续定时物化 ai_gateway_dashboard/ai_metric |
| 自动摘要使用消息截断与去重 | 明确标记 deterministic | 替换 SummaryPort |
| Agent schedule 使用 Spring CronExpression | 无外部调度依赖 | 接入 core-workflow 后迁移 |

## Deferred Unknowns

| Unknown | Why deferred | Follow-up |
|---|---|---|
| 真实 MCP transport 细节 | 外部协议/服务不应阻塞 | ToolExecutionPort adapter |
| 真实 Provider chat/streaming 方言 | Gateway port 已冻结 | Provider-specific adapters |
| 高维向量持久化 | 需选型 | KnowledgeEmbeddingPort/IndexPort |
| LLM Planner 和 Judge | 需 Gateway 真实推理 | AgentPlannerPort/AnalyticsInsightPort |
| 企业审批组织流 | 需 Identity/Workflow | ApprovalPort |

## Recommended Implementation Boundary

### Implement now

- 六个 Runtime 的表、领域、服务、API、权限、UI、审计和测试。
- Mock/Preview/Local adapters、危险操作门禁、Trace、事件采集和跨 Runtime 主链路。
- OpenAPI Tool 导入、本地 Gateway Pipeline、Conversation Context、Knowledge 本地检索、Agent 确定性执行、Analytics Dashboard。

### Do not implement now

- 默认 Shell/SQL/Python 执行、真实 MCP/LLM/向量库、外部审批/Identity 同步。

### Interfaces or data contracts to freeze

- `ToolExecutionPort`
- `GatewayProviderPort` / `GatewayInvocationPort`
- `ConversationReplyPort`
- `KnowledgeEmbeddingPort` / `KnowledgeSourcePort`
- `AgentPlannerPort`
- `AnalyticsEventPort`

### Areas that must remain reversible

- 所有外部 adapter、日志正文策略、检索算法、Planner、Summary、Dashboard 聚合。

## Verification Plan

### Automated

- 每 Runtime 至少一组 JUnit5 核心断言和一条 HTTP E2E 主链路。
- 安全：脱敏、权限、审批、限流、循环、归档边界。
- 跨模块：Tool→Gateway→Conversation→Knowledge→Agent→Analytics。
- Vue：每个新页面关键交互组件断言。
- 最终 `mvn clean verify` 和独立 JAR smoke。

### Observability

- 统一 request/trace ID。
- Tool/Gateway/Agent Trace 和 Analytics spans。
- 外部 adapter/Analytics 写入失败记录但不阻塞核心。

## Handoff

- [x] Acceptance criteria
- [x] Explicit invariants
- [x] Data and interface contracts
- [x] Test cases
- [x] Rollback requirements
- [x] Observability requirements
- [x] Non-goals
- [x] Implementation notes file
