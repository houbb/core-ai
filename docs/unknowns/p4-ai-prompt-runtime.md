# P4 AI Prompt Runtime Unknowns Report

## Metadata

- **Task / Feature:** `design-docs/004-ai-prompt-runtime.md`
- **Mode:** Standard
- **Date:** 2026-07-16
- **Prepared by:** Codex
- **Scope:** Prompt 资产、版本、渲染、变量、测试、发布、回滚、比较、权限、Guardrail、输出 Schema、Chain、A/B、Scene 联动和 Vue 管理界面

## Intent

### User-visible problem

当前 Scene 只能保存 Prompt ID/版本占位引用，企业 Prompt 仍无法统一编辑、验证、版本化、发布和复用。

### Desired behavior change

用户可以把 Prompt 当作代码资产管理：创建不可变版本、实时渲染、运行测试、通过门禁发布、比较和回滚，并让 Published Scene 引用真实可解析的 Prompt。

### Affected users and workflows

- Prompt 工程师：编辑、变量提示、实时预览、测试、比较和发布。
- Scene 管理员：绑定 Published Prompt，并在 Scene 测试/发布时验证引用。
- 安全管理员：通过 Public/Project/Department/Private 和 scopes 控制访问。
- 后续 Gateway/Agent：通过稳定 Prompt 引用取得结构化渲染结果。

### Success criteria

- V4 真实创建设计要求的八张表，满足统一审计字段、注释、索引和无外键规范。
- Prompt 生命周期、不可变版本、测试发布门禁、回滚和比较可通过 API/UI 使用。
- 变量类型、默认值、模板引用、Guardrail 和 JSON Schema 均有确定性校验。
- Scene 在绑定 Prompt 时保持编辑可逆，但进入 Testing/Published/执行前必须解析 Published Prompt。
- 默认模式不调用外部 LLM；渲染、测试和 A/B 核心能力可独立运行。
- JUnit5、HTTP E2E、Vitest、生产构建和独立 JAR 冒烟全部通过。

### Non-goals

- 不实现真实模型推理、模型评分或线上 A/B 自动流量代理。
- 不实现完整 JSON Schema 2020-12 规范，只冻结当前支持的可验证子集。
- 不实现 Monaco/VSCode 内核；提供仓库内无新大型依赖的 IDE 风格编辑器。
- 不调用外部 Identity、Project 或 Department 服务。

## Evidence Reviewed

| Source | Location | What it confirms | Confidence |
|---|---|---|---|
| Design | `design-docs/004-ai-prompt-runtime.md` | Prompt Runtime 功能、生命周期、八表和 UX | High |
| Roadmap | `design-docs/000-roadMap.md` | Prompt 位于 Scene 之后，需版本/测试/比较/实时预览 | High |
| Scene code | `SceneService` / `ai_scene_prompt` | P3 已冻结 Prompt ID/版本引用和执行端口 | High |
| Security | `SecurityConfig` / `SecurityScenePermissionAdapter` | local/JWT 双模式和本地 claims 解析模式 | High |
| Persistence | V1-V3 / JDBC repositories | SQLite/MySQL 可移植 SQL、审计和无外键约束 | High |
| Tests | P1-P3 JUnit5/E2E/Vitest | 完整门禁和隔离 SQLite 的现有模式 | High |
| UI | `ScenesPage.vue` / tokens / i18n | Apple 风格、双语、100% 宽度和组件测试模式 | High |

## Confirmed Facts

| Fact | Evidence | Relevance |
|---|---|---|
| `004` 文件正文称 Phase 3，但用户已明确每个文件独立对应一个 P | 当前任务顺序与既有 P1-P3 | 本次按 P4 交付，不重编号历史模块 |
| Prompt Runtime 只负责生成最终 Prompt | 设计“只负责生成最终 Prompt” | 默认实现必须确定性、不可伪造 LLM 输出 |
| P3 Scene 已保存 `prompt_id`/`prompt_version` | V3 和 Scene domain | 可直接增加解析端口，无需改表 |
| Gateway 尚未实现 | 设计顺序与 README | Prompt 测试需用可替换 Preview evaluator |
| 每张新表必须有五个审计字段且禁止外键 | `AGENTS.md` | V4 必须扩展设计草案字段 |
| local 模式必须独立运行 | 用户要求、现有安全配置 | 权限默认读取当前安全上下文 |

## Critical Unknowns

| Unknown | Category | Evidence / Reasoning | Impact | Probability | Irreversibility | Late cost | Priority | Disposition | Resolution |
|---|---|---|---:|---:|---:|---:|---:|---|---|
| Published Prompt 后继续编辑是否应中断旧版本运行 | Known unknown | 生命周期和“永远版本化”未定义在线切换语义 | 5 | 4 | 4 | 5 | 400 | Decision | 编辑创建新 Draft，保留 `published_version`，旧 Published 继续可用 |
| Test Case 的 Actual 在无 Gateway 时是什么 | Known unknown | 文档展示 Expected/Actual，但本阶段不负责 LLM | 4 | 5 | 2 | 4 | 160 | Decision | 可替换 `PromptEvaluationPort`；默认用渲染结果做 PREVIEW actual，明确 `executed=false` |
| Scene 的 `prompt_id` 是内部 ID 还是稳定 code | Known unknown | 字段名与示例 `translate-v3` 不一致 | 4 | 4 | 3 | 4 | 192 | Decision | API 同时接受 UUID/稳定 code；响应返回规范 ID/code/version |
| Prompt Chain 如何在无推理时执行 | Known unknown | Chain 步骤间通常依赖模型输出 | 4 | 4 | 2 | 4 | 128 | Decision | 本阶段验证引用和无环，渲染每个阶段；真实阶段串行执行留给 Gateway/Agent |
| JSON Schema 支持范围 | Unknown known | “原生支持”可能被理解为完整标准 | 4 | 4 | 3 | 5 | 240 | Decision | 支持 type/required/properties/items/enum 的稳定子集，API 返回 unsupported keyword |
| Render Log 是否保存敏感正文 | Unknown unknown candidate | 设计明确提醒保留策略 | 5 | 4 | 4 | 5 | 400 | Decision | 默认不保存正文，只保存变量名、版本、Token 和哈希；配置显式开启才保存 |
| A/B 统计由谁产生 | Known unknown | Gateway 尚未承载真实流量 | 3 | 4 | 2 | 3 | 72 | Monitor | 提供确定性分流和观测写入 API，不伪造统计 |
| 同一 Draft 并发编辑 | Unknown unknown candidate | 当前无 ETag/锁版本 | 3 | 3 | 2 | 3 | 54 | Accept | 每次编辑生成新版本降低覆盖风险；后续多人协作增加 If-Match |

## Implicit Expectations

| Expectation | Why it may exist | How to surface it |
|---|---|---|
| 编辑器应像 IDE 而不是普通表单 | 设计多次强调 VSCode、行号、变量提示 | 实现行号、分区、变量高亮/建议和快捷复制 |
| 发布不能影响正在使用的旧版本 | Prompt as Code 和生产只能 Published | 保留 active published version，切换为原子字段更新 |
| 调试信息不能泄露敏感输入 | 企业资产、安全规范和 Render Log 提示 | 默认脱敏日志，审计只记录元数据 |
| Scene 和 Prompt 应真正打通 | 用户要求尽可能与已有模块联动 | Scene Testing/Publish/execute 前解析 Prompt 并把渲染摘要加入 Trace |

## Blind-Spot Candidates

| Candidate | Why it may matter | Validation method |
|---|---|---|
| 模板引用未声明变量 | 会在生产渲染时失败 | 保存和发布时静态解析断言 |
| 默认值类型与变量类型不一致 | UI 看似正常但运行失败 | 服务层类型转换单测 |
| Prompt/Chain 循环引用 | 可导致无限递归 | 发布前图遍历和最大深度测试 |
| Strict Schema 但模板没有结构化输出提示 | 可能让下游误以为有保障 | 发布门禁要求 schema 合法；输出仍需调用验证端点 |
| Render Log 无界增长 | 调试请求可放大数据库 | 配置保留上限并在写入时清理旧记录 |
| A/B ratio 边界和重复观测 | 会污染统计 | 0-100 校验、观测只做原子累加并审计 |

## Decisions Required

| Decision | Options | Trade-offs | Owner | Trigger |
|---|---|---|---|---|
| 版本模型 | 覆盖当前 / 每次编辑新版本 | 新版本更多，但满足 Prompt as Code | Architecture | 已冻结 |
| 发布切换 | 发布时中断旧版本 / 保留旧版本 | 保留旧版本更安全 | Architecture | 已冻结 |
| 默认测试 | 阻塞等待 Gateway / Preview evaluator | Preview 可独立但不代表模型质量 | Architecture | 已冻结 |
| 日志正文 | 默认保存 / 默认脱敏 | 脱敏牺牲调试便利但安全 | Security | 已冻结 |
| JSON Schema | 完整标准 / 明确子集 | 子集可控且可测试 | Architecture | 已冻结 |

## Experiments or Prototypes Required

| Question | Method | Success signal | Cost | Owner |
|---|---|---|---|---|
| 无 Monaco 的 IDE 风格编辑器是否足够 | Vue 组件 + Vitest | 行号、变量建议、实时预览、复制可用 | Low | P4 |
| Scene Prompt 联动是否保持 P3 回归 | HTTP E2E | 创建 Published Prompt 后 Scene 全链路通过 | Medium | P4 |
| SQLite 下版本/测试关系加载是否稳定 | 隔离 DB E2E | 无锁等待、完整回读 | Low | P4 |

## Safe Assumptions

| Assumption | Why it is safe | Reversal plan |
|---|---|---|
| Token 使用字符数/4 估算 | 响应明确 `estimated`，无账单用途 | Gateway tokenizer 接入后替换 |
| local 模式允许全部 Prompt | 与 P1-P3 一致且仅监听本机 | JWT 模式使用 item permission adapter |
| A/B subject 使用稳定哈希分桶 | 无外部状态且可重复 | Gateway 接入时复用同一算法或迁移 |
| Chain 最大深度 10 | 防止递归失控且可配置扩展 | 后续通过配置提高 |

## Deferred Unknowns

| Unknown | Why deferred | Monitoring / Follow-up |
|---|---|---|
| 真实模型输出和质量评分 | 属于 Gateway/Evaluation | `PromptEvaluationPort` 替换 |
| 在线 A/B 自动路由 | Gateway 尚未处理流量 | 保留 assign/record API |
| 完整 JSON Schema 标准 | 当前无复杂业务契约样例 | 按 unsupported keyword 错误逐步扩展 |
| 外部 Project/Department 目录 | Identity 契约未冻结 | 只读取 JWT claims |

## Recommended Implementation Boundary

### Implement now

- 八表、领域模型、版本状态机、渲染器、变量校验、Guardrail、Schema 子集。
- 测试用例、Preview evaluator、发布门禁、回滚、比较、Chain 校验、A/B API。
- Prompt 权限、审计、脱敏 Render Log、Scene 引用解析和渲染联动。
- Prompt IDE、实时预览、Playground、版本/测试/A-B 界面和中英文资源。

### Do not implement now

- 真实 LLM 请求、在线实验代理、外部 Identity 同步、完整 Monaco 和完整 JSON Schema 标准。

### Interfaces or data contracts to freeze

- `PromptReferencePort`：按 ID/code + 可选版本解析 Published Prompt。
- `PromptEvaluationPort`：测试执行可替换，默认 PREVIEW。
- `PromptRenderResult`：版本、三段 Prompt、Chain、Token 估算、Guardrail 和 Schema 元数据。
- Scene 的 `promptId` 继续兼容，允许 ID 或 code。

### Areas that must remain reversible

- Preview evaluator、权限 claims 映射、Render Log 内容策略、JSON Schema validator 和 A/B 观测入口。

## Verification Plan

### Automated

- Unit：渲染/类型/Guardrail/Schema、版本发布门禁、权限、A/B 分流。
- Integration：Flyway V1-V4、JDBC 关系回读、Prompt HTTP 主链路。
- Regression：P1-P3 全部测试，Scene 使用 Published Prompt。
- Frontend：IDE、实时预览、Playground/版本交互。
- Static/build：`mvn clean verify` 统一执行 Java、Vitest、Vue typecheck/build 和 JAR。

### Manual

- 空列表、新建、变量提示、实时预览、失败测试、发布、回滚、比较、权限拒绝和响应式布局。

### Observability

- 所有写操作进入 `ai_audit_log`。
- Render Log 默认只保留摘要和哈希，支持保留上限清理。
- Preview 响应明确 `mode=PREVIEW`、`executed=false`。

## Handoff

- [x] Acceptance criteria
- [x] Explicit invariants
- [x] Data and interface contracts
- [x] Test cases
- [x] Rollback requirements
- [x] Observability requirements
- [x] Non-goals
- [x] Implementation notes file
