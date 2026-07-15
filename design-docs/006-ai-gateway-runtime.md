这里我建议继续调整一下整体路线。

很多 AI 平台做到 Tool Runtime 就结束了，但真正到了生产环境，**最核心的问题变成了：如何稳定地调用模型**。

企业真正需要的是：

> **AI Gateway（AI 网关）**

它类似于：

* API Gateway（Spring Cloud Gateway）
* Service Mesh（Istio）
* Database Proxy（MyCat）

所有 AI 请求都必须经过 Gateway。

所以 **P5 不应该直接做 Conversation，而应该做 AI Gateway Runtime。**

这是整个 `core-ai` 最核心的基础设施之一。

---

# Phase 5：AI Gateway Runtime ⭐⭐⭐⭐⭐

> One Gateway for Every AI Request

## 定位

AI Gateway Runtime 是所有 AI 请求的统一入口。

任何请求：

```text
Chat
OCR
RAG
Workflow
Agent
Tool Calling
Embedding
Image
Speech
```

都必须经过：

```text
AI Gateway
```

Gateway 决定：

* 用哪个 Provider
* 用哪个 Model
* 是否缓存
* 是否限流
* 是否重试
* 是否降级
* 是否熔断
* 是否记录日志
* 是否统计成本

业务永远不要直接调用 Provider。

---

# 一、设计目标

一句话：

> **让业务永远不知道底层到底用了哪个 AI。**

业务：

```java
AI.chat(scene="chat")
```

↓

Gateway

↓

选择：

```text
GPT5

↓

Claude

↓

DeepSeek

↓

Qwen(Local)
```

全部自动。

---

# 二、整体架构

```text
Business

↓

Scene Runtime

↓

Prompt Runtime

↓

Tool Runtime

↓

AI Gateway

↓

Router

↓

Provider

↓

Response
```

Gateway：

统一：

调度。

---

# 三、Gateway 生命周期

```text
Draft

↓

Testing

↓

Published

↓

Disabled
```

通常：

Gateway：

只有：

一个。

但是：

支持：

多个：

Cluster。

---

# 四、Gateway 管理界面

建议：

```text
┌─────────────────────────────────────────────┐
│ AI Gateway                                 │
├──────────────┬──────────────────────────────┤
│ Router       │                              │
│ Policy       │                              │
│ Rate Limit   │      Gateway Detail          │
│ Retry        │                              │
│ Cache        │                              │
│ Circuit      │                              │
└──────────────┴──────────────────────────────┘
```

---

# 五、Router（核心）

这是：

Gateway：

最重要。

例如：

```text
Scene

↓

Model Alias

↓

Provider

↓

Endpoint
```

例如：

```text
Chat

↓

chat-default

↓

GPT5

↓

OpenAI
```

以后：

改：

Provider。

业务：

不用：

改。

---

# 六、Routing Policy

建议：

支持：

### 固定路由

```text
Chat

↓

GPT5
```

---

### 权重路由

```text
GPT5

70%

Claude

20%

DeepSeek

10%
```

以后：

灰度。

---

### 成本优先

例如：

```text
Cheap
```

自动：

选：

DeepSeek。

---

### 速度优先

自动：

Gemini Flash。

---

### 质量优先

自动：

GPT5。

---

### 本地优先

例如：

```text
Qwen(Local)

↓

GPT5
```

适合：

企业。

---

# 七、Fallback

建议：

支持：

```text
GPT5

↓

Claude

↓

DeepSeek

↓

Qwen(Local)
```

例如：

429

↓

自动：

切。

Timeout。

↓

自动：

切。

无需：

业务：

重试。

---

# 八、Retry

支持：

```text
Retry

1

2

3
```

策略：

```text
Linear

Exponential

Random
```

建议：

默认：

指数退避。

---

# 九、Circuit Breaker

建议：

内置。

例如：

```text
OpenAI

连续：

10

次失败

↓

Open
```

30 秒：

恢复。

避免：

雪崩。

---

# 十、Rate Limit

支持：

多个维度。

例如：

```text
User

Department

Project

Provider

Scene

IP
```

支持：

```text
RPM

TPM

Daily
```

全部。

---

# 十一、Cache

建议：

支持：

Prompt Cache。

例如：

```text
Question

↓

Hash

↓

Cache
```

以后：

RAG：

很好用。

支持：

TTL。

支持：

Redis（后续）。

前期：

SQLite：

也行。

---

# 十二、Load Balance

多个：

Provider。

支持：

```text
Round Robin

Weight

Latency

Least Request
```

企业：

非常需要。

---

# 十三、Timeout

支持：

不同：

Scene。

例如：

```text
Chat

30s
```

OCR

```text
120s
```

Embedding

```text
15s
```

全部：

配置。

---

# 十四、Streaming

建议：

Gateway：

统一：

Streaming。

以后：

Provider：

不同。

业务：

不用：

知道。

---

# 十五、Request Pipeline

建议：

每次：

请求：

都是：

Pipeline。

```text
Request

↓

Policy

↓

Rate Limit

↓

Router

↓

Retry

↓

Provider

↓

Cache

↓

Response
```

以后：

很好：

扩展。

---

# 十六、Gateway Trace

建议：

展示：

```text
Scene

↓

Alias

↓

Model

↓

Provider

↓

Latency

↓

Cost

↓

Response
```

以后：

调试：

神器。

---

# 十七、Gateway Dashboard

建议：

首页：

展示：

```text
Today

Requests

Latency

Success

Failure

Cost
```

Provider：

排行。

Scene：

排行。

Model：

排行。

---

# 十八、Gateway Policy

建议：

统一。

例如：

```text
Retry

Timeout

Cache

Fallback

Rate Limit
```

全部：

Policy。

以后：

企业：

配置。

---

# 十九、UX（重点）

## 首页

不要：

Provider。

先：

Dashboard。

例如：

```text
AI Gateway

Today

120,000 Requests

Success

99.98%
```

企业：

最关心。

---

## Router

建议：

可视化。

例如：

```text
Chat

↓

GPT5

↓

OpenAI
```

拖拽。

即可。

---

## Retry

不要：

数字。

建议：

解释。

例如：

```text
Retry

3

失败后自动重试 3 次
```

用户：

秒懂。

---

## 熔断

Provider：

变：

红色。

例如：

```text
OpenAI

Circuit Open
```

很明显。

---

# 二十、数据模型设计

建议拆成 **10 张核心表**。

---

## ai_gateway

Gateway。

```sql
id

name

description

enabled

created_at

updated_at
```

---

## ai_gateway_route

路由。

```sql
id

scene

alias

provider

priority

weight

enabled
```

---

## ai_gateway_policy

策略。

```sql
id

retry

timeout

cache

fallback

rate_limit
```

建议策略拆分为独立配置对象（JSON 或子表），避免后续字段持续膨胀。

---

## ai_gateway_retry

重试。

```sql
id

policy_id

max_retry

strategy

interval
```

---

## ai_gateway_circuit

熔断。

```sql
id

provider

failure_threshold

recover_time
```

---

## ai_gateway_rate_limit

限流。

```sql
id

target

rpm

tpm

daily_limit
```

---

## ai_gateway_cache

缓存。

```sql
id

scene

ttl

strategy
```

---

## ai_gateway_trace

链路。

```sql
id

request_id

scene

provider

model

latency

status

created_at
```

---

## ai_gateway_cluster

集群。

```sql
id

name

endpoint

weight
```

---

## ai_gateway_dashboard

统计。

```sql
date

request_count

success_count

error_count

avg_latency

total_cost
```

更推荐将 Dashboard 作为聚合统计视图，由日志实时或定时汇总生成，而不是作为业务主表维护。

---

# 二十一、需要特别注意的设计

## ① Gateway 是唯一入口

任何 AI 调用：

```text
Chat

OCR

RAG

Agent

Workflow
```

全部：

必须：

经过：

Gateway。

不要：

绕。

---

## ② 路由不要绑定具体 Provider

建议：

始终：

```text
Scene

↓

Alias

↓

Gateway

↓

Provider
```

以后：

切：

模型。

不用：

改：

业务。

---

## ③ Retry 与 Fallback 必须分开

很多系统：

混。

其实：

Retry：

同一个：

Provider。

Fallback：

换：

Provider。

完全：

不同。

---

## ④ Gateway 不负责 Prompt

Prompt：

属于：

Prompt Runtime。

Gateway：

只负责：

执行。

不要：

混。

---

## ⑤ Gateway 必须完全可观测

每一次请求都应能够追踪完整链路：

```text
Request
→ Route
→ Model
→ Provider
→ Retry
→ Fallback
→ Response
→ Cost
```

这样才能真正支撑企业生产环境的问题排查、成本分析和 SLA 管理。

---

# P5 在整个 AI Runtime 中的位置

```text
Provider Runtime
        │
Model Runtime
        │
Scene Runtime
        │
Prompt Runtime
        │
Tool Runtime
        │
────────────────────────────
Gateway Runtime ⭐⭐⭐⭐⭐
────────────────────────────
        │
Conversation Runtime
        │
Knowledge Runtime
        │
Agent Runtime
        │
Enterprise AI Platform
```

**P5 是整个 `core-ai` 的生产运行中枢。**

前面的几个阶段解决的是**如何定义 AI 能力**，而 Gateway Runtime 解决的是**如何稳定、可靠、低成本地运行这些 AI 能力**。它也是后续 Cost Runtime、Conversation Runtime、Analytics Runtime 和 Enterprise AI Platform 的共同基础。
