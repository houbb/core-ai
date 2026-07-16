# core-ai

Independent Enterprise AI Runtime for Core Platform.

当前已实现阶段：

- P1 AI Provider Runtime
- P2 AI Model Runtime
- P3 AI Scene Runtime
- P4 AI Prompt Runtime
- P5 AI Tool Runtime
- P6 AI Gateway Runtime
- P7 AI Conversation & Memory Runtime
- P8 AI Knowledge Runtime
- P9 AI Agent Runtime
- P10 AI Analysis Runtime

## Responsibilities

- AI Provider 管理
- 加密凭据
- Provider 连接测试
- 远程模型目录同步
- Provider 能力识别
- 健康状态、标签、排序和审计
- Provider 管理界面
- 统一 Model Registry
- 模型能力、上下文、默认参数和价格历史
- 模型生命周期、标签、收藏、推荐和比较
- 稳定 Alias 和默认模型解析
- Scene Catalog、模板、发布、版本和回滚
- Scene Playground、Preview Trace、权限和 JSON 分享
- Prompt IDE、强类型变量、实时渲染和 Playground
- Prompt 不可变版本、测试发布门禁、比较和回滚
- Prompt Guardrail、JSON Schema、Chain、权限和 A/B 实验
- Tool 版本、Schema、测试门禁、Marketplace、确认/审批和安全执行
- Gateway 路由、缓存、限流、重试、Fallback、Trace 和 Scene 统一入口
- Conversation、不可变消息、上下文快照、长期 Memory、分享与重放
- Knowledge Source、文档切分、索引、权限前置过滤、混合检索和引用
- Agent Profile、Planner、Task、Tool/Knowledge/Memory 编排、审批和 Cron
- Usage、Cost、Metric、Trace、Evaluation、Feedback、Budget、Alert 和洞察

## Non-responsibilities

- 未配置适配器的真实外部 Provider/MCP/向量库/LLM Planner 执行
- 用户登录和身份签发
- 外部 Secret/Vault 服务

## Requirements

- Java 21
- Maven 3.6+
- Node.js 22（Maven 完整构建可自动安装项目内 Node）

## Quick start

默认使用 SQLite 和仅监听本机的 local 模式：

```bash
mvn spring-boot:run
```

打开：

- 管理页面：`http://127.0.0.1:8104/analytics`
- OpenAPI：`http://127.0.0.1:8104/swagger-ui`
- 健康检查：`http://127.0.0.1:8104/actuator/health`

默认数据库：

```text
./data/core-ai.db
```

## Production security

生产环境使用 `jwt` Profile，并接入 `core-identity` 或兼容 JWKS：

```bash
java -jar core-ai.jar \
  --spring.profiles.active=sqlite,jwt
```

必须提供：

```text
CORE_AI_MASTER_KEY=<32-byte AES key encoded as Base64>
CORE_IDENTITY_JWKS_URI=https://identity.example.com/.well-known/jwks.json
```

权限：

- `ai.provider.read`
- `ai.provider.manage`
- `ai.model.read`
- `ai.model.manage`
- `ai.scene.read`
- `ai.scene.manage`
- `ai.scene.execute`
- `ai.prompt.read`
- `ai.prompt.manage`
- `ai.prompt.render`
- `ai.tool.read` / `ai.tool.manage` / `ai.tool.execute` / `ai.tool.approve`
- `ai.gateway.read` / `ai.gateway.manage` / `ai.gateway.invoke`
- `ai.conversation.read` / `ai.conversation.manage`
- `ai.memory.read` / `ai.memory.manage`
- `ai.knowledge.read` / `ai.knowledge.manage` / `ai.knowledge.search`
- `ai.agent.read` / `ai.agent.manage` / `ai.agent.execute` / `ai.agent.approve`
- `ai.analytics.read` / `ai.analytics.manage`

外部身份或 Secret 服务不会阻塞默认单机能力；相关能力通过配置和端口预留。

## Provider API

```text
GET    /api/v1/ai/admin/providers
POST   /api/v1/ai/admin/providers
GET    /api/v1/ai/admin/providers/{id}
PUT    /api/v1/ai/admin/providers/{id}
DELETE /api/v1/ai/admin/providers/{id}
PATCH  /api/v1/ai/admin/providers/{id}/enabled
POST   /api/v1/ai/admin/providers/{id}/test
POST   /api/v1/ai/admin/providers/{id}/models/refresh
GET    /api/v1/ai/admin/providers/{id}/models
GET    /api/v1/ai/admin/providers/{id}/audit
GET    /api/v1/ai/admin/providers/presets
```

Model Runtime：

```text
GET    /api/v1/ai/admin/models
POST   /api/v1/ai/admin/models/sync
POST   /api/v1/ai/admin/models/compare
GET    /api/v1/ai/admin/models/recommend
GET    /api/v1/ai/admin/models/defaults
GET    /api/v1/ai/admin/models/{id}
PUT    /api/v1/ai/admin/models/{id}
PATCH  /api/v1/ai/admin/models/{id}/status
PUT    /api/v1/ai/admin/models/{id}/capabilities
POST   /api/v1/ai/admin/models/{id}/capabilities/reset
PUT    /api/v1/ai/admin/models/{id}/parameters
POST   /api/v1/ai/admin/models/{id}/pricing
PATCH  /api/v1/ai/admin/models/{id}/flags
DELETE /api/v1/ai/admin/models/{id}

GET    /api/v1/ai/admin/model-aliases
POST   /api/v1/ai/admin/model-aliases
PUT    /api/v1/ai/admin/model-aliases/{id}
DELETE /api/v1/ai/admin/model-aliases/{id}
GET    /api/v1/ai/admin/model-aliases/{alias}/resolve
```

Scene Runtime：

```text
GET    /api/v1/ai/admin/scenes
POST   /api/v1/ai/admin/scenes
POST   /api/v1/ai/admin/scenes/import
GET    /api/v1/ai/admin/scenes/{id}
PUT    /api/v1/ai/admin/scenes/{id}
PATCH  /api/v1/ai/admin/scenes/{id}/status
POST   /api/v1/ai/admin/scenes/{id}/test
GET    /api/v1/ai/admin/scenes/{id}/versions
POST   /api/v1/ai/admin/scenes/{id}/versions/{version}/rollback
GET    /api/v1/ai/admin/scenes/{id}/export
POST   /api/v1/ai/admin/scenes/{id}/templates
GET    /api/v1/ai/admin/scenes/{id}/audit

GET    /api/v1/ai/admin/scene-templates
POST   /api/v1/ai/admin/scene-templates/{id}/instantiate
DELETE /api/v1/ai/admin/scene-templates/{id}

GET    /api/v1/ai/scenes
GET    /api/v1/ai/scenes/{code}
POST   /api/v1/ai/scenes/{code}/execute
```

默认执行适配器返回明确标记的 `PREVIEW`，用于验证 Scene、Alias、Prompt 引用、
成本估算和 Trace。真实推理由后续 Gateway 实现 `SceneExecutionPort`。

Prompt Runtime：

```text
GET    /api/v1/ai/admin/prompts
POST   /api/v1/ai/admin/prompts
GET    /api/v1/ai/admin/prompts/{id}
PUT    /api/v1/ai/admin/prompts/{id}
PATCH  /api/v1/ai/admin/prompts/{id}/status
GET    /api/v1/ai/admin/prompts/{id}/versions
POST   /api/v1/ai/admin/prompts/{id}/versions/{version}/rollback
GET    /api/v1/ai/admin/prompts/{id}/compare
POST   /api/v1/ai/admin/prompts/{id}/render
POST   /api/v1/ai/admin/prompts/{id}/validate-output
GET    /api/v1/ai/admin/prompts/{id}/test-cases
POST   /api/v1/ai/admin/prompts/{id}/test-cases
POST   /api/v1/ai/admin/prompts/{id}/tests/run
GET    /api/v1/ai/admin/prompts/{id}/ab-tests
POST   /api/v1/ai/admin/prompts/{id}/ab-tests
GET    /api/v1/ai/admin/prompts/{id}/audit
GET    /api/v1/ai/admin/prompts/{id}/render-logs

GET    /api/v1/ai/prompts
POST   /api/v1/ai/prompts/{code}/render
POST   /api/v1/ai/prompts/{code}/validate-output
```

Prompt 测试默认使用明确标记的 `PREVIEW` evaluator，只验证变量、渲染、
Guardrail、Schema 和 Chain，不伪造模型质量。真实评估可替换 `PromptEvaluationPort`。

## P5-P10 runtime API

```text
/api/v1/ai/admin/tools              /api/v1/ai/tools
/api/v1/ai/admin/gateways           /api/v1/ai/gateway
/api/v1/ai/conversations            /api/v1/ai/memories
/api/v1/ai/admin/knowledge          /api/v1/ai/knowledge
/api/v1/ai/admin/agents             /api/v1/ai/agents
/api/v1/ai/admin/analytics
```

默认适配器的行为：

- `MOCK` Tool 在本地真实执行；HTTP/MCP/Shell/SQL/Python 等外部或危险执行返回 `PREVIEW`。
- Gateway 是 Scene、Conversation 和 Agent 的唯一 AI 请求入口。
- Knowledge 使用本地切分、哈希向量引用和确定性 Keyword/Hybrid 检索。
- Agent 默认使用确定性 Planner；危险 Tool 操作进入持久化审批状态。
- Analytics 采集失败只告警，不阻塞核心业务链。

## Database

默认 SQLite，可切换 MySQL：

```bash
java -jar core-ai.jar --spring.profiles.active=mysql,local
```

配置：

```text
CORE_AI_MYSQL_URL
CORE_AI_MYSQL_USERNAME
CORE_AI_MYSQL_PASSWORD
```

Flyway 负责迁移。所有 P1-P10 表均包含：

```text
id
create_time
update_time
create_user
update_user
```

数据库不使用外键。

## Build and verification

完整阶段门禁：

```bash
mvn clean verify
```

该命令会执行：

- Java 21 编译
- JUnit5 单元断言测试
- Provider HTTP 端到端测试
- Model/Scene/Prompt/Tool/Gateway/Memory/Knowledge/Agent/Analysis HTTP 端到端测试
- Vue/Vitest 单元测试
- TypeScript 类型检查
- Vue 生产构建
- 前端资源写入最终 Spring Boot JAR

## Backup

Windows：

```bat
scripts\backup.bat
```

Linux/macOS：

```bash
./scripts/backup.sh
```

恢复时停止服务，将备份文件复制回 `data/core-ai.db`，再启动并检查健康端点。

## Compatibility

- Java: 21
- Spring Boot: 3.5.x
- SQLite/MySQL: 通过独立 Profile 切换
- API: `/api/v1/ai`

## Security

- API Key 和自定义 Header/参数配置使用 AES-256-GCM 加密。
- API 与 UI 只返回掩码，不返回明文密钥。
- 写操作、连接测试、模型刷新和启停删除全部记录审计。
- Provider Endpoint 仅允许 HTTP(S)，并限制超时和响应大小。
- `tlsVerify=false` 仅用于明确需要自签名证书的受控环境。
