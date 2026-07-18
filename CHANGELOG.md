# Changelog

## 0.1.0 - 2026-07-16

### Added

- 项目结构优化：前后端分离为 `core-ai-backend/` 和 `core-ai-frontend/`
- P1 AI Provider Runtime
- Provider CRUD、生命周期、标签、搜索和排序
- AES-GCM Secret 存储与掩码响应
- OpenAI Compatible、Anthropic、Gemini、Ollama、LM Studio、Azure OpenAI、Custom 探测
- 连接测试、模型缓存、能力识别和健康快照
- 审计日志
- Vue3 中英文管理界面
- SQLite/MySQL Profile、Flyway、OpenAPI、Actuator
- JUnit5、HTTP E2E、Vitest 和单 JAR 构建门禁

### P2 AI Model Runtime

- Provider 发现结果自动同步到独立 Model Registry
- 模型生命周期、能力人工覆盖和上下文保护
- 默认参数、价格历史、标签、收藏和推荐
- Alias、多候选优先级、默认模型和解析接口
- 模型搜索、过滤、比较和可解释推荐
- Models 管理页面和 P2 自动化测试

### P3 AI Scene Runtime

- 七张 Scene 表、八个内置业务模板和 Scene Catalog
- Draft、Testing、Published、Disabled、Archived 生命周期
- 发布前测试门禁、不可变发布快照、新 Draft 版本和安全回滚
- 多 Alias 主备路由、场景参数、Prompt 引用、权限和 Workflow 配置
- Playground、Preview 输出、成本估算和可解释 Trace
- Scene JSON 导入导出、自定义模板和业务执行 API
- Scenes 首页、Vue3 中英文管理界面和 P3 自动化测试

### P4 AI Prompt Runtime

- 八张 Prompt 表、稳定 Code、Draft/Published 双版本指针和不可变版本资产
- System/User/Assistant IDE 编辑器、强类型变量、自动提示和实时渲染预览
- Preview Playground、测试用例、发布门禁、版本比较和安全回滚
- JSON Schema 子集、输入/输出 Guardrail、Prompt Chain 和结构化输出验证
- Public/Project/Department/Private 权限、脱敏 Render Log 和完整审计
- A/B 稳定分桶、真实观测聚合和版本指标
- Scene Testing/Publish/execute 与 Published Prompt 的真实解析和渲染联动
- Prompts 首页、Vue3 中英文管理界面和 P4 自动化测试

### P5-P10 Remaining AI Runtimes

- P5 Tool：版本、参数 Schema、Policy、测试门禁、Marketplace、Chain、脱敏执行日志、确认和审批
- P6 Gateway：统一调用入口、Route、Policy、Retry/Fallback、Rate Limit、Cache、Trace 和 Dashboard
- Scene 执行切换到 Gateway 适配器，外部 Provider 未配置时保持明确 Preview
- P7 Conversation & Memory：Session、不可变消息修订、摘要、上下文快照、长期记忆、分享、导出和重放
- P8 Knowledge：Source、Document、Chunk、Embedding 引用、Index、权限、版本、Hybrid 检索和 Citation
- P9 Agent：Profile、Planner、Task、资源绑定、Execution、Trace、Approval、Pause/Resume 和 Cron
- P10 Analysis：Usage、Cost、Metric、Trace、Evaluation、Feedback、Alert、Budget、Dashboard 和确定性洞察
- 新增 Tools、Gateway、Conversations、Knowledge、Agents、Analytics 中英文控制台
- 新增 P5-P10 JUnit5 单元断言、Vue 组件断言和跨运行时 HTTP E2E
