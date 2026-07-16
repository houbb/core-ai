# Security Policy

## Reporting

请勿在公开 Issue 中提交 API Key、Provider Header、数据库文件或可复现的真实凭据。

安全问题应包含：

- 受影响版本
- 影响范围
- 最小复现步骤
- 已完成的脱敏信息

## Security model

- Provider API Key、自定义 Header 和 Custom Parameters 使用 AES-256-GCM 加密。
- local 模式仅监听 `127.0.0.1`。
- 生产使用 JWT/JWKS，并区分 `ai.provider.read` 与 `ai.provider.manage`。
- Model Runtime 使用 `ai.model.read` 与 `ai.model.manage`。
- Scene 管理使用 `ai.scene.read` 与 `ai.scene.manage`，业务执行使用 `ai.scene.execute`。
- Prompt 管理使用 `ai.prompt.read` 与 `ai.prompt.manage`，业务渲染使用 `ai.prompt.render`。
- Tool 区分 read/manage/execute/approve，危险执行必须显式确认或审批。
- Gateway 区分 read/manage/invoke，并为所有 AI 请求写入 Trace。
- Conversation/Memory、Knowledge、Agent、Analytics 使用各自最小权限 Scope。
- Scene 权限只读取本地 JWT roles/department/groups claims，不同步调用外部 Identity。
- Scene 导入导出不包含 Provider Secret、Model 凭据或 Prompt 内容。
- 默认 Scene 执行为 `PREVIEW`，不会向未配置的外部 Gateway 发送输入。
- Prompt Public/Project/Department/Private 权限只读取当前用户和 JWT claims，不调用外部目录。
- Prompt Render Log 默认不保存变量值或渲染正文；仅显式设置
  `CORE_AI_PROMPT_LOG_CONTENT=true` 时保存调试内容。
- Prompt 审计不保存变量输入、测试输入或完整 Prompt 正文。
- Endpoint 只接受 HTTP(S)，请求具备超时和响应大小限制。
- 审计日志不得包含明文 Secret。
- Tool Execution Log 递归脱敏敏感字段；外部危险执行默认不启用。
- Knowledge 权限在检索评分前执行，避免未授权 Chunk 进入候选集。
- Context Snapshot 默认只保存脱敏摘要和哈希。
- 外部 Provider、Source、Planner、Analytics 失败不应扩大为核心能力不可用。

生产部署必须替换 local 开发主密钥。
