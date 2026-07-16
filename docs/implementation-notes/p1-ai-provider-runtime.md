# P1 AI Provider Runtime Implementation Notes

## Metadata

- **Task / Feature:** `design-docs/001-ai-provider-runtime.md`
- **Date started:** 2026-07-16
- **Implementation owner:** Codex
- **Related Unknowns Report:** `docs/unknowns/p1-ai-provider-runtime.md`
- **Related plan / issue / PR:** 当前任务

## Confirmed Discoveries

### Discovery D-001

- **What was discovered:** 仓库只有设计文档，没有应用代码、数据库迁移或测试框架。
- **Evidence:** 仓库文件扫描。
- **Why it matters:** P1 必须同时创建符合规范的最小项目骨架。
- **Affected scope:** 后端、前端、数据库、文档和测试。
- **Action taken:** 采用一个 Maven 工程、一个 Spring Boot 应用、一个 Vue3 应用和一个数据库。

### Discovery D-002

- **What was discovered:** 默认命令行 Java 是 17，但本机 IntelliJ JBR 提供 Java 21.0.6。
- **Evidence:** 本地 `java -version` 和 JBR `javac -version`。
- **Why it matters:** 项目规范要求 Java 21。
- **Affected scope:** 构建和测试命令。
- **Action taken:** 验证时显式设置 `JAVA_HOME` 到 JBR 21。

### Discovery D-003

- **What was discovered:** SQLite 单连接池中不能在 RowMapper 尚未释放主结果集时嵌套查询标签。
- **Evidence:** 首次 HTTP E2E 在 Provider 测试入口触发 Hikari 连接等待超时。
- **Why it matters:** 默认 SQLite 必须在真实请求链路稳定运行。
- **Affected scope:** Provider 详情和列表查询。
- **Action taken:** 主 Provider 结果集关闭后再加载标签，并保留单连接池配置；修复后 E2E 全链路通过。

### Discovery D-004

- **What was discovered:** Custom Parameters 可能承载 token/secret/password 等敏感值。
- **Evidence:** Custom Provider 允许自定义认证协议。
- **Why it matters:** 仅加密落库不足以防止 API 响应泄露。
- **Affected scope:** Provider 详情响应和编辑语义。
- **Action taken:** 敏感键响应为 `****`，更新时 `****` 保留原密文值，并增加 E2E 断言。

## Decisions

### Decision DEC-001

- **Decision:** 生产使用 JWT/JWKS，开发使用仅本机可访问的 local 模式。
- **Alternatives considered:** 无鉴权、静态管理 Token、强制依赖尚不存在的 Identity。
- **Reason:** 同时满足长期安全架构和当前仓库可独立运行。
- **Evidence:** `000-tech.md` 认证规范及当前仓库状态。
- **Owner / approver:** Architecture。
- **Reversibility:** High。
- **Follow-up:** `core-identity` 可用后将部署默认模式切换为 JWT。

### Decision DEC-002

- **Decision:** 使用 AES-256-GCM 加密 Provider Secret，并通过端口隔离实现。
- **Alternatives considered:** 明文、数据库函数、外部 Vault。
- **Reason:** 满足当前安全要求且便于未来替换。
- **Evidence:** `001` 明确要求 API Key 永远加密。
- **Owner / approver:** Security。
- **Reversibility:** High。
- **Follow-up:** Secret Runtime 可用后新增适配器。

### Decision DEC-003

- **Decision:** 增加 Provider 标签表和审计日志表。
- **Alternatives considered:** JSON 字段、忽略设计中遗漏的持久化。
- **Reason:** 两项均是 P1 明确功能，独立表便于筛选和审计。
- **Evidence:** `001` 第十三节和第十七节。
- **Owner / approver:** Architecture。
- **Reversibility:** Medium。
- **Follow-up:** 无。

### Decision DEC-004

- **Decision:** 外部 Identity、Vault 和其他 Core 只保留接口与可选配置，不进入默认核心运行路径。
- **Alternatives considered:** 强制依赖外部服务、在本项目复制外部服务能力。
- **Reason:** 外部系统不可用时不得阻塞 Provider 核心能力。
- **Evidence:** 用户补充要求和独立服务规范。
- **Owner / approver:** User。
- **Reversibility:** High。
- **Follow-up:** 外部系统可用后新增适配器或启用 JWT Profile。

## Assumptions

### Assumption A-001

- **Assumption:** P1 的 availability、RPM、TPM 只建立数据结构；真实统计由后续 Gateway 调用事件驱动。
- **Why it is currently acceptable:** P1 没有推理调用链。
- **Risk:** 管理界面初期只能展示测试产生的健康数据。
- **How it will be validated:** 测试和刷新可更新延迟、成功/失败和时间。
- **Reversal plan:** Gateway Runtime 调用同一健康写入端口。

## Deviations

### Deviation DEV-001

- **Original plan:** Provider 查询在 RowMapper 内直接加载标签。
- **Actual implementation:** 主结果集关闭后再加载标签。
- **Reason for deviation:** SQLite 默认单连接池会因嵌套取连接超时。
- **User-visible effect:** 无；列表和详情稳定返回完整标签。
- **Data / API effect:** 无。
- **Risk introduced:** 小规模 Provider 列表会增加标签查询次数。
- **Approval required:** No
- **Follow-up:** Provider 数量显著增长时可改为一次批量标签查询。

## Unresolved Risks

| Risk | Impact | Current mitigation | Owner | Review trigger |
|---|---:|---|---|---|
| Custom Provider 返回格式不可穷举 | 3 | 支持常见格式并给出清晰错误 | P1/P4 | 新协议接入 |
| 能力启发式可能误判 | 3 | 保守识别、缓存可重建 | P2 | Model Runtime 实施 |
| 私有 Endpoint 具备 SSRF 属性 | 4 | JWT 管理权限、协议限制、超时、响应限制、审计 | Security | 公开部署前 |
| 标签采用结果后逐项加载 | 2 | 当前 Provider 管理规模较小 | P1 | Provider 数量或查询延迟增长 |

## Tests Added or Updated

| Test | Purpose | Result |
|---|---|---|
| `CapabilityDetectorTest` | 模型能力识别与聚合 | Pass |
| `AesGcmSecretCipherTest` | AES-GCM、随机 IV、解密和掩码 | Pass |
| `ProviderApiE2ETest` | Provider 全生命周期 HTTP E2E | Pass |
| `ConnectionResultPanel.spec.ts` | 连接成功/失败 UI 断言 | Pass |
| `ProviderList.spec.ts` | Provider 列表、空状态和选择事件 | Pass |
| `mvn clean verify` | Java/Vue 编译、全部测试、单 JAR 打包 | Pass |
| Packaged JAR smoke | Health、Provider API、静态页面 | Pass |

## Rollback Notes

- Code rollback: 删除本轮新增应用文件即可回到纯设计仓库。
- Data rollback: P1 仅新增独立数据库表；回滚前备份数据库文件。
- Configuration rollback: 移除 P1 环境变量和配置文件。
- External-system rollback: 无外部系统写操作。
- Recovery validation: 恢复数据库后执行健康检查和 Provider 列表查询。

## Knowledge Capture

- [x] Tests
- [x] Documentation
- [ ] Architecture decision record
- [x] Schema constraint
- [ ] Static analysis rule
- [x] Reusable component
- [x] AGENTS.md rule
- [ ] Another Skill
