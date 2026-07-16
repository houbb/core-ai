# API

基础路径：

```text
/api/v1/ai/admin/providers
```

成功响应直接返回资源；错误使用 RFC 9457 Problem Details，并附加：

```json
{
  "errorCode": "AI_PROVIDER_NOT_FOUND",
  "traceId": "request-id",
  "path": "/api/v1/ai/admin/providers/id"
}
```

## 密钥更新语义

- 创建时 `apiKey` 写入加密存储。
- 更新时省略或留空 `apiKey` 会保留原密钥。
- 读取时只返回 `apiKeyMasked`。
- Headers 始终返回掩码；更新时省略 `headers` 会保留原值。
- Custom Parameters 中 key/token/secret/password/credential 类字段返回 `****`；更新时 `****` 保留原值。

完整契约通过 `/v3/api-docs` 获取。

## Model 生命周期

```text
DISCOVERED → REGISTERED → ENABLED
ENABLED → DEPRECATED / DISABLED
DEPRECATED / DISABLED → ENABLED
```

Enabled 模型删除前必须先 Disabled。

## Alias

同一 Alias 可以按 `priority` 绑定多个模型。解析时只返回：

- Alias 已启用
- Model 状态为 Enabled
- Model 仍可从 Provider 发现
- Provider 已启用

默认模型使用 `chat-default`、`coding-default`、`embedding-default`、`vision-default`、
`ocr-default`、`reasoning-default`、`image-default`、`video-default` 等标准 Alias。

## Scene 生命周期

```text
DRAFT → TESTING → PUBLISHED → DISABLED → DRAFT（新版本）
                                  └────→ ARCHIVED
DRAFT → ARCHIVED
TESTING → DRAFT
```

- 当前版本必须成功执行 Scene Test，才能 Published。
- Published Scene 不允许直接修改。
- Rollback 会生成新的 Draft 版本，历史发布快照不可变。
- 业务目录 `/api/v1/ai/scenes` 只返回 Published、Enabled 且当前用户有权限的 Scene。

## Scene 执行

默认 `SceneExecutionPort` 返回：

```json
{
  "mode": "PREVIEW",
  "executed": false,
  "modelAlias": "chat-default",
  "trace": []
}
```

这表示配置、Alias、Prompt 引用和 Workflow 已验证，但没有伪造真实 AI 输出。
Gateway 接入后可替换执行端口，不改变 Scene API。

绑定 Prompt 的 Scene 在进入 Testing、Published 和业务执行前，必须解析到已发布的
Prompt 版本。Scene 调用会把 `input` 同时提供为 `input` 和 `content` 变量，并在 Trace
中显示 Prompt code、version 和 Token 估算，不显示 Prompt 正文。

## Prompt 生命周期

```text
DRAFT → TESTING → PUBLISHED → DEPRECATED → ARCHIVED
TESTING → DRAFT
```

- 每次编辑都创建新的 Draft 版本，不覆盖历史。
- 编辑已发布 Prompt 时，旧 `published_version` 继续服务。
- 当前版本必须至少有一个启用测试用例，并且全部通过，才能 Published。
- Published 版本不可变；Rollback 会复制历史版本为新的 Draft。
- Deprecated 会清除 active published pointer，历史版本仍保留。

## Prompt 渲染和测试

- Admin Playground 可以渲染当前 Draft/Testing 版本。
- Runtime 只能渲染 active Published 版本，也可固定到曾发布的历史版本。
- 默认 `PromptEvaluationPort` 返回 `mode=PREVIEW`、`executed=false`。
- Prompt Chain 在本阶段完成引用、无环和逐阶段渲染；真实模型串行执行由 Gateway 接入。
- 输出 Schema 支持 `type`、`required`、`properties`、`items`、`enum` 和
  `additionalProperties`。
- Render Log 默认不保存变量值和 Prompt 正文，只保存变量名、Token、哈希和模式。

## Prompt A/B

- A/B 版本必须都曾发布。
- `trafficRatio` 为 A 的流量百分比，范围 1-99。
- assignment 使用 subject key 稳定哈希，同一实验和 subject 会得到相同版本。
- 统计必须由调用方通过 observation API 真实写入，系统不会生成虚假样本。

## Tool

- `DRAFT → TESTING → PUBLISHED → DEPRECATED/DISABLED → ARCHIVED`
- 每次更新创建新 Draft 版本；发布必须先通过当前版本测试。
- `manualConfirm` 和 `approvalRequired` 会持久化为 `WAITING_CONFIRM` /
  `WAITING_APPROVAL`，由显式接口继续。
- 日志默认递归脱敏 secret/token/password/credential/apiKey/身份证类字段。
- `MOCK` 本地执行；未配置的 HTTP/MCP/DB/Shell/Python 只返回 `PREVIEW`。

## Gateway

- `POST /api/v1/ai/gateway/invoke` 是统一请求入口。
- Pipeline 包含路由、限流、精确缓存、同 Provider 重试、跨 Route Fallback 和 Trace。
- Scene 已通过 `GatewaySceneExecutionAdapter` 接入，不再直连执行适配器。

## Conversation & Memory

- 消息不覆盖，编辑会创建相同 sequence 的新 version，并写入 supersedes id。
- 上下文由最近消息、确定性摘要和 User/Conversation Memory 组成。
- Context Snapshot 默认只保存脱敏摘要和哈希，不保存完整上下文。
- 支持收藏/归档/标签、Session、分享、导出和安全 Preview 重放。

## Knowledge

- 文档经过本地解析、Paragraph/Fixed Chunk、哈希 Embedding 引用和 Hybrid Index。
- Knowledge/Chunk 权限在评分前过滤。
- 检索返回 rank、score、citation，并持久化 Search Log 和 Reference。
- 外部 Source 没有适配器时返回空 Preview，不阻塞本地文档导入。

## Agent

- Agent 包含 Profile、Planner、Task、Tool/Knowledge/Memory 绑定和不可变版本快照。
- 默认 Planner 按 order 确定性执行；Gateway、Tool、Knowledge、Memory 共享真实运行时。
- 危险 Tool 在 Agent Policy 和 Tool Policy 两层进入持久化审批。
- 支持 pause/resume、审批/拒绝、Trace 和 Spring CronExpression 调度。

## Analysis

- Gateway、Tool、Knowledge、Agent 通过 `AnalyticsEventPort` 统一写入 Usage/Trace。
- Dashboard 聚合请求、成功率、延迟、Token、成本、质量、排行、预算和告警。
- Evaluation、Feedback、Budget、Alert 都提供管理 API。
- 洞察为确定性摘要；Analytics 故障不会反向中断核心调用。
