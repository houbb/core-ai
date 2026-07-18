# Core AI

企业级 AI 运行时平台 —— 一站式管理 AI 供应商、模型、Prompt、知识库和智能体。

## 能做什么？

### 🤖 多供应商统一接入

支持 OpenAI、Anthropic、Gemini、Azure OpenAI、Ollama、LM Studio 等主流 AI 平台，一套 API 调度所有模型，告别多套 SDK 的维护噩梦。

### 📋 模型全生命周期管理

自动发现、能力标注、参数预设、价格追踪、版本对比、推荐打分。从录入到下线的全流程管起来，再也不用翻文档找模型参数。

### 🎬 场景编排与发布

将模型+Prompt+参数打包为"场景"，一键发布、版本管理、安全回滚。内置 8 个业务模板，也支持 JSON 导入导出和自定义模板。

### ✍️ Prompt 工程工作台

类 IDE 编辑器，支持变量、Chain、Guardrail、JSON Schema 校验。实时渲染预览，内置测试用例、A/B 实验和发布门禁，靠谱的 Prompt 才能上线。

### 🛠️ 工具插件市场

HTTP、MCP、Mock 等多种工具类型，版本管理、权限策略、执行审批。危险操作必须确认，安全可追溯。

### 🚪 统一网关

路由、缓存、限流、重试、Fallback，所有 AI 请求经网关统一调度。Trace 链路追踪，问题一目了然。

### 💬 对话与记忆

会话管理、消息修订、上下文快照、长期记忆。对话可分享、可导出、可重放，上下文不丢失。

### 📚 知识库

文档上传、智能切分、混合检索（关键词+向量），检索结果带引用溯源。支持权限前置过滤，敏感内容自动拦截。

### 🤖 智能体

Agent Profile + Planner + 工具/知识/记忆无缝编排。支持审批流程和定时任务，构建能干活、不瞎干的 AI 员工。

### 📊 分析看板

用量、成本、延迟、成功率、用户反馈，一眼掌握。预算预警和异常告警，成本不失控。

---

## 快速开始

需要 **Java 21** 和 **Maven 3.6+**。

### 1. 启动后端

```bash
cd core-ai-backend
mvn spring-boot:run
```

### 2. 启动前端

```bash
cd core-ai-frontend
npm ci
npm run dev
```

### 3. 打开页面

| 地址 | 说明 |
|---|---|
| `http://127.0.0.1:8104` | 管理控制台 |
| `http://127.0.0.1:8104/swagger-ui` | API 文档 |
| `http://127.0.0.1:8104/actuator/health` | 健康检查 |

启动后自动创建 SQLite 数据库 `core-ai-backend/data/core-ai.db`，无需额外配置。

---

## 环境要求

| 组件 | 版本 |
|---|---|
| Java | 21 |
| Maven | 3.6+ |
| Node.js | 22+（前端开发） |

---

## 项目结构

```
core-ai/
├── core-ai-backend/     # Spring Boot 后端
├── core-ai-frontend/    # Vue 3 前端
├── README.md
├── CHANGELOG.md
└── LICENSE
```

前后端独立构建、独立部署，互不耦合。

---

## 生产部署

### 后端

```bash
cd core-ai-backend
mvn clean package -DskipTests
java -jar target/core-ai-backend-*.jar
```

### 切换到 MySQL

```bash
java -jar target/core-ai-backend-*.jar --spring.profiles.active=mysql,local
```

环境变量：

```text
CORE_AI_MYSQL_URL=jdbc:mysql://localhost:3306/core_ai
CORE_AI_MYSQL_USERNAME=root
CORE_AI_MYSQL_PASSWORD=your_password
```

### 接入 OAuth2 / JWT

```bash
java -jar target/core-ai-backend-*.jar --spring.profiles.active=mysql,jwt
```

配置环境变量：

```text
CORE_AI_MASTER_KEY=<Base64 编码的 32 字节 AES 密钥>
CORE_IDENTITY_JWKS_URI=https://identity.example.com/.well-known/jwks.json
```

---

## 数据安全

- API Key 和敏感配置使用 **AES-256-GCM** 加密存储
- 界面和 API 仅返回掩码，不暴露明文密钥
- 所有写操作、连接测试、启停变更记录完整审计日志
- Provider 请求限制超时和响应大小，防止 SSRF

---

## 构建验证

```bash
# 后端：编译 + 单元测试 + 端到端测试
cd core-ai-backend && mvn clean verify

# 前端：依赖安装 + 单元测试 + 生产构建
cd core-ai-frontend && npm ci && npm run test:unit -- --run && npm run build
```

---

## 数据备份

```bash
# Linux / macOS
./core-ai-backend/scripts/backup.sh

# Windows
core-ai-backend\scripts\backup.bat
```

恢复：停服 → 将备份文件覆盖 `core-ai-backend/data/core-ai.db` → 启动 → 检查健康端点。

---

## License

MIT
