# P2 AI Model Runtime Unknowns Report

## Metadata

- **Task / Feature:** `design-docs/002-ai-model-runtime.md`
- **Mode:** Standard
- **Date:** 2026-07-16
- **Prepared by:** Codex
- **Scope:** Model Registry、发现同步、生命周期、能力、上下文、参数、价格历史、Alias、默认模型、标签、收藏、推荐、比较、搜索和管理界面

## Intent

### User-visible problem

P1 只能展示 Provider 返回的模型缓存，用户无法把模型作为稳定、可配置、可比较、可被业务引用的独立资源管理。

### Desired behavior change

用户可以把 Provider 发现的模型自动纳入统一目录，确认注册并启用，维护能力、上下文、默认参数和价格历史，通过 Alias/默认模型稳定引用，并按能力、成本、速度和上下文搜索、比较与推荐模型。

### Affected users and workflows

- 平台管理员：发现、注册、启停、弃用、删除和配置模型。
- 业务运行时：以后通过 Alias 或默认能力获取模型，而不是写死远程 Model ID。
- 成本和场景运行时：读取价格历史、能力、上下文和推荐结果。
- P1 Provider 管理：测试或刷新后自动同步 Model Registry。

### Success criteria

- P1 Provider 刷新成功后自动创建或更新 P2 模型目录。
- 生命周期覆盖 Discovered、Registered、Enabled、Deprecated、Disabled、Deleted。
- 能力自动识别且可人工修正，后续同步保留人工覆盖。
- 支持上下文、默认参数、价格历史、标签、收藏和推荐标记。
- Alias 可按优先级绑定多个模型并提供稳定解析接口。
- 支持默认 Chat、Embedding、Vision、OCR、Reasoning、Image 等 Alias。
- 支持搜索、过滤、比较和可解释推荐。
- Vue3 管理界面覆盖目录、详情、编辑、比较、推荐和 Alias。
- JUnit5、HTTP E2E、前端断言、完整构建和最终 JAR 冒烟通过。

### Non-goals

- 不实现 `003-ai-scene-runtime.md` 的 Scene 表、发布、版本和工作流。
- 不实现真实推理调用、Gateway 路由和请求级参数覆盖。
- 不从厂商网页抓取价格；P2 只支持发现协议提供的数据和管理员价格历史。
- 不依赖外部 Identity、Billing、Analytics 或 Secret 服务。

## Evidence Reviewed

| Source | Location | What it confirms | Confidence |
|---|---|---|---|
| Documentation | `design-docs/002-ai-model-runtime.md` | P2 功能、生命周期、六张核心表和 UX | High |
| Documentation | `design-docs/000-tech.md` | 三层结构、Java/Vue/SQLite、API 和数据库规范 | High |
| Code | `ProviderService` | Provider 测试后已有统一发现模型结果 | High |
| Code | `ProviderRepository` / `ai_provider_model_cache` | P1 缓存可作为 P2 发现输入，但不能成为 Model Registry | High |
| Code | `CapabilityDetector` | 已有基础能力识别，可扩展到 P2 能力集合 | High |
| Tests | `ProviderApiE2ETest` | P1 已具备可复用的真实 HTTP 假 Provider | High |
| UI | `ProvidersPage` / `App.vue` | 已有 Apple 风格和路由，可扩展 Models 导航 | High |

## Confirmed Facts

| Fact | Evidence | Relevance |
|---|---|---|
| 本轮只实现文件编号 `002` | 用户要求每次一个 P | 固定 P2 边界 |
| Provider 与 Model 必须解耦 | `002` 核心原则 | 新建独立 Model Registry |
| P1 模型表只是缓存 | P1 实现和文档 | P2 不复用缓存表承载业务配置 |
| 所有表必须有五个审计字段且禁止外键 | `AGENTS.md` | P2 六张表遵守统一规范 |
| 外部服务不能阻塞核心能力 | 用户在 P1 的补充要求 | P2 默认完全本地运行 |
| 每阶段必须编译、JUnit5 和 E2E | `AGENTS.md` 阶段门禁 | P2 完成标准 |

## Critical Unknowns

| Unknown | Category | Evidence / Reasoning | Impact | Probability | Irreversibility | Late discovery cost | Priority | Disposition | Resolution |
|---|---|---|---:|---:|---:|---:|---:|---|---|
| P1 缓存与 P2 Registry 如何同步 | Known unknown | 两层数据职责不同但必须联动 | 5 | 5 | 3 | 5 | 375 | Decision | Provider 探测成功后通过 `ModelDiscoveryPort` 同步；另提供从缓存手工同步入口 |
| 人工能力修正如何避免被同步覆盖 | Known unknown | 文档明确要求保留人工覆盖 | 5 | 5 | 3 | 5 | 375 | Decision | 能力表保存逐能力人工覆盖 JSON，自动同步只更新未覆盖项 |
| 人工上下文如何避免被同步覆盖 | Unknown unknown candidate | 文档要求自动同步 Context，用户也可编辑 | 4 | 4 | 2 | 4 | 128 | Decision | `context_manually_overridden` 标记控制同步 |
| 同一 Alias 是否允许多个模型 | Known unknown | Alias 数据模型包含 priority，暗示候选列表 | 5 | 4 | 3 | 4 | 240 | Decision | 允许同一 Alias 绑定多个模型，按 priority 解析首个 Enabled 模型 |
| 默认模型如何表示 | Known unknown | 文档列出能力默认模型但没有单独表 | 4 | 5 | 2 | 4 | 160 | Decision | 使用稳定 Alias：`chat-default`、`embedding-default` 等 |
| 推荐算法质量标准 | Unknown known | “最好、最快、便宜”没有精确公式 | 4 | 5 | 1 | 3 | 60 | Experiment | 使用确定性可解释评分：当前价格、Provider 延迟、上下文、能力、favorite/recommended |
| 厂商价格如何自动同步 | Known unknown | P1 发现协议不包含标准价格 | 4 | 5 | 1 | 3 | 60 | Accept | P2 支持价格历史和手工录入；协议未来提供价格时可接入 |
| Provider 模型暂时消失时是否禁用 Registry | Unknown unknown candidate | 临时 API 不完整不应破坏业务引用 | 5 | 3 | 3 | 5 | 225 | Decision | 只标记 `available_from_provider=false`，不自动改变管理员生命周期 |
| Scene Binding 在 P2 如何落地 | Known unknown | Scene Runtime 是下一阶段 | 3 | 5 | 2 | 3 | 90 | Accept | Alias 保存可选 `scene` 文本，不创建 Scene 外键或提前实现 P3 |

## Implicit Expectations

| Expectation | Why it may exist | How to surface it |
|---|---|---|
| 管理员主要看友好名称而不是远程 ID | `002` UX 明确要求 | 卡片使用 displayName，远程 ID 仅在 Basic 次要区域 |
| 同步不能破坏人工配置 | 能力和价格长期维护需求 | E2E 验证人工能力/上下文在再次同步后保留 |
| 推荐结果必须说明原因 | “便宜、最快、最好”需要可判断 | 推荐 API 返回 score 和 reason |
| Alias 是长期契约 | 后续业务全部依赖 Alias | 唯一性、优先级和解析 E2E |

## Blind-Spot Candidates

| Candidate | Why it may matter | Validation method |
|---|---|---|
| 同名远程模型出现在不同 Provider | 混合部署常见 | 唯一键使用 `(provider_id, remote_model_id)` |
| 价格生效时间相同 | 会导致当前价格不确定 | 同模型同生效时间唯一约束 |
| Alias 指向 Disabled/Deleted 模型 | 解析可能返回不可调用资源 | 解析只返回 Enabled 且 Provider 可用的模型 |
| 推荐模型来自 Disabled Provider | 推荐不可执行 | 推荐过滤 Model Enabled、Provider Enabled 和可用性 |
| 删除模型破坏历史 Alias/价格 | 后续审计和成本需要历史 | 逻辑删除，不物理删除历史表 |
| 能力枚举与 P1 Provider 能力列不一致 | P2 新增 OCR/Video/Tool/JSON/Streaming | Provider 表保持原列，Model 表使用扩展枚举 |

## Decisions Required

已通过现有代码、设计约束和可逆策略解决，无需阻塞用户：

| Decision | Options | Trade-offs | Recommended owner | Deadline / Trigger |
|---|---|---|---|---|
| Model 同步入口 | 复用缓存表 / 新 Registry | 新 Registry 边界正确但需要同步端口 | Architecture | P2 |
| Alias 基数 | 单 Alias 单 Model / 多候选 | 多候选支持未来 fallback | Architecture | P2 |
| 推荐算法 | 黑盒 AI / 确定性评分 | 确定性易测试和解释 | Product | P2 |
| 人工覆盖 | 整体锁定 / 逐字段覆盖 | 逐字段精确但需额外元数据 | Architecture | P2 |

## Experiments or Prototypes Required

| Question | Method | Success signal | Cost | Owner |
|---|---|---|---|---|
| P1→P2 自动同步能否稳定 | 扩展本地假 Provider E2E | Provider 测试后模型自动出现在 Registry | Medium | Implementation |
| 人工覆盖是否能跨同步保留 | E2E 更新后再次刷新 Provider | 手工能力和上下文值不变 | Medium | Implementation |
| 推荐是否可解释 | 单元测试固定模型组合 | cheapest/fastest/context/best 排序稳定且返回原因 | Medium | Implementation |

## Safe Assumptions

| Assumption | Why it is safe | Reversal plan |
|---|---|---|
| 当前价格取生效时间不晚于当前时刻的最新记录 | 常见价格版本语义 | 后续 Cost Runtime 可增加组织/合同维度 |
| Model Category 使用一个主分类，Capability 支持多选 | 符合设计的 category + 多能力结构 | Category 可重新计算或人工修改 |
| Alias 解析返回优先级最小的可用模型 | 数据模型已有 priority | Gateway 阶段扩展为策略路由 |
| 推荐使用 Provider 级延迟 | P2 尚无模型级调用统计 | Analytics 有模型级数据后替换评分输入 |

## Deferred Unknowns

| Unknown | Why deferred | Monitoring / Follow-up |
|---|---|---|
| Scene 实体完整绑定 | 属于 `003` | P3 将 scene 文本迁移到正式关系 |
| 模型级真实质量评分 | 尚无调用和评测数据 | Analytics/Evaluation 阶段接入 |
| 合同价、组织价和区域价 | 属于 Cost/Billing | 保留价格历史扩展空间 |
| Alias 的动态路由策略 | 属于 Gateway | P5 读取候选列表 |

## Recommended Implementation Boundary

### Implement now

- 六张 P2 核心表、迁移、Repository、Service 和审计。
- P1 Provider 探测成功后自动同步 Model Registry。
- 生命周期、能力人工覆盖、上下文、参数、价格历史、Alias、默认模型、标签、收藏和推荐。
- 搜索、过滤、比较、推荐和 Alias 解析 API。
- Models 管理界面、比较面板、Alias 管理和中英文。
- P2 单元、HTTP E2E、前端断言、完整构建和 JAR 冒烟。

### Do not implement now

- Scene 实体、版本、发布和工作流。
- 推理调用、Gateway fallback 和真实模型级 SLA。
- 外部价格抓取和外部服务强依赖。

### Interfaces or data contracts to freeze

- `/api/v1/ai/admin/models`
- `/api/v1/ai/admin/model-aliases`
- `ModelDiscoveryPort`
- Model 状态、分类、能力和 Alias 解析语义。

### Areas that must remain reversible

- 推荐评分权重。
- 能力自动识别规则。
- Scene 文本绑定。
- 价格来源和未来合同维度。

## Verification Plan

### Automated

- Unit tests: 生命周期、能力覆盖合并、推荐排序、Alias 校验。
- Integration tests: V2 Flyway、Repository、价格版本和 Alias 解析。
- End-to-end tests: Provider 创建/测试 → Model 发现 → 注册/启用 → 配置 → Alias → 推荐/比较 → 再同步 → 删除。
- Contract tests: P1 缓存到 P2 Registry 的接口契约。
- Static analysis: Java 编译、Vue 类型检查、生产构建、依赖方向。

### Manual

- Happy path: OpenAI Compatible 假 Provider 自动发现四个模型。
- Empty state: 无模型时显示同步引导。
- Failure path: 非法生命周期、重复 Alias、无可用 Alias 返回明确错误。
- Recovery path: Provider 再同步后模型恢复 available。
- Permission boundaries: JWT 模式新增 model read/manage scope。
- Mobile / responsive: 模型列表和详情单列。
- Accessibility: 表单 label、状态文本、键盘按钮。
- Performance: 搜索和比较限制结果规模。

### Observability

- Logs: 同步数量和异常，不记录 Provider Secret。
- Metrics: 复用 Actuator。
- Alerts: P2 不实现主动告警。
- Audit trail: 模型更新、状态、能力、参数、价格、Alias、收藏和推荐。

## Handoff

- [x] Acceptance criteria
- [x] Explicit invariants
- [x] Data and interface contracts
- [x] Test cases
- [x] Rollback requirements
- [x] Observability requirements
- [x] Non-goals
- [x] Implementation notes file
