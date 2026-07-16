# P2 AI Model Runtime Implementation Notes

## Metadata

- **Task / Feature:** `design-docs/002-ai-model-runtime.md`
- **Date started:** 2026-07-16
- **Implementation owner:** Codex
- **Related Unknowns Report:** `docs/unknowns/p2-ai-model-runtime.md`
- **Related plan / issue / PR:** 当前任务

## Confirmed Discoveries

### Discovery D-001

- **What was discovered:** P1 已提供经过协议适配和能力识别的 `DiscoveredModel`，但只保存为 Provider 缓存。
- **Evidence:** `ProviderService.runProbe` 和 `ai_provider_model_cache`。
- **Why it matters:** P2 可以复用发现结果，但必须保持独立 Registry。
- **Affected scope:** P1/P2 同步边界。
- **Action taken:** 计划新增 `ModelDiscoveryPort`，Provider 探测成功后调用。

### Discovery D-002

- **What was discovered:** P1 Capability 枚举缺少 Video、Moderation、OCR、Tool Call、JSON Mode 和 Streaming。
- **Evidence:** P1 `Capability` 与 P2 设计对比。
- **Why it matters:** P2 搜索和模型详情需要完整能力集合。
- **Affected scope:** Capability 枚举、Model 数据和前端类型。
- **Action taken:** 扩展共享枚举，但不改变 P1 Provider 能力表结构。

### Discovery D-003

- **What was discovered:** Provider 探测成功后，如果 Model Registry 写入异常，原实现会让 Provider 保持 `TESTING` 并把连接成功误判为失败。
- **Evidence:** 三轮审查中的 `ProviderService.runProbe` 失败路径分析。
- **Why it matters:** P2 内部同步不应阻塞 P1 Provider 核心连接能力。
- **Affected scope:** Provider→Model 自动同步、审计和故障恢复。
- **Action taken:** 先提交 Provider 缓存、能力、健康和状态，再隔离 Model Registry 同步异常并记录失败审计；手工同步入口可重试。

### Discovery D-004

- **What was discovered:** Model 列表逐模型加载价格、Alias 和标签会形成 `3N+1` 查询。
- **Evidence:** `JdbcModelRepository.search` 的关系装配路径。
- **Why it matters:** P2 目标包含几十到上百个模型，逐项查询会放大 SQLite 往返。
- **Affected scope:** Model 搜索、推荐和管理页面。
- **Action taken:** 改为主查询后批量加载三类关系，查询次数固定为四次。

## Decisions

### Decision DEC-001

- **Decision:** P2 使用独立六表 Model Registry，不把 P1 缓存表升级为业务模型表。
- **Alternatives considered:** 直接扩展 `ai_provider_model_cache`。
- **Reason:** 保持 Provider=连接、Model=能力的职责边界。
- **Evidence:** P1/P2 设计原则。
- **Owner / approver:** Architecture。
- **Reversibility:** Medium。
- **Follow-up:** P3 只依赖 P2 模型接口。

### Decision DEC-002

- **Decision:** 人工能力覆盖按 Capability→Boolean 保存，上下文使用独立人工覆盖标记。
- **Alternatives considered:** 每次同步覆盖、整模型锁定。
- **Reason:** 精确保护人工修正，同时继续同步其他字段。
- **Evidence:** P2 明确要求能力自动识别且可人工修正。
- **Owner / approver:** Architecture。
- **Reversibility:** High。
- **Follow-up:** UI 提供恢复自动识别入口。

### Decision DEC-003

- **Decision:** 默认模型通过标准 Alias 表示，Alias 允许多候选。
- **Alternatives considered:** 新增默认模型表、Alias 单一指向。
- **Reason:** 避免重复数据模型，并为 Gateway fallback 留出稳定接口。
- **Evidence:** `ai_model_alias.priority` 和 Alias 长期稳定原则。
- **Owner / approver:** Architecture。
- **Reversibility:** High。
- **Follow-up:** Gateway 阶段扩展候选选择策略。

## Assumptions

### Assumption A-001

- **Assumption:** P2 推荐使用 Provider 健康延迟和当前价格作为模型速度/成本代理。
- **Why it is currently acceptable:** 尚无模型级推理调用数据。
- **Risk:** 同 Provider 不同模型的真实速度差异无法体现。
- **How it will be validated:** 单元测试确保排序确定且原因可解释。
- **Reversal plan:** Analytics 提供模型级指标后替换评分输入。

## Deviations

| Deviation | Reason | User-visible effect | Risk |
|---|---|---|---|
| Provider 标准发现协议不自动提供价格 | 主流 `/models` 协议没有统一价格字段 | 价格通过版本化管理 API/UI 手工维护；未来外部价格系统可调用同一接口 | Low |
| Scene Binding 在 P2 仅保存 `scene` 文本 | 完整 Scene Runtime 属于下一阶段 | Alias 已可按场景标记，但不提前创建跨阶段实体依赖 | Low |

## Unresolved Risks

| Risk | Impact | Current mitigation | Owner | Review trigger |
|---|---:|---|---|---|
| 真实模型价格无法标准自动同步 | 3 | 价格历史支持手工维护，REST 契约可供外部价格源写入 | P2/Analytics | 新 Provider 元数据可用 |
| 推荐缺少真实质量分 | 3 | 可解释规则 + recommended 标记 | Analytics | Evaluation 数据可用 |
| Scene 仅保存文本 | 2 | 不创建错误的提前依赖 | P3 | Scene Runtime 实施 |

## Tests Added or Updated

| Test | Purpose | Result |
|---|---|---|
| `CapabilityDetectorTest` | 特化模型、多模态和纯 Embedding 能力识别 | 2 passed |
| `ModelServiceTest` | 生命周期、推荐排序、上下文、价格和能力覆盖校验 | 5 passed |
| `ProviderServiceTest` | Model Registry 失败不阻塞 Provider 核心能力 | 1 passed |
| `ModelApiE2ETest` | Provider 发现到 Model 删除的完整 HTTP 主链路 | 1 passed |
| `ProviderApiE2ETest` | P1 回归与 P1→P2 联动 | 1 passed |
| `ModelList/Compare/Detail/Editor` Vitest | 友好名称、比较、忙碌态、价格和 Alias 交互 | 6 P2 tests passed |
| 全部前端 Vitest | P1/P2 前端回归 | 10 passed |
| `mvn clean verify` | Java/Vue 编译、单测、E2E、生产构建和 JAR | BUILD SUCCESS |
| Packaged JAR smoke | `/actuator/health`、Model API、`/models` SPA | UP / 200 / passed |

## Rollback Notes

- Code rollback: 回退 P2 源码和 V2 之后的应用版本。
- Data rollback: 备份后删除六张 P2 表；P1 Provider 数据不受影响。
- Configuration rollback: 恢复 P1 路由和 model scope 配置。
- External-system rollback: 无。
- Recovery validation: P1 Provider 列表和测试仍可独立运行。

## Knowledge Capture

- [x] Tests
- [x] Documentation
- [ ] Architecture decision record
- [x] Schema constraint
- [ ] Static analysis rule
- [x] Reusable component
- [x] AGENTS.md rule
- [ ] Another Skill
