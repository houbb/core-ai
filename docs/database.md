# Database

P1 表：

- `ai_provider`
- `ai_provider_secret`
- `ai_provider_capability`
- `ai_provider_health`
- `ai_provider_model_cache`
- `ai_provider_tag`
- `ai_audit_log`

P2 表：

- `ai_model`
- `ai_model_capability`
- `ai_model_parameter`
- `ai_model_pricing`
- `ai_model_alias`
- `ai_model_tag`

P3 表：

- `ai_scene`
- `ai_scene_model`
- `ai_scene_parameter`
- `ai_scene_prompt`
- `ai_scene_permission`
- `ai_scene_version`
- `ai_scene_template`

P4 表：

- `ai_prompt`
- `ai_prompt_version`
- `ai_prompt_variable`
- `ai_prompt_output_schema`
- `ai_prompt_testcase`
- `ai_prompt_abtest`
- `ai_prompt_guardrail`
- `ai_prompt_render_log`

P5-P10 表组：

- P5：`ai_tool*`（定义、版本、参数、权限、Policy、Binding、Test、Execution、Market）
- P6：`ai_gateway*`（Gateway、Route、Policy、Retry、Circuit、Rate Limit、Cache、Trace、Cluster、Dashboard）
- P7：`ai_conversation`、`ai_session`、`ai_message*`、`ai_memory`、`ai_summary`、`ai_context_snapshot`、Tag/Share/Replay
- P8：`ai_knowledge*`、`ai_document`、`ai_chunk`、`ai_embedding`、`ai_index`、Retriever/Reranker/Search/Reference
- P9：`ai_agent*`（Profile、Planner、Task、资源绑定、Execution、Trace、Schedule、Approval、Version）
- P10：`ai_usage_event`、`ai_cost_record`、`ai_metric`、`ai_trace`、`ai_evaluation`、`ai_feedback`、`ai_alert_rule`、`ai_budget`

`ai_provider_secret.api_key_cipher`、`headers_json` 和 `custom_parameters_json` 保存 AES-GCM 密文。

所有表具备统一审计字段，不使用数据库外键。Provider 删除为软删除，模型缺失时标记为 `INACTIVE`。

Model 删除同样是逻辑删除。Provider 同步只更新自动字段：

- 人工能力覆盖保存在 `manual_override_json`。
- 人工上下文由 `context_manually_overridden` 保护。
- 价格按 `effective_time` 保留历史版本。

Scene 约束：

- Scene 只保存 Model Alias，不保存 Model ID 或 Provider ID。
- Scene Prompt 绑定只保存 `prompt_id` 与 `prompt_version`，不复制 Prompt 内容。
- Published 时把完整配置写入不可变 `ai_scene_version` 快照。
- Rollback 复制历史快照到新的 Draft 版本，不覆盖历史。
- `workflow_json` 为后续 Workflow Runtime 保留可扩展步骤契约。
- V3 内置八个 Scene Template，但不在 Alias 未就绪时伪造 Published Scene。

Prompt 约束：

- `ai_prompt.current_version` 指向正在编辑/测试的版本，`published_version` 指向线上版本。
- Prompt Version 及其变量、Schema、Guardrail 和 Chain 创建后不覆盖。
- 每次编辑、Rollback 都写入新的 `ai_prompt_version`。
- Test Case 属于具体 Prompt Version，版本复制时同时复制并重置测试结果。
- A/B 只引用曾发布的版本，观测字段使用原子累加。
- Render Log 默认 `variables_json` 和 `rendered_prompt` 为 NULL，只保存摘要与 SHA-256。
- 八张表均包含统一审计字段、注释和索引，不使用外键。

P5-P10 约束：

- Tool/Gateway/Agent 的等待与审批状态持久化，不依赖进程内存。
- Message 修订新增版本，不覆盖原消息。
- Knowledge 权限在 Chunk 候选评分前应用；Embedding 表只保存外部或本地向量引用。
- Usage Event 是 Analysis 的事实来源；成本、Trace、预算和告警均可追溯。
- 全部新表包含统一审计字段、注释和合适索引，不使用数据库外键。
