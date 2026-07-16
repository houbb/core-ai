# P4 AI Prompt Runtime Implementation Notes

## Metadata

- **Task / Feature:** `design-docs/004-ai-prompt-runtime.md`
- **Date started:** 2026-07-16
- **Implementation owner:** Codex
- **Related Unknowns Report:** `docs/unknowns/p4-ai-prompt-runtime.md`
- **Related plan / issue / PR:** 当前任务

## Confirmed Discoveries

### Discovery D-001

- **What was discovered:** Scene 已有稳定 Prompt 引用列，但没有解析契约。
- **Evidence:** `ai_scene_prompt`、`ScenePromptBinding`。
- **Why it matters:** P4 可直接联动 P3，不需要修改 Scene 数据模型。
- **Affected scope:** Scene Testing/Publish/Runtime。
- **Action taken:** 计划引入 `PromptReferencePort`，同时支持 ID 和 code。

### Discovery D-002

- **What was discovered:** Prompt Runtime 的职责是确定性渲染，不是模型推理。
- **Evidence:** P4 设计“只负责生成最终 Prompt”；Gateway 在后续阶段。
- **Why it matters:** 测试不能伪造真实 LLM 输出。
- **Affected scope:** Playground、Test Case、A/B。
- **Action taken:** 计划使用可替换 Preview evaluator，并显式返回 `executed=false`。

## Decisions

### Decision DEC-001

- **Decision:** 编辑永远创建新 Draft 版本；`published_version` 独立保存当前线上版本。
- **Alternatives considered:** 覆盖当前版本、编辑 Published 时立即下线。
- **Reason:** 满足 Prompt as Code，并避免编辑影响生产。
- **Evidence:** 版本化、生产只能 Published、回滚要求。
- **Owner / approver:** Architecture。
- **Reversibility:** Medium。
- **Follow-up:** Gateway 使用 active published version。

### Decision DEC-002

- **Decision:** Render Log 默认脱敏，只保存变量名、字符/Token 数和内容哈希。
- **Alternatives considered:** 默认保存完整变量和 Prompt、完全不记录。
- **Reason:** 保留调试证据又避免默认长期保存敏感内容。
- **Evidence:** 设计保留策略提醒和项目安全规范。
- **Owner / approver:** Security。
- **Reversibility:** High。
- **Follow-up:** 通过配置显式开启正文并设置保留数量。

### Decision DEC-003

- **Decision:** JSON Schema 支持稳定子集：`type`、`required`、`properties`、`items`、`enum`。
- **Alternatives considered:** 不校验、引入完整外部 Schema 引擎。
- **Reason:** 覆盖本阶段结构化输出核心场景，减少新依赖和不透明行为。
- **Evidence:** 当前无复杂 Schema 样例，设计强调稳定数据接口。
- **Owner / approver:** Architecture。
- **Reversibility:** High。
- **Follow-up:** 根据真实契约扩展关键字。

## Assumptions

### Assumption A-001

- **Assumption:** 默认 Prompt 测试的 Actual 为渲染后的完整 Prompt。
- **Why it is currently acceptable:** 本阶段只负责生成最终 Prompt，真实 LLM 属于 Gateway。
- **Risk:** 只能验证工程正确性，不能证明模型质量。
- **How it will be validated:** 响应和 UI 明确显示 PREVIEW / 未执行。
- **Reversal plan:** 替换 `PromptEvaluationPort`。

## Deviations

| Deviation | Reason | User-visible effect | Risk | Approved |
|---|---|---|---|---|
| 编辑器使用仓库内 IDE 风格组件，不引入 Monaco | 避免为行号、变量提示和复制引入大型前端依赖 | 提供行号、等宽字体、变量补全、实时预览和复制，但不是完整 VSCode 内核 | 低 | Yes |
| 默认 Test Case Actual 为最终渲染 Prompt | 本阶段只负责 Prompt 生成，Gateway 尚未实现 | 可以验证变量、模板、Chain 和治理规则；明确显示 `PREVIEW` / `executed=false` | 中 | Yes |
| JSON Schema 实现稳定子集 | 当前没有需要完整 2020-12 标准的业务样例 | 未支持关键字返回明确错误，不会静默忽略 | 中 | Yes |
| A/B 只提供稳定分桶和 observation API | Gateway 尚未承载线上流量 | 不自动产生样本或伪造胜率 | 低 | Yes |

## Unresolved Risks

| Risk | Impact | Current mitigation | Owner | Review trigger |
|---|---:|---|---|---|
| Preview 不代表模型质量 | 3 | 明确标识，端口可替换 | Gateway | P6 |
| JSON Schema 不是完整标准 | 3 | 冻结支持子集并拒绝未知关键字 | P4 | 新 Schema 样例 |
| 在线 A/B 尚无真实流量 | 2 | 只提供稳定分流和观测 API | Gateway | P6 |
| JWT Project/Department claims 可能变化 | 2 | 集中在权限 adapter | Identity | 契约冻结 |

## Tests Added or Updated

| Test | Purpose | Result |
|---|---|---|
| `PromptTemplateRendererTest` | 强类型变量、默认值、缺失和类型错误 | 2 passed |
| `JsonSchemaValidatorTest` | Schema 子集、结构化输出和未知关键字 | 2 passed |
| `PromptGuardrailEngineTest` | Injection 和 Length 阻断 | 1 passed |
| `PromptDiffServiceTest` | Git 风格新增/删除/相同行 | 1 passed |
| `PreviewPromptEvaluationAdapterTest` | Preview Actual 和不伪造 LLM 执行 | 1 passed |
| `SecurityPromptPermissionAdapterTest` | Department/Private/local 权限 | 2 passed |
| `PromptApiE2ETest` | Prompt 创建→渲染→测试→发布→Runtime→Schema→新版本→Diff→A/B→Rollback→日志/审计 | 1 passed |
| `SceneApiE2ETest` | P3 Scene 绑定 Published Prompt 并渲染后执行 | 1 passed |
| P1-P3 Java 回归 | Provider、Model、Scene、加密和权限 | 18 passed |
| Prompt Vue Vitest | IDE 变量补全、Playground、测试面板 | 4 P4 tests passed |
| 全部前端 Vitest | P1-P4 前端回归 | 12 files / 17 tests passed |
| `mvn clean verify` | Java/Vue 编译、单测、HTTP E2E、生产构建和 JAR | BUILD SUCCESS |
| Packaged JAR smoke | Flyway V1-V4、Prompt 创建/渲染/脱敏日志、Scene 模板、`/prompts` SPA | UP / 200 / passed |

## Rollback Notes

- Code rollback: 回退 P4 源码及 V4 之后应用版本。
- Data rollback: 备份后删除八张 P4 表；P1-P3 数据保持。
- Configuration rollback: 移除 prompt scopes 和 Render Log 配置。
- External-system rollback: 无外部强依赖。
- Recovery validation: Provider、Model、无 Prompt 绑定的 Scene 继续运行。

## Knowledge Capture

- [x] Tests
- [x] Documentation
- [ ] Architecture decision record
- [x] Schema constraint
- [ ] Static analysis rule
- [x] Reusable component
- [x] AGENTS.md rule
- [ ] Another Skill
