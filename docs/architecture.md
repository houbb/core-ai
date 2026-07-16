# Architecture

`core-ai` 是一个独立 Spring Boot 服务和一个 Vue3 应用，最终共同打包为一个 JAR。

后端只使用三层：

```text
api → application ← infrastructure
```

- `api`：REST、校验、Problem Details、静态页面入口。
- `application`：P1-P10 用例、生命周期、发布门禁、审批、检索、编排和端口。
- `infrastructure`：JDBC、协议 HTTP、AES-GCM、JWT/JWKS、Preview 执行适配。

当前核心可替换端口：

- `ProviderRepository`
- `ProviderProbePort`
- `SecretCipherPort`
- `RequestContextPort`
- `ModelDiscoveryPort`
- `ModelRepository`
- `SceneRepository`
- `SceneExecutionPort`
- `ScenePermissionPort`
- `PromptRepository`
- `PromptReferencePort`
- `PromptEvaluationPort`
- `PromptPermissionPort`
- `ToolRepository`
- `ToolExecutionPort`
- `GatewayRepository`
- `GatewayInvocationPort`
- `GatewayProviderPort`
- `ConversationRepository`
- `ConversationSummaryPort`
- `KnowledgeRepository`
- `KnowledgeSourcePort`
- `AgentRepository`
- `AgentPlannerPort`
- `AnalyticsRepository`
- `AnalyticsEventPort`

外部 Identity、Vault 或其他 Core 不参与默认运行路径。

P1 与 P2 的边界：

```text
Provider Probe
    ↓
ai_provider_model_cache（远程缓存）
    ↓ ModelDiscoveryPort
ai_model（稳定模型目录）
```

Provider 测试或刷新不会直接修改人工能力覆盖、人工上下文、价格、Alias 或标签。

P2 与 P3 的边界：

```text
Scene Code（业务稳定接口）
    ↓
Scene Model Binding
    ↓ Alias
Model Registry
    ↓
Provider
```

P3 与 P4 的边界：

```text
Scene Prompt Binding（ID/code + optional version）
    ↓ PromptReferencePort
Published Prompt Version
    ↓ Renderer / Variable / Guardrail / Chain
Rendered Prompt
    ↓ SceneExecutionPort
Gateway
```

Scene Draft 可以暂存 Prompt 引用；进入 Testing、Published 和执行时必须解析到真实
Published Prompt。Prompt Runtime 不调用模型，默认测试只返回 `PREVIEW`。外部 Identity
不参与默认路径，Prompt 权限集中读取当前本地/JWT 安全上下文。

P5-P10 主链：

```text
Scene / Conversation / Agent
              ↓
      GatewayInvocationPort
              ↓
Route → Rate Limit → Cache → Retry/Fallback → ProviderPort
              ↓
       Trace + AnalyticsEventPort

Agent Planner
  ├─ Tool Runtime → Confirm / Approval → Safe Adapter
  ├─ Knowledge Runtime → Permission Filter → Hybrid Retrieve → Citation
  ├─ Memory Runtime → Context Snapshot / Long-term Memory
  └─ Gateway Runtime
```

默认外部适配器均可空缺：核心会返回明确 `PREVIEW`，而不会伪造外部成功或阻塞
Tool、Conversation、Knowledge、Agent 的本地能力。Analytics 写入同样是 fail-open。
