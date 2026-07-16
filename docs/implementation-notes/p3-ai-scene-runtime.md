# P3 AI Scene Runtime Implementation Notes

## Metadata

- **Task / Feature:** `design-docs/003-ai-scene-runtime.md`
- **Date started:** 2026-07-16
- **Implementation owner:** Codex
- **Related Unknowns Report:** `docs/unknowns/p3-ai-scene-runtime.md`
- **Related plan / issue / PR:** 当前任务

## Confirmed Discoveries

### Discovery D-001

- **What was discovered:** P2 已提供稳定 Alias 解析和完整 Model 元数据，Scene 不需要保存 Model ID。
- **Evidence:** `ModelRepository.resolveAlias` 和 `ModelData`。
- **Why it matters:** Scene 可保持业务稳定接口，并从模型价格/延迟生成 Preview 信息。
- **Affected scope:** Scene Model Binding 和 Execution Trace。
- **Action taken:** Scene Binding 只保存 `model_alias`。

### Discovery D-002

- **What was discovered:** Prompt Runtime、Gateway 和外部 Identity 均未实现，但 P3 设计包含 Prompt、执行和权限。
- **Evidence:** 当前代码和后续设计文档边界。
- **Why it matters:** 强依赖会让 P3 无法独立运行。
- **Affected scope:** Prompt、Playground、业务执行和权限。
- **Action taken:** 计划定义可替换端口；默认只保存 Prompt 引用、返回明确 Preview、使用本地 JWT claims。

## Decisions

### Decision DEC-001

- **Decision:** 发布时保存不可变版本快照，Rollback 复制旧快照到新的 Draft 版本。
- **Alternatives considered:** 每次保存快照、直接把历史版本设为当前。
- **Reason:** 保持发布历史稳定并避免覆盖审计证据。
- **Evidence:** P3 发布和版本章节。
- **Owner / approver:** Architecture。
- **Reversibility:** Medium。
- **Follow-up:** Prompt Runtime 发布时复用同一版本语义。

### Decision DEC-002

- **Decision:** 真实推理通过 `SceneExecutionPort` 接入；默认实现是明确标记的本地 Preview。
- **Alternatives considered:** 返回 503、伪造 AI 输出、直接耦合 Provider HTTP。
- **Reason:** 保持核心配置/测试能力可用，同时不误导用户。
- **Evidence:** 用户要求外部服务不阻塞核心能力。
- **Owner / approver:** Architecture。
- **Reversibility:** High。
- **Follow-up:** Gateway 阶段替换默认 adapter。

### Decision DEC-003

- **Decision:** 内置内容以八个 Scene Template 提供，而不是在无模型的新库中预发布 Scene。
- **Alternatives considered:** 直接插入 Published Scene、仅代码内常量。
- **Reason:** 模板可开箱创建，又不会因 Alias 尚未配置产生虚假 Published 能力。
- **Evidence:** P3 模板和开箱即用要求。
- **Owner / approver:** Product。
- **Reversibility:** High。
- **Follow-up:** 安装向导可在默认 Alias 就绪后自动实例化模板。

## Assumptions

### Assumption A-001

- **Assumption:** Scene Preview 的成本使用输入字符估算 Token，并读取主模型当前输入价格。
- **Why it is currently acceptable:** 尚无真实 Tokenizer 和推理用量。
- **Risk:** 仅能用于相对提示，不能用于账单。
- **How it will be validated:** 断言结果明确为 estimate 和 PREVIEW。
- **Reversal plan:** Cost Runtime 提供真实 usage 后替换。

## Deviations

| Deviation | Reason | User-visible effect | Risk | Approved |
|---|---|---|---|---|
| 默认执行器返回明确的本地 Preview，不调用真实模型 | Gateway/Prompt Runtime 尚未实现，且外部系统不能阻塞核心能力 | Playground 和 Runtime 可验证完整链路，但响应标记为 `PREVIEW` / `executed=false` | 中 | Yes |
| Prompt 只保存 ID/版本引用 | P4 才提供 Prompt 实体和发布契约 | 当前不校验 Prompt 是否真实存在，Trace 会显示引用 | 中 | Yes |
| Workflow 结构保存在 `ai_scene.workflow_json` | 设计文档限定 P3 为七张表，现阶段也没有独立编排运行时 | 可编辑、导入导出并生成 Trace，暂不执行真实编排 | 中 | Yes |

## Unresolved Risks

| Risk | Impact | Current mitigation | Owner | Review trigger |
|---|---:|---|---|---|
| Preview 不是真实模型输出 | 3 | 响应明确 `PREVIEW` / `executed=false` | Gateway | SceneExecutionPort adapter 可用 |
| Prompt 引用暂时无法校验存在性 | 2 | 不阻塞发布，Trace 显示引用 | Prompt Runtime | P4 |
| Workflow 尚无实际编排 | 3 | 保存结构化步骤并输出 Trace | Workflow Runtime | 执行器接入 |
| JWT claim 命名可能变化 | 2 | 映射集中在 ScenePermissionPort adapter | Identity | Identity 契约冻结 |

## Tests Added or Updated

| Test | Purpose | Result |
|---|---|---|
| `SceneServiceTest` | 发布前测试门禁、唯一主模型、禁用后新 Draft 版本 | 3 passed |
| `PreviewSceneExecutionAdapterTest` | Preview 标识、Trace、Token/成本估算和输入保护 | 1 passed |
| `SecurityScenePermissionAdapterTest` | JWT role/department claims 和 local 独立运行 | 2 passed |
| `SceneApiE2ETest` | Provider→Model Alias→模板→配置→测试→发布→执行→版本→分享→审计完整 HTTP 链路 | 1 passed |
| P1/P2 Java 回归 | Provider、Model、加密和能力识别 | 12 passed |
| `SceneList/SceneEditorModal/ScenePlayground` Vitest | 列表、配置和 Playground 关键交互 | 3 P3 tests passed |
| 全部前端 Vitest | P1/P2/P3 前端回归 | 9 files / 13 tests passed |
| `mvn clean verify` | Java/Vue 编译、单测、HTTP E2E、生产构建和 JAR | BUILD SUCCESS |
| Packaged JAR smoke | Flyway V1-V3、health、Scene APIs、八模板、`/scenes` SPA | UP / 200 / passed |

## Rollback Notes

- Code rollback: 回退 P3 源码和 V3 之后的应用版本。
- Data rollback: 备份后删除七张 P3 表；P1/P2 数据不受影响。
- Configuration rollback: 恢复根路由和 scene scopes 前的配置。
- External-system rollback: 无外部强依赖。
- Recovery validation: Provider、Model 页面和 API 继续可独立运行。

## Knowledge Capture

- [x] Tests
- [x] Documentation
- [ ] Architecture decision record
- [x] Schema constraint
- [ ] Static analysis rule
- [x] Reusable component
- [x] AGENTS.md rule
- [ ] Another Skill
