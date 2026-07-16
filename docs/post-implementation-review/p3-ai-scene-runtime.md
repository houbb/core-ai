# P3 AI Scene Runtime Post-Implementation Review

## Metadata

- **Task / Feature:** `design-docs/003-ai-scene-runtime.md`
- **Date completed:** 2026-07-16
- **Reviewer:** Codex
- **Related Unknowns Report:** `docs/unknowns/p3-ai-scene-runtime.md`
- **Related implementation notes:** `docs/implementation-notes/p3-ai-scene-runtime.md`
- **Related PR / commit:** 当前工作区

## Behavior Changes

### Before

- P2 只有 Provider、Model Registry 和稳定 Model Alias。
- 业务侧没有可发布的 AI Scene、参数组合、权限、版本、模板或 Playground。
- Prompt、Gateway、Workflow 和外部 Identity 尚未实现，直接依赖会阻塞核心能力。

### After

- Scene 支持创建、编辑、搜索、归档和完整生命周期管理。
- 发布前强制当前版本真实完成测试；发布版本保存不可变快照。
- 支持按历史版本回滚到新的 Draft，避免覆盖发布证据。
- 支持多 Model Alias、主备模型、参数、Prompt 引用、权限和结构化 Workflow。
- 提供八个内置模板、自定义模板、实例化、导入和可移植 JSON 导出。
- 业务目录只暴露已发布、启用、权限允许且模型 Alias 可解析的 Scene。
- Playground 和 Runtime 走统一 `SceneExecutionPort`；默认返回明确 Preview，不伪造真实 AI 输出。
- 权限默认从本地/JWT claims 解析，不调用外部 Identity。
- Vue3 Scenes 页面覆盖目录、模板、全量配置、Playground、版本、分享和中英文资源。

## Three-Pass Review

| Pass | Focus | Findings and actions |
|---|---|---|
| 1 | 架构和故障边界 | 隐藏 Alias 已失效的 Published Scene；导出包移除内部模型解析字段；默认执行和权限 adapter 改为可替换 Bean |
| 2 | 数据、安全和审计 | 缓存价格不再错误标记 CHEAP；自定义模板编码冲突返回明确错误；补齐导入/实例化/删除模板审计；输入正文不进入审计 |
| 3 | UI、i18n、测试和性能 | 模板实例化支持覆盖 code；Published Scene 可直接打开 Runtime Playground；列表 Alias 解析增加请求级缓存；修正 Prompt 类型和无用导入 |

## Files and Systems Affected

| Area | Change |
|---|---|
| Flyway | 新增七张 P3 表，全部包含统一审计字段、注释和索引，无外键；初始化八个模板 |
| Application | Scene 生命周期、测试发布门禁、版本、回滚、模板、导入导出、目录和执行服务 |
| P2 integration | Scene 只保存稳定 Model Alias，并通过 Model Registry 解析可用性、价格和成本层级 |
| Ports | `SceneExecutionPort`、`ScenePermissionPort`、`SceneRepository` |
| Persistence | JDBC Scene Repository、批量子项加载、快照和共享审计日志 |
| Security | `ai.scene.read` / `ai.scene.manage` / `ai.scene.execute` 和本地/JWT 权限映射 |
| Vue3 | Scenes 页面、编辑器、模板面板、Playground、版本和分享 |
| Tests | JUnit5、Spring HTTP E2E、Vitest 和 packaged JAR smoke |
| Documentation | README、API、数据库、安全、架构、CHANGELOG、Unknowns 和实施记录 |

## Verification Evidence

最终完整门禁：

```text
JAVA_HOME=D:\tools\graalvm\graalvm-jdk-21.0.1+12.1
mvn clean verify
BUILD SUCCESS
Java/JUnit5: 19 passed, 0 failed, 0 errors, 0 skipped
HTTP E2E: ProviderApiE2ETest + ModelApiE2ETest + SceneApiE2ETest passed
Vitest: 9 files, 13 tests passed
Vue type check: passed
Vue production build: passed
Spring Boot JAR: target/core-ai-0.1.0-SNAPSHOT.jar
```

成品 JAR 独立进程冒烟：

```text
Flyway: V1, V2, V3 applied
Health: UP
GET /api/v1/ai/admin/scenes: 200, 0 scenes
GET /api/v1/ai/admin/scene-templates: 200, 8 templates
GET /api/v1/ai/scenes: 200, 0 published scenes
GET /scenes: 200, Vue app mount present
```

门禁中发现并解决：

- 默认 adapter 最初把 `@ConditionalOnMissingBean` 放在组件类上，导致 Spring 上下文没有注册执行端口；改为显式配置类后重跑完整门禁通过。
- Windows PowerShell 冒烟脚本最初把 JSON 数组包装为单对象导致错误计数；修正脚本计数语义后，使用全新 SQLite 数据库重跑通过。

非阻塞警告：

- Mockito 动态 agent、SpringDoc 默认启用和 `glob` 传递依赖弃用为未来兼容/生产配置提示。
- `npm audit` 为 0 vulnerabilities。

## Requirements Traceability

| Requirement | Evidence |
|---|---|
| Scene CRUD、搜索和分类 | Service/API/UI、HTTP E2E |
| Draft/Testing/Published/Disabled/Archived | 状态机、JUnit5、HTTP E2E |
| 测试后发布 | `last_tested_version` 门禁、冲突断言 |
| Model Alias 主备绑定 | 唯一主模型校验、Alias 解析、E2E |
| 参数和 Prompt 引用 | 七表配置、编辑器、导入导出 |
| Permission | ScenePermissionPort、本地/JWT 断言 |
| Workflow | 结构化步骤、编辑器、快照和 Trace |
| 版本与回滚 | 不可变发布快照、回滚生成新 Draft |
| 内置/自定义模板 | 八模板迁移、模板 API/UI/E2E |
| Playground/业务执行 | 统一执行端口、Preview Trace/成本 |
| 分享和迁移 | 可移植 JSON 导入导出、内部字段隔离 |
| 外部服务不阻塞 | 可替换 ports、本地 Preview、JWT claims |
| 编译+单测+E2E | `mvn clean verify` 和 packaged JAR smoke |

## Remaining Risks

| Risk | Current boundary |
|---|---|
| 默认 Preview 不是真实模型输出 | 明确返回 `PREVIEW` / `executed=false`；Gateway 以后替换 adapter |
| Prompt 引用尚不能校验存在性 | 保留 ID/版本并显示 Trace；P4 接入正式 Prompt Runtime |
| Workflow 尚不执行真实编排 | 保存结构化定义和 Trace；未来执行器复用端口 |
| JWT claims 命名可能变化 | 所有映射集中在 `SecurityScenePermissionAdapter` |
| 同一 Draft 并发编辑是最后写入生效 | 当前管理端规模可接受；需要多人协作时增加乐观锁 |
| MySQL 尚无真实实例证据 | 迁移保持 SQLite/MySQL 兼容，部署环境需补集成测试 |

## Rollback and Recovery

- 停止服务并恢复上一版 JAR。
- 恢复升级前 SQLite/MySQL 备份。
- 如仅回退 P3，P1 Provider 和 P2 Model Registry 仍可独立运行。
- 恢复后检查 health、Provider/Model 页面及其 E2E 主链路。

## Knowledge Capture

- [x] Tests
- [x] Documentation
- [ ] Architecture decision record
- [x] Schema constraint
- [ ] Static analysis rule
- [x] Reusable component
- [x] AGENTS.md rule
- [ ] Another Skill
