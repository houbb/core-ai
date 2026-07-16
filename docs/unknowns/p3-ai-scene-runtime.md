# P3 AI Scene Runtime Unknowns Report

## Metadata

- **Task / Feature:** `design-docs/003-ai-scene-runtime.md`
- **Mode:** Standard
- **Date:** 2026-07-16
- **Prepared by:** Codex
- **Scope:** Scene Catalog、生命周期、模型 Alias 绑定、场景参数、Prompt 引用、权限、模板、测试/Trace、版本发布、回滚、导入导出、Workflow 接口和管理界面

## Intent

### User-visible problem

P1/P2 仍要求用户理解 Provider、Model 和 Alias。业务用户真正知道的是“翻译、总结、OCR、代码审查”等目标，目前没有稳定的业务能力入口。

### Desired behavior change

管理员可以从内置模板创建业务场景，绑定多个模型 Alias、默认参数、Prompt ID、权限和 Workflow，经过 Testing 与 Playground 验证后发布。业务只通过 Scene Code 浏览和执行已发布场景，不直接依赖底层模型。

### Affected users and workflows

- 平台管理员：创建、配置、测试、发布、禁用、归档和回滚 Scene。
- 业务开发者：通过稳定 Scene Code 获取能力目录并执行。
- Prompt/Gateway/Identity：未来通过端口接入，不成为 P3 本地核心能力的启动依赖。
- P2 Model Runtime：为 Scene 提供 Alias 解析、模型价格、延迟和能力信息。

### Success criteria

- 新增七张 P3 核心表并遵守统一审计字段、索引和无外键规范。
- 生命周期覆盖 Draft、Testing、Published、Disabled、Archived。
- Scene 支持多 Alias、主模型/备用模型、优先级和启用状态。
- 支持场景参数、Prompt ID/版本引用、权限和可扩展 Workflow 步骤。
- 发布前必须成功测试当前版本；发布生成不可变配置快照。
- 支持版本列表和安全回滚，回滚结果进入新的 Draft 版本。
- 内置 Chat、Translate、OCR、Meeting、SQL、Coding、Knowledge、Email 模板。
- 支持 JSON 导出、导入和从现有 Scene 保存自定义模板。
- Playground 返回模型、Prompt 引用、延迟、成本估算、输出模式和 Trace。
- 外部真实推理未接入时明确返回 `PREVIEW`，不得伪装为真实 AI 输出。
- 业务目录只暴露 Published 且有权限的 Scene。
- Vue3 Scene 管理页面、JUnit5、HTTP E2E、Vitest、完整构建和 JAR 冒烟通过。

### Non-goals

- 不实现 `004` Prompt Runtime 的 Prompt 内容、变量渲染和 Prompt 版本实体。
- 不实现真实 Gateway 推理、自动故障切换、Token 计费或模型级 SLA。
- 不实现 Workflow 引擎、向量检索、Agent 或 Marketplace 安装协议。
- 不同步调用外部 Identity；JWT 权限在本地 Claims 上判断。

## Evidence Reviewed

| Source | Location | What it confirms | Confidence |
|---|---|---|---|
| Documentation | `design-docs/003-ai-scene-runtime.md` | P3 功能、生命周期、七表、模板、测试、版本和 UX | High |
| Documentation | `design-docs/000-tech.md` | 三层依赖、独立服务、SQLite/MySQL 和外部依赖降级要求 | High |
| Schema | `V1` / `V2` | 统一审计字段、无外键和已有共享审计日志 | High |
| Code | `ModelRepository.resolveAlias` | Scene 可通过 P2 Alias 获取可用模型候选 | High |
| Code | `SecurityConfig` / `SecurityRequestContext` | local/JWT 双模式和可扩展本地 Claims 权限 | High |
| UI | Provider/Model pages | Apple 风格、单 Vue 应用、路由和 i18n 模式 | High |
| Tests | `ModelApiE2ETest` | 可复用假 Provider 构建 Scene 全链路前置数据 | High |

## Confirmed Facts

| Fact | Evidence | Relevance |
|---|---|---|
| 本轮只实现文件编号 `003` | 用户要求每次一个 P | 固定 P3 边界 |
| Scene 必须依赖 Alias 而不是 Model ID | P3 核心设计 | 底层模型替换不影响业务 |
| Prompt 内容属于下一阶段 | P3 特别注意事项 | P3 只保存 Prompt ID/版本 |
| P2 Alias 解析只返回 Enabled 且 Provider 可用模型 | P2 实现 | 发布和执行可复用可靠候选 |
| 外部服务不得阻塞核心 | 用户明确要求 | 默认本地 Preview/Claims 适配 |
| 每阶段必须真实编译、单测和 E2E | `AGENTS.md` | P3 完成门禁 |

## Critical Unknowns

| Unknown | Category | Evidence / Reasoning | Impact | Probability | Irreversibility | Late discovery cost | Priority | Disposition | Resolution |
|---|---|---|---:|---:|---:|---:|---:|---|---|
| Gateway 未实现时 Playground 如何工作 | Known unknown | P3 要求 Run/Output/Trace，但真实推理属于后续阶段 | 5 | 5 | 2 | 5 | 250 | Decision | 定义 `SceneExecutionPort`；默认本地适配器返回明确 `PREVIEW`，验证配置并生成 Trace/估算，不伪造 AI 输出 |
| 发布版本何时生成 | Known unknown | Draft 修改频繁，版本必须可回滚 | 5 | 5 | 4 | 5 | 500 | Decision | Published 时保存不可变快照；Disabled→Draft 创建下一工作版本 |
| 发布是否必须先测试 | Unknown known | 企业场景不应直接上线 | 5 | 5 | 3 | 5 | 375 | Decision | 保存 `last_tested_version`；当前版本未成功测试则禁止 Publish |
| 内置 Scene 在没有模型时如何开箱即用 | Known unknown | 新安装可能没有 Provider/Alias | 5 | 5 | 2 | 4 | 200 | Decision | 内置八个模板；创建后为 Draft，Alias 就绪后测试发布，不让缺失外部模型阻塞服务启动 |
| Scene Category 是否固定枚举 | Unknown unknown candidate | 插件未来要增加 Medical/Finance/Legal | 4 | 4 | 4 | 4 | 256 | Decision | 后端存规范化字符串而非封闭枚举，内置分类由 UI 提供 |
| 权限如何在无 Identity 时执行 | Known unknown | local 模式必须独立，JWT 模式需角色/部门/组 | 5 | 4 | 3 | 4 | 240 | Decision | 定义 `ScenePermissionPort`；local 允许，JWT 从本地 claims/authorities 判断，不远程调用 Identity |
| Workflow 如何存储而不新增第八张表 | Known unknown | 设计指定七表，但要求未来 Pipeline | 4 | 4 | 3 | 4 | 192 | Decision | `ai_scene.workflow_json` 保存可扩展步骤；P3 Preview 解析和展示，后续 Workflow Runtime 接管执行 |
| Rollback 是否覆盖历史版本 | Known unknown | 覆盖会破坏审计 | 5 | 4 | 4 | 5 | 400 | Decision | 历史快照不可变；Rollback 复制旧配置到新的 Draft 版本 |
| Scene Alias 在 Draft 阶段是否必须可解析 | Known unknown | 模板需要在模型未配置时存在 | 4 | 5 | 2 | 3 | 120 | Decision | Draft 允许未解析 Alias；Testing/Publish 必须全部解析 |
| 导入包是否可能包含 Secret | Unknown unknown candidate | JSON 分享可能泄漏配置 | 5 | 2 | 3 | 4 | 120 | Decision | Scene 包只包含 Alias、参数、Prompt ID、权限和 Workflow，不包含 Provider/Model Secret |

## Implicit Expectations

| Expectation | Why it may exist | How to surface it |
|---|---|---|
| 首页默认进入 Scenes | P3 UX 明确“你想做什么” | 根路由改为 `/scenes` |
| Scene Card 一眼看到图标、模型和状态 | 设计示例 | SceneList 组件断言 |
| 发布是受控操作 | Draft→Test→Publish 强调企业门禁 | JUnit5/E2E 验证未测试不能发布 |
| Trace 必须可解释 | Playground/调试章节 | 返回 Scene→Alias→Model→Provider→Output 步骤 |
| 模型替换不能产生新 Scene 版本 | Scene 依赖 Alias | E2E 替换 Alias 后 Scene Code 不变 |

## Blind-Spot Candidates

| Candidate | Why it may matter | Validation method |
|---|---|---|
| 同一 Scene 出现多个 Primary | 路由语义不确定 | Service 校验 exactly one enabled primary |
| Alias 在测试后、发布前失效 | 发布可能产生不可执行 Scene | Publish 时重新验证所有 Alias |
| Published Scene 被直接修改 | 线上配置漂移且版本不可追踪 | 仅 Draft/Testing 可编辑 |
| 回滚形成版本号冲突 | 多次发布/回滚常见 | 新工作版本使用历史最大版本 + 1 |
| Scene Workflow 自引用 | 后续递归执行可能死循环 | 禁止直接引用自身，Trace 标记外部/嵌套步骤 |
| 导入同名 Code | 覆盖线上 Scene 风险高 | 默认冲突拒绝，不提供静默覆盖 |
| Department 权限在 local 模式不可判定 | 本地开发仍需可操作 | local 模式显式允许并在响应中保留权限策略 |
| 大量 Scene 逐项解析 Alias | 目录可能放大查询 | Service 对唯一 Alias 做请求内缓存 |

## Decisions Required

已通过现有设计、阶段边界和可逆实现解决，无需阻塞用户：

| Decision | Options | Trade-offs | Recommended owner | Deadline / Trigger |
|---|---|---|---|---|
| 执行模式 | 强依赖 Gateway / 本地 Preview 端口 | Preview 诚实且可独立测试，真实输出后续接入 | Architecture | P3 |
| 版本策略 | 每次保存快照 / 发布快照 | 发布快照减少噪声且符合上线版本语义 | Architecture | P3 |
| 权限策略 | 远程 Identity / 本地 JWT claims | 本地 claims 无外部阻塞 | Security | P3 |
| 模板落地 | 预建 Published Scene / 内置模板 | 模板不依赖模型环境，安装更稳健 | Product | P3 |
| Workflow 存储 | 新表 / JSON | JSON 保持七表边界和可演进性 | Architecture | P3 |

## Experiments or Prototypes Required

| Question | Method | Success signal | Cost | Owner |
|---|---|---|---|---|
| 发布门禁与版本回滚是否稳定 | HTTP E2E 完整生命周期 | 未测试发布失败、发布快照存在、回滚生成新 Draft | Medium | Implementation |
| Preview 是否足够可解释 | 端口单测 + E2E | 返回 mode、模型、Prompt 引用、成本、延迟和 Trace | Medium | Implementation |
| 内置模板是否可直接创建 | Migration + E2E | 新库可列出八个模板并创建 Draft Scene | Medium | Implementation |
| 权限是否无外部依赖 | local/JWT 适配器单测 | local 独立运行，JWT claims 可判断 | Medium | Implementation |

## Safe Assumptions

| Assumption | Why it is safe | Reversal plan |
|---|---|---|
| Scene Code 使用小写字母、数字、点、下划线和连字符 | 与 Provider/Alias 风格一致 | API 主版本内保持，导入时规范化 |
| 成本提示由当前主模型价格估算 | P3 尚无真实 Token 用量 | Cost Runtime 接入后替换估算 |
| 一个 Scene 只有一个 Prompt 引用 | P3 表设计是单 scene_id 绑定 | Prompt Runtime 可扩展为阶段级引用 |
| P3 Workflow 步骤仅做配置和 Preview Trace | 真正编排属于 Workflow Runtime | `SceneExecutionPort` 接入真实执行器 |

## Deferred Unknowns

| Unknown | Why deferred | Monitoring / Follow-up |
|---|---|---|
| Prompt 内容和变量渲染 | 属于 `004` | Prompt Runtime 通过 prompt_id/version 接管 |
| Gateway 真实模型调用和 fallback | 后续 Gateway 阶段 | 实现 `SceneExecutionPort` 适配器 |
| Workflow 多阶段状态与补偿 | 属于 Workflow Runtime | 保留 workflow_json 契约 |
| Marketplace 签名、依赖和版本兼容 | 属于 Marketplace | P3 只支持安全 JSON 导入导出 |
| 真实组织权限同步 | Identity 已签发 JWT claims 后 | 不新增同步数据库 |

## Recommended Implementation Boundary

### Implement now

- 七张 P3 表、Flyway 迁移、八个内置模板。
- Scene CRUD、搜索、生命周期、发布测试门禁、版本和回滚。
- 多 Alias、参数、Prompt 引用、权限、Workflow 配置。
- `SceneExecutionPort` 和 `ScenePermissionPort`，默认 Preview/local 实现。
- 管理 API、业务目录/执行 API、导入导出和模板 API。
- Scenes 首页、目录、详情、编辑、模板、Playground、版本和 Trace。
- P3 JUnit5、HTTP E2E、Vitest、完整构建和 JAR 冒烟。

### Do not implement now

- Prompt 内容存储或渲染。
- 真实远程推理、Gateway fallback 和计费。
- Workflow 引擎和 Marketplace 安装协议。
- 外部 Identity/Prompt/Gateway 服务强依赖。

### Interfaces or data contracts to freeze

- `/api/v1/ai/admin/scenes`
- `/api/v1/ai/admin/scene-templates`
- `/api/v1/ai/scenes`
- `SceneExecutionPort`
- `ScenePermissionPort`
- Scene Code、生命周期、版本快照和 Preview Trace 语义。

### Areas that must remain reversible

- Preview 输出格式和成本估算。
- Workflow JSON 步骤扩展字段。
- Permission claim 映射。
- 内置模板内容与推荐模型 Alias。

## Verification Plan

### Automated

- Unit tests: 生命周期、发布测试门禁、版本、主/备用模型和权限规则。
- Integration tests: V3 Flyway、七表、模板种子、快照和逻辑查询。
- End-to-end tests: Provider/Model/Alias → Template → Scene → Test → Publish → Runtime → Export/Import → Disable/Rollback/Archive。
- Contract tests: SceneExecutionPort Preview、Prompt 仅引用、Alias 解析。
- Static analysis: 三层依赖、Java 编译、Vue 类型检查和生产构建。

### Manual

- Happy path: AI Chat 模板创建并发布。
- Empty state: 无 Scene 时仍显示模板入口。
- Failure path: 未解析 Alias、未测试发布、重复 Code、导入冲突。
- Recovery path: Disabled Scene 回到新 Draft，旧版本可回滚。
- Permission boundaries: scene read/manage/execute scopes 和 local 独立模式。
- Mobile / responsive: 列表和详情单列。
- Accessibility: label、状态文本、禁用态和键盘按钮。
- Performance: 目录对唯一 Alias 使用请求内缓存。

### Observability

- Logs: Execution adapter 异常和 Preview 模式，不记录输入全文或 Secret。
- Metrics: 复用 Actuator；P3 不新增外部指标系统。
- Alerts: 外部 Gateway 尚未接入，不实现告警。
- Audit trail: Scene 创建、更新、测试、发布、禁用、回滚、导入、模板和执行。

## Handoff

- [x] Acceptance criteria
- [x] Explicit invariants
- [x] Data and interface contracts
- [x] Test cases
- [x] Rollback requirements
- [x] Observability requirements
- [x] Non-goals
- [x] Implementation notes file
