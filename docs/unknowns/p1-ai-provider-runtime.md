# P1 AI Provider Runtime Unknowns Report

## Metadata

- **Task / Feature:** `design-docs/001-ai-provider-runtime.md`
- **Mode:** Standard
- **Date:** 2026-07-16
- **Prepared by:** Codex
- **Scope:** AI Provider 的管理、加密凭据、协议适配、连接测试、模型缓存、能力识别、健康状态、搜索筛选、审计和管理界面

## Intent

### User-visible problem

平台尚无可运行代码，用户无法通过统一界面配置、验证和管理云端或本地 AI Provider。

### Desired behavior change

用户可以创建 Provider、填写连接信息、测试连接、同步模型、查看健康和能力、启停或软删除 Provider，并且不会在 API 或界面中泄露明文密钥。

### Affected users and workflows

- 平台管理员：Provider 的创建、修改、测试、启停、删除、搜索和筛选。
- 后续运行时：通过稳定的 Provider、能力和模型缓存数据访问 AI 连接。
- 运维人员：通过健康信息、错误详情和审计记录排查连接问题。

### Success criteria

- 默认 SQLite 配置可独立启动，MySQL 配置可切换。
- 支持 OpenAI Compatible、Anthropic、Gemini、Ollama、LM Studio、Azure OpenAI 和 Custom 类型。
- API Key 使用 AES-GCM 加密存储，读取接口仅返回掩码。
- 连接测试返回认证、延迟、模型、能力和用户可理解的错误。
- 测试成功可同步模型缓存并更新健康状态。
- Provider 支持多实例、标签、优先级、权重、启停、软删除和筛选。
- 所有写操作和连接操作均写入审计日志。
- Vue3 管理界面覆盖完整管理流程，并提供中英文文案。
- JUnit5 单元测试和 HTTP 端到端测试覆盖关键流程。

### Non-goals

- 不实现 `002-ai-model-runtime.md` 中的模型价格、参数、别名、默认模型和推荐。
- 不实现 Gateway 的路由、重试、熔断、限流和业务推理调用。
- 不实现 `core-identity` 本身，只预留并验证 JWT/JWKS 接入。
- 不接入外部 Vault；密钥加密端口保持可替换。

## Evidence Reviewed

| Source | Location | What it confirms | Confidence |
|---|---|---|---|
| Documentation | `design-docs/001-ai-provider-runtime.md` | P1 功能、生命周期、协议类型、UX 和核心数据 | High |
| Documentation | `design-docs/000-tech.md` | Java/Spring/Vue/SQLite 技术栈、三层结构、API 和发布规范 | High |
| Repository | repository root | 当前只有设计文档，无应用代码、迁移和测试 | High |
| Environment | local Java installations | IntelliJ JBR 提供 Java 21.0.6，可满足编译测试 | High |
| Environment | local Maven repository | Spring Boot 3.5.16、SQLite JDBC、Flyway 和测试依赖可用 | High |
| Git | `git status` | `AGENTS.md` 存在用户未提交修改，必须保留 | High |

## Confirmed Facts

| Fact | Evidence | Relevance |
|---|---|---|
| 本轮以文件编号为阶段编号，`001` 是 P1 | 用户补充说明 | 固定实施边界 |
| 项目必须是一个独立 Maven 服务和一个 Vue 应用 | `000-tech.md` | 决定工程骨架 |
| Provider 与 Model 必须解耦 | `001` 第十七节 | 模型表仅作为远程缓存 |
| 密钥必须加密且 UI 只显示掩码 | `001` 第十七节 | 决定加密和响应契约 |
| 删除必须保留历史关联 | `001` 生命周期 | 使用软删除状态 |
| 所有连接操作必须审计 | `001` 第十七节 | 增加审计表和查询能力 |
| 所有表必须有统一审计字段且禁止外键 | `AGENTS.md` | 所有表使用 `id/create_time/update_time/create_user/update_user`，不创建外键 |

## Critical Unknowns

| Unknown | Category | Evidence / Reasoning | Impact | Probability | Irreversibility | Late discovery cost | Priority | Disposition | Resolution |
|---|---|---|---:|---:|---:|---:|---:|---|---|
| `core-identity` 尚不存在，管理 API 如何鉴权 | Known unknown | 技术规范要求 JWT，本仓库无法联调身份服务 | 5 | 5 | 2 | 5 | 250 | Decision | 生产模式支持 JWT/JWKS 和 scope；开发模式仅绑定 `127.0.0.1` 并记录 `local-admin` |
| 主密钥从哪里获得 | Known unknown | 文档只要求加密，未定义 Secret Runtime | 5 | 5 | 3 | 5 | 375 | Decision | 使用环境变量提供的 256-bit Base64 主密钥；开发/测试使用明确标识的本地配置，端口可替换为 Vault |
| Custom Provider 的协议格式未定义 | Known unknown | 文档说明未来由插件实现 | 3 | 4 | 1 | 3 | 36 | Accept | 提供受限通用适配器：可配置 models path 和认证头，解析常见模型列表形状；插件系统留到后续 |
| Provider 标签没有对应设计表 | Unknown unknown candidate | 功能要求支持标签，但五张建议表未覆盖 | 3 | 5 | 1 | 3 | 45 | Decision | 增加 `ai_provider_tag` 表，保持规范化和可查询 |
| 审计日志没有对应设计表 | Unknown unknown candidate | 明确要求审计，但五张建议表未覆盖 | 5 | 5 | 1 | 4 | 100 | Decision | 增加 `ai_audit_log` 表，详情禁止包含密钥 |
| 私有地址可能被 Provider 测试访问 | Unknown unknown candidate | Ollama/LM Studio 必须支持本地地址，无法简单禁止内网 | 4 | 3 | 2 | 4 | 96 | Monitor | 仅授权管理员可配置；限制协议为 HTTP(S)、设置连接/读取超时、限制响应体大小并审计 |
| Provider 能力自动识别准确率 | Known unknown | P1 未提供厂商能力元数据标准 | 3 | 5 | 1 | 2 | 30 | Experiment | 基于模型 ID 和厂商返回字段做保守识别，结果允许后续 P2 人工修正 |

## Implicit Expectations

| Expectation | Why it may exist | How to surface it |
|---|---|---|
| 第一次使用不应面对复杂配置 | `001` 新手模式和高级折叠 | 使用 Provider 预设卡片与默认 Endpoint |
| 错误必须可行动而不是只显示 HTTP 码 | `001` Connection Test UX | 将 401、403、404、429、超时和网络错误映射为明确提示 |
| 本地与云端体验一致 | `001` 设计原则 | 使用同一列表、详情和测试流程，仅预设不同 |
| API Key 修改时不应要求重复填写 | 密钥掩码 UX | 更新请求中 `apiKey=null` 表示保留，非空表示替换 |

## Blind-Spot Candidates

| Candidate | Why it may matter | Validation method |
|---|---|---|
| Endpoint 尾部路径差异导致重复 `/v1` | 不同兼容服务的 Endpoint 习惯不同 | 为 URL 拼接编写单元测试 |
| 测试失败覆盖已有可用状态 | 短暂网络故障不应破坏配置 | 状态转换断言和端到端失败路径 |
| 模型刷新删除历史模型 | 上层运行时未来可能仍引用缓存项 | 缺失模型标记为 `INACTIVE`，不物理删除 |
| 多次测试产生明文泄露 | 日志、错误和审计可能意外包含请求头 | 测试响应、审计详情和数据库内容断言 |
| SQLite/MySQL SQL 差异 | 双数据库是完成标准 | 使用保守 JDBC SQL；提供独立配置并对 SQLite 真实迁移测试 |

## Decisions Required

已通过仓库事实和保守、可逆实现解决，无需阻塞用户：

| Decision | Options | Trade-offs | Recommended owner | Deadline / Trigger |
|---|---|---|---|---|
| 身份模式 | 本地无鉴权 / 静态 Token / JWT | JWT 符合长期架构；本地模式保证首次可运行 | Architecture | P1 |
| 密钥后端 | 本地 AES / Vault | 本地 AES 可立即运行；端口化便于后续替换 | Security | P1 |
| 标签和审计存储 | JSON 字段 / 独立表 | 独立表查询清晰，改动可逆 | Architecture | P1 |

## Experiments or Prototypes Required

| Question | Method | Success signal | Cost | Owner |
|---|---|---|---|---|
| 多协议模型列表能否统一 | 使用本地假 Provider 做 HTTP E2E | 创建、测试、同步、识别能力全链路通过 | Medium | Implementation |
| SQLite 迁移和 JDBC SQL 是否可运行 | 启动真实 Spring Boot 测试上下文 | Flyway 成功且 CRUD 通过 | Low | Implementation |
| 管理 UI 是否覆盖关键状态 | 组件单测和生产构建 | 空状态、成功、失败、筛选和详情均可渲染 | Medium | Implementation |

## Safe Assumptions

| Assumption | Why it is safe | Reversal plan |
|---|---|---|
| P1 健康数据主要由测试和刷新更新 | 业务调用尚未实现，没有真实流量可采集 | Gateway Runtime 接入调用结果后更新同一健康端口 |
| RPM/TPM 在 P1 初始为 0 | 当前没有推理调用链 | 后续由 Gateway/Analytics 写入 |
| 能力识别采用保守启发式 | 结果为缓存，不是不可逆业务数据 | P2 增加人工校正和协议元数据 |
| Custom 先支持常见 JSON 列表形状 | 文档明确完整 Custom 由未来插件实现 | 插件注册新适配器替换通用实现 |

## Deferred Unknowns

| Unknown | Why deferred | Monitoring / Follow-up |
|---|---|---|
| Vault/Core Config 对接协议 | 对应服务尚不存在 | 保持 `SecretCipherPort`，后续替换适配器 |
| Provider 真实可用率统计窗口 | 缺少 Gateway 调用事件 | P5 Gateway 接入后定义滑动窗口 |
| Capability 人工校正规则 | 属于 Model Runtime | 在 `002` 阶段处理 |

## Recommended Implementation Boundary

### Implement now

- 独立 Spring Boot/Vue3 工程、SQLite/MySQL 配置、Flyway 迁移。
- Provider 全量管理 API、协议适配、测试、同步、健康、能力、标签、搜索和审计。
- AES-GCM 密钥加密、掩码响应、开发与 JWT 安全模式。
- Apple 风格响应式管理 UI 和中英文。
- JUnit5 单元测试、Spring Boot HTTP 端到端测试、前端组件断言测试。

### Do not implement now

- 模型定价、参数、别名、默认模型和推理调用。
- Gateway 路由、重试、限流、成本统计。
- 外部 Secret Runtime 和身份服务本体。

### Interfaces or data contracts to freeze

- `/api/v1/ai/admin/providers`
- Provider 类型、状态、能力枚举。
- `SecretCipherPort`、`ProviderAdapter` 和模型探测结果。
- 密钥更新的保留/替换语义。

### Areas that must remain reversible

- 安全认证提供者。
- Custom Provider 协议实现。
- 能力识别策略。
- Secret 存储适配器。

## Verification Plan

### Automated

- Unit tests: 加密、URL 归一化、能力识别、状态规则、错误映射。
- Integration tests: SQLite Flyway、Repository 和 Service。
- End-to-end tests: HTTP 创建 → 测试 → 同步 → 筛选 → 启停 → 软删除 → 审计。
- Contract tests: 多协议假服务响应解析和错误响应。
- Static analysis: Java 编译、TypeScript 检查、Vue 生产构建。

### Manual

- Happy path: 使用本地假 OpenAI Compatible 服务连接成功。
- Empty state: 无 Provider 时显示引导卡片。
- Failure path: 401、超时、无效 Endpoint 显示明确错误。
- Recovery path: 更换密钥后重新测试并启用。
- Permission boundaries: JWT 模式下读写 scope 分离。
- Mobile / responsive: 单列布局和自适应宽度。
- Accessibility: 表单 label、键盘按钮、状态文本和颜色同时表达。

### Observability

- Logs: 请求 ID、连接测试结果，不记录密钥。
- Metrics: Actuator 健康端点。
- Alerts: P1 不实现主动告警。
- Audit trail: 创建、更新、测试、刷新、启停、删除。

## Handoff

- [x] Acceptance criteria
- [x] Explicit invariants
- [x] Data and interface contracts
- [x] Test cases
- [x] Rollback requirements
- [x] Observability requirements
- [x] Non-goals
- [x] Implementation notes file
