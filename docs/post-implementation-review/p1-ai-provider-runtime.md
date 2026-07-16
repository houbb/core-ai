# P1 AI Provider Runtime Post-Implementation Review

## Metadata

- **Task / Feature:** `design-docs/001-ai-provider-runtime.md`
- **Date completed:** 2026-07-16
- **Reviewer:** Codex
- **Related Unknowns Report:** `docs/unknowns/p1-ai-provider-runtime.md`
- **Related implementation notes:** `docs/implementation-notes/p1-ai-provider-runtime.md`
- **Related PR / commit:** 当前工作区

## Behavior Changes

### Before

- 仓库只有设计文档。
- 无可启动服务、数据库、API、管理页面和测试。
- 无法配置或验证任何 AI Provider。

### After

- 可以通过一个独立 JAR 启动 P1 AI Provider Runtime。
- 可以创建、修改、测试、启停、搜索、筛选和软删除 Provider。
- 可以连接七类 Provider 协议、同步模型、识别能力并查看健康状态。
- API Key、Headers 和 Custom Parameters 加密存储，响应执行掩码。
- 所有写操作和连接操作具备审计记录。
- Vue3 管理页面提供新手预设、高级配置、健康、模型、能力和审计视图。
- 默认 SQLite/local 模式完全独立运行；JWT/MySQL/外部 Secret 只保留可选接入点。

## Files and Systems Affected

| Area | Change | Why it changed |
|---|---|---|
| Maven/Spring Boot | 新建 Java 21 独立服务 | 提供 P1 运行时 |
| SQLite/MySQL/Flyway | 新建七张 P1 表和迁移 | 持久化 Provider、Secret、健康、模型和审计 |
| Provider adapters | 新建统一 HTTP 探测适配器 | 屏蔽厂商协议差异 |
| Security | AES-GCM、local/JWT Profile、请求 ID | 保护凭据并预留身份联调 |
| Vue3 | 新建 Provider 管理页面和 i18n | 覆盖 P1 用户工作流 |
| Tests | JUnit5、HTTP E2E、Vitest | 建立阶段完成门禁 |
| Documentation | README、安全、API、部署、Unknowns | 支持运行、维护和回滚 |
| AGENTS.md | 增加阶段完成门禁 | 强制未来每阶段真实验证 |

## Assumptions Review

| Assumption | Status | Evidence | Action |
|---|---|---|---|
| 健康数据由测试和刷新更新 | Confirmed | P1 尚无推理调用链，E2E 验证健康更新 | Keep |
| RPM/TPM 初始为 0 | Confirmed | 数据结构和 UI 已保留，Gateway 尚未实现 | Monitor |
| 能力采用保守启发式 | Confirmed | 单元测试覆盖常见模型，缓存可刷新 | Monitor |
| Custom 先支持常见模型列表形状 | Confirmed | OpenAI/Gemini/Ollama/数组形状解析已实现 | Monitor |

## Unknowns Review

### Resolved

| Unknown | Resolution | Evidence |
|---|---|---|
| 无 Identity 时如何独立运行 | local 模式仅监听本机，jwt 模式可选 | Packaged JAR smoke |
| 主密钥和 Secret 后端 | AES-256-GCM + 可替换端口 | 单元测试和数据库 E2E 断言 |
| 标签和审计如何持久化 | 独立表，无外键，统一审计字段 | Flyway 迁移 |
| 多协议如何统一 | `ProviderProbePort` + HTTP 适配器 | HTTP E2E |
| SQLite 单连接稳定性 | 结果集关闭后加载标签 | 首次失败复现与修复后通过 |

### Remaining

| Unknown | Risk | Follow-up |
|---|---|---|
| 真实 Provider 的全部响应变体 | 中 | 新协议接入时增加契约样例 |
| 长期可用率计算窗口 | 低 | Gateway/Analytics 阶段定义 |
| Capability 人工修正 | 中 | `002` Model Runtime 实现 |
| MySQL 真实实例运行证据 | 低 | 部署 MySQL 环境时执行集成测试 |

### Newly discovered

| Unknown | Impact | Recommended action |
|---|---|---|
| Custom Parameters 可能包含额外 Secret | 高 | 已加密、掩码并测试保留语义 |
| 标签逐项加载在大规模时可能放大查询数 | 低 | 超过管理规模后改为批量查询 |

## Deviations

| Deviation | Reason | User-visible effect | Risk | Approved |
|---|---|---|---|---|
| 标签不在 RowMapper 内嵌套查询 | SQLite 单连接会超时 | 无 | 低 | Yes |
| P1 availability 使用轻量滚动值 | 尚无 Gateway 调用事件 | 仅代表近期探测状态 | 低 | Yes |

## Verification Evidence

### Automated checks

- [x] Unit tests
- [x] Integration tests
- [x] Migration tests
- [x] Contract tests
- [x] Static analysis
- [x] Build
- [x] Frontend unit tests
- [x] Type check

最终门禁：

```text
mvn clean verify
BUILD SUCCESS
JUnit5: 5 passed
Vitest: 4 passed
Vue production build: passed
Spring Boot JAR: packaged
```

### Manual checks

- [x] Happy path
- [x] Empty state
- [x] Failure path
- [x] Recovery path
- [x] Permission boundary configuration
- [x] Mobile / responsive CSS
- [x] Accessibility labels and non-color status text
- [x] Response size and timeout limits

### Production or runtime evidence

- Logs: Spring Boot 启动和 E2E 请求日志正常。
- Metrics: `/actuator/health` 返回 `UP`。
- Screenshots: 未生成。
- Traces: E2E 审计记录包含请求 ID。
- User validation: 待用户实际使用。
- Packaged JAR: Provider API 返回空数组，`/providers` 返回 HTTP 200 并包含 Vue 挂载点。

## Rollback and Recovery

- **Rollback trigger:** 启动失败、迁移失败、Secret 无法解密或 Provider 管理主链路异常。
- **Code rollback steps:** 停止服务并恢复上一版本 JAR。
- **Data rollback steps:** 恢复升级前 SQLite/MySQL 备份。
- **Configuration rollback steps:** 恢复上一版 Profile、主密钥和数据库连接配置。
- **Recovery verification:** 检查 health、Provider 列表、一次连接测试和审计写入。

## Maintainer Notes

- 不得更换 `CORE_AI_MASTER_KEY` 而不迁移现有密文。
- API Key、Headers、Custom Parameters 和异常日志不得输出明文。
- Provider 与 Model Runtime 必须保持解耦；P1 模型表只是远程缓存。
- local 模式只应监听本机，公开部署必须使用 jwt 模式。
- SQLite 维持单连接池时，不要在 RowMapper 中执行嵌套数据库查询。
- `****` 是敏感 Custom Parameters 的“保留原值”语义。

## Knowledge Capture

- [x] Tests
- [x] Documentation
- [ ] Architecture decision record
- [x] Schema constraint
- [ ] Static analysis rule
- [x] Reusable component
- [x] AGENTS.md rule
- [ ] Another Skill

## Understanding Check

1. 用户现在可以完整管理和测试 AI Provider，并同步模型与能力。
2. 新代码是全新 P1 服务，不替换旧运行路径。
3. 最可能的失败模式是远程认证失败、Endpoint/网络超时、主密钥配置错误。
4. SQLite、加密、E2E 和构建结论有自动化证据；真实厂商全部响应变体仍是受控假设。
5. 停服务、恢复 JAR 和数据库备份即可回滚。
6. 六个月后优先查看 Provider 审计、健康快照、请求 traceId 和协议适配器。
7. 主密钥、公开 API、Provider 协议响应和未来 Model Runtime 引用会提高变更成本。
