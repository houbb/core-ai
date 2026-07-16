# P4 AI Prompt Runtime Post-Implementation Review

## Metadata

- **Task / Feature:** `design-docs/004-ai-prompt-runtime.md`
- **Date completed:** 2026-07-16
- **Reviewer:** Codex
- **Related Unknowns Report:** `docs/unknowns/p4-ai-prompt-runtime.md`
- **Related implementation notes:** `docs/implementation-notes/p4-ai-prompt-runtime.md`
- **Related PR / commit:** 当前工作区

## Behavior Changes

### Before

- Scene 只能保存未校验的 Prompt ID/版本占位引用。
- Prompt 内容没有统一资产、变量、版本、测试、发布或权限边界。
- Playground 只能展示 Scene 配置 Preview，无法生成最终 Prompt。

### After

- Prompt 具有稳定 Code、Draft/Published 双指针和不可变版本历史。
- 每次编辑或 Rollback 都创建新 Draft，旧 Published 继续服务直到原子切换。
- System/User/Assistant、强类型变量、默认值和模板引用可确定性渲染。
- 支持测试发布门禁、版本比较、回滚、结构化输出 Schema 和 Guardrail。
- Prompt Chain 会校验 Published 引用、深度和循环，并输出逐阶段渲染结果。
- A/B 提供稳定哈希分桶和真实 observation 聚合，不伪造线上数据。
- Public/Project/Department/Private 权限读取本地/JWT 上下文，不调用外部目录。
- Render Log 默认只保存变量名、Token、SHA-256 和模式，正文默认脱敏。
- Scene 在 Testing、Published 和执行前必须解析有权限的 Published Prompt。
- Vue3 Prompts 页面提供 IDE 风格编辑、变量提示、实时预览、Playground、测试、版本、A/B 和审计。

## Three-Pass Review

| Pass | Focus | Findings and actions |
|---|---|---|
| 1 | 架构和故障边界 | Runtime 输出校验只允许 Published 版本；Optional Chain 只忽略依赖不可用，不吞 Guardrail/变量错误；JSON_VALIDATE 强制绑定 Schema |
| 2 | 数据、安全和生命周期 | Scene 内部 Prompt 调用执行资产权限；Archived 清除线上指针；Render Log 清理过期记录；修正 `additionalProperties=false` 的无 properties 行为 |
| 3 | UI、性能和测试可用性 | 保留测试运行结果；修正可选 Prompt 字段的 `v-model`；新建页隐藏不可用标签；版本关系加载由 `3N+1` 优化为固定四次查询；移动导航支持滚动 |

## Files and Systems Affected

| Area | Change |
|---|---|
| Flyway | V4 新增八张 Prompt 表，全部有统一审计字段、注释和索引，无外键 |
| Domain/Application | Prompt 生命周期、不可变版本、渲染、测试、Schema、Guardrail、Chain、A/B 和审计 |
| Ports | `PromptReferencePort`、`PromptEvaluationPort`、`PromptPermissionPort`、`PromptRepository` |
| Persistence | JDBC Prompt Repository、批量版本关系加载、原子 A/B 聚合和日志保留 |
| P3 integration | Scene Testing/Publish/Runtime 解析 Published Prompt，并传入渲染结果 |
| Security | `ai.prompt.read` / `ai.prompt.manage` / `ai.prompt.render` 和资产级权限 |
| Vue3 | Prompts 首页、IDE、实时预览、Playground、测试、版本、治理、A/B 和双语资源 |
| Tests | JUnit5、Spring HTTP E2E、Vitest 和 packaged JAR smoke |
| Documentation | README、API、数据库、安全、架构、CHANGELOG、Unknowns 和实施记录 |

## Assumptions Review

| Assumption | Status | Evidence | Action |
|---|---|---|---|
| 默认测试 Actual 可使用最终渲染 Prompt | Confirmed | Preview evaluator 单测和完整 HTTP E2E | 保留，Gateway 后替换端口 |
| 字符数/4 可用于非账单 Token 提示 | Confirmed | Playground、Scene Trace 和断言均标记 estimate | 保留为 UI 提示 |
| local 模式可独立运行 | Confirmed | 独立 JAR 使用全新 SQLite 成功创建/渲染 Prompt | 保留 |
| 稳定哈希足以支持本阶段 A/B | Confirmed | 同 subject 重复 assignment 返回相同 variant | Gateway 复用或迁移 |

## Unknowns Review

### Resolved

| Unknown | Resolution | Evidence |
|---|---|---|
| 编辑是否中断 Published | 独立 `published_version`，新 Draft 不影响 Runtime | E2E V2 Draft 时 Runtime 仍返回 V1 |
| Test Actual 的定义 | 默认最终渲染 Prompt，响应明确 PREVIEW | 单测/E2E |
| Scene Prompt ID/code 歧义 | 引用解析同时支持内部 ID 和稳定 code | `PromptReferencePort` + Scene E2E |
| Render Log 敏感正文 | 默认 NULL，只返回 contentStored=false | JAR smoke |
| Chain 循环和不可用依赖 | 发布/渲染前图遍历，Optional 只跳过不可用依赖 | Service 逻辑和 E2E helper chain |

### Remaining

| Unknown | Risk | Follow-up |
|---|---|---|
| 真实模型质量评估 | 中 | Gateway/Evaluation 实现 `PromptEvaluationPort` |
| 在线 A/B 自动流量 | 中 | Gateway 使用 assignment 并写 observation |
| 完整 JSON Schema 标准 | 中 | 依据真实 Schema 契约逐关键字扩展 |
| 外部 Project/Department 目录 | 低 | Identity 契约冻结后替换 claims adapter |
| 多人同时编辑同一 Prompt | 低 | 需要时增加 If-Match/乐观锁 |

### Newly discovered

| Unknown | Impact | Recommended action |
|---|---|---|
| Test Case 本身可能包含敏感固定样例 | 中 | 仅授予 Prompt 管理权限；未来增加测试数据脱敏策略 |
| 完整 Prompt 版本过多时历史查询仍会增长 | 低 | 当前关系查询已固定四次；后续增加分页 |

## Deviations

| Deviation | Reason | User-visible effect | Risk | Approved |
|---|---|---|---|---|
| 不引入 Monaco | 避免大型依赖 | 保留核心 IDE 体验但没有完整语言服务 | 低 | Yes |
| 默认 evaluator 不调用 LLM | 外部 Gateway 不阻塞核心能力 | 只证明工程链路，不证明回答质量 | 中 | Yes |
| JSON Schema 为明确子集 | 控制复杂度和依赖 | 未支持关键字会明确失败 | 中 | Yes |
| A/B 由调用方写入观测 | 无线上代理 | 不会自动显示虚假指标 | 低 | Yes |

## Verification Evidence

最终完整门禁：

```text
JAVA_HOME=D:\tools\graalvm\graalvm-jdk-21.0.1+12.1
mvn clean verify
BUILD SUCCESS
Java/JUnit5: 29 passed, 0 failed, 0 errors, 0 skipped
HTTP E2E: Provider + Model + Prompt + Scene passed
Vitest: 12 files, 17 tests passed
Vue type check: passed
Vue production build: passed
Spring Boot JAR: target/core-ai-0.1.0-SNAPSHOT.jar
```

成品 JAR 独立进程冒烟：

```text
Flyway: V1, V2, V3, V4 applied
Health: UP
Initial Prompt count: 0
Prompt create: DRAFT V1
Prompt render: "Hello Smoke", 5 estimated tokens
Render Log: 1, contentStored=false
Scene templates: 8
GET /prompts: 200, Vue app mount present
```

非阻塞警告：

- Mockito 动态 agent 和 SpringDoc 默认启用为未来兼容/生产配置提示。
- `glob` 为传递依赖弃用提示，`npm audit` 为 0 vulnerabilities。
- `JsonSchemaValidator` 编译器报告一个 Jackson 过时 API 使用提示，不影响当前验证结果；后续依赖升级时替换。

## Requirements Traceability

| Requirement | Evidence |
|---|---|
| Prompt 是企业资产 | `ai_prompt`、稳定 Code、权限和审计 |
| Prompt 永远版本化 | 每次编辑/Rollback 新版本、Published 不覆盖 |
| System/User/Assistant IDE | Vue IDE 组件、行号、变量提示、复制 |
| 强类型变量和实时预览 | Renderer 单测、UI computed preview、服务端 Playground |
| 测试再发布 | Test Case、Preview evaluator、发布冲突断言 |
| Compare/Version/Rollback | Diff 服务、版本 API/UI、E2E |
| Prompt Chain | Published 引用、无环/深度检查、逐阶段渲染 |
| Output Schema | Schema 子集、Runtime validation、成功/失败断言 |
| Guardrail | Input/Output 规则、Injection/Length 单测 |
| A/B Test | 稳定分桶、observation 聚合、E2E |
| 权限 | Public/Project/Department/Private、JWT/local 单测 |
| Scene 联动 | Scene E2E 先发布 Prompt 再测试/发布/执行 |
| 外部服务不阻塞 | 可替换 evaluator/permission/reference ports |
| 编译+单测+E2E | `mvn clean verify` 和 packaged JAR smoke |

## Rollback and Recovery

- 停止服务并恢复 P3 JAR。
- 恢复升级前 SQLite/MySQL 备份。
- 如仅回退 P4，清理 V4 八表；P1-P3 无 Prompt 绑定的能力仍可运行。
- 已绑定 Prompt 的 Scene 应先解除 Prompt 引用或保留 P4 服务。
- 恢复后检查 health、Provider/Model/Scene 页面和 P1-P3 E2E。

## Maintainer Notes

- Prompt 内容只写入新版本；禁止增加覆盖历史内容的 UPDATE。
- `published_version` 是线上原子指针，`current_version` 是编辑/测试指针。
- `PromptReferencePort` 是 Scene/Gateway 的稳定边界，不应绕过 Published 和权限校验。
- 默认 Render Log 不保存正文；开启正文配置前必须完成数据保留和访问审查。
- Optional Chain 只允许忽略依赖不可用，不能忽略渲染或安全失败。

## Knowledge Capture

- [x] Tests
- [x] Documentation
- [ ] Architecture decision record
- [x] Schema constraint
- [ ] Static analysis rule
- [x] Reusable component
- [x] AGENTS.md rule
- [ ] Another Skill
