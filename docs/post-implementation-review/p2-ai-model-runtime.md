# P2 AI Model Runtime Post-Implementation Review

## Metadata

- **Task / Feature:** `design-docs/002-ai-model-runtime.md`
- **Date completed:** 2026-07-16
- **Reviewer:** Codex
- **Related Unknowns Report:** `docs/unknowns/p2-ai-model-runtime.md`
- **Related implementation notes:** `docs/implementation-notes/p2-ai-model-runtime.md`
- **Related PR / commit:** 当前工作区

## Behavior Changes

### Before

- P1 只保存 Provider 返回的远程模型缓存。
- 模型没有独立生命周期、业务配置、价格历史或稳定引用。
- 业务无法按能力搜索、比较、推荐或通过 Alias 解析模型。

### After

- Provider 测试或刷新会自动同步到独立 Model Registry。
- 模型具备 `DISCOVERED → REGISTERED → ENABLED → DEPRECATED/DISABLED → DELETED` 生命周期。
- 能力和上下文支持自动识别、人工修正及后续同步保护。
- 支持默认参数、版本化价格、标签、收藏、推荐、搜索和比较。
- Alias 支持多候选优先级、场景标记、默认模型和稳定解析。
- Vue3 Models 页面覆盖目录、详情、编辑、价格、Alias、比较、推荐和中英文资源。
- 默认 SQLite/local 模式独立运行；外部 Identity、价格和 Scene 服务不阻塞核心能力。

## Three-Pass Review

| Pass | Focus | Findings and actions |
|---|---|---|
| 1 | 架构和故障边界 | 隔离 Model Registry 同步失败，避免 Provider 成功连接被反向阻塞；修正纯 Embedding 的能力误判 |
| 2 | 生命周期、数据和权限 | 补齐上下文/参数/价格/能力覆盖校验；删除模型时更新 Alias 审计字段；隐藏已删除 Provider/Model 的 Alias；补齐标准默认 Alias |
| 3 | UI、i18n、测试和性能 | 收口 P2 用户文案到中英文资源；补齐忙碌态和失败时保留 Alias 输入；增加组件断言；把 Model 列表关系加载由 `3N+1` 优化为固定四次查询 |

## Files and Systems Affected

| Area | Change |
|---|---|
| Flyway | 新增六张 P2 表，全部包含统一审计字段、注释和索引，无外键 |
| Application | Model 生命周期、能力、参数、价格、Alias、搜索、比较和推荐服务 |
| P1 integration | `ModelDiscoveryPort` 将 Provider 缓存同步到 Model Registry |
| Persistence | JDBC Model Repository、逻辑删除、人工覆盖合并和批量关系加载 |
| Security | `ai.model.read` / `ai.model.manage` 路由权限 |
| Vue3 | Models 页面、详情、编辑、比较和 i18n |
| Tests | JUnit5、Spring HTTP E2E、Vitest 和 packaged JAR smoke |
| Documentation | README、API、数据库、安全、架构、Unknowns 和实施记录 |

## Verification Evidence

最终完整门禁：

```text
JAVA_HOME=D:\tools\graalvm\graalvm-jdk-21.0.1+12.1
mvn clean verify
BUILD SUCCESS
Java/JUnit5: 12 passed, 0 failed
HTTP E2E: ModelApiE2ETest + ProviderApiE2ETest passed
Vitest: 6 files, 10 tests passed
Vue type check: passed
Vue production build: passed
Spring Boot JAR: target/core-ai-0.1.0-SNAPSHOT.jar
```

成品 JAR 冒烟：

```text
Health: UP
GET /api/v1/ai/admin/models: 200, empty registry
GET /models: 200, Vue app mount present
```

门禁中发现并解决：

- 默认 `JAVA_HOME` 指向 JDK 17，改用本机 JDK 21 后重跑。
- 新增 Mockito 断言最初混用 raw value/matcher，修正后完整重跑并通过。

非阻塞警告：

- `glob@10.5.0` 为传递依赖弃用提示，`npm audit` 为 0 vulnerabilities。
- Mockito 动态 agent 和 SpringDoc 默认启用为未来兼容/生产配置提示。

## Requirements Traceability

| Requirement | Evidence |
|---|---|
| 独立 Model Registry | V2 六表 + `ModelDiscoveryPort` |
| 生命周期 | Service 状态机、JUnit5、HTTP E2E |
| 多能力与人工修正 | 能力表、覆盖 JSON、重同步保护断言 |
| Context/Parameter/Pricing | Service/API/UI、价格历史 E2E |
| Alias/默认模型/Scene 标记 | Alias API、优先级解析、默认 Alias E2E |
| 搜索/过滤/比较/推荐 | Model API、Vue 页面、单元和 E2E |
| 标签/收藏/Recommended | Repository、API、UI、E2E |
| Provider 自动同步 | Provider→Registry E2E |
| 外部服务不阻塞 | local 默认、同步异常隔离单测 |
| 编译+单测+E2E | `mvn clean verify` 和 packaged JAR smoke |

## Remaining Risks

| Risk | Current boundary |
|---|---|
| 厂商没有统一价格发现协议 | 使用版本化价格 API/UI；未来外部价格源复用 REST 契约 |
| 推荐没有真实模型级质量和延迟 | 使用确定性可解释规则；Analytics 阶段替换评分输入 |
| Scene 尚无正式实体 | P2 只保存 `scene` 文本，P3 再建立正式关系 |
| MySQL 尚无真实实例证据 | 迁移保持 SQLite/MySQL 兼容，部署环境需补集成测试 |

## Rollback and Recovery

- 停止服务并恢复上一版 JAR。
- 恢复升级前 SQLite/MySQL 备份。
- 如仅回退 P2，P1 Provider 缓存和连接能力仍可独立运行。
- 恢复后检查 health、Provider 列表和一次 Provider 连接测试。

## Knowledge Capture

- [x] Tests
- [x] Documentation
- [ ] Architecture decision record
- [x] Schema constraint
- [ ] Static analysis rule
- [x] Reusable component
- [x] AGENTS.md rule
- [ ] Another Skill
