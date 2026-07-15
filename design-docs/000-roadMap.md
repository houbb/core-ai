我认为 **`core-ai` 不应该只是一个 AI SDK**。

它应该定位为：

> **Enterprise AI Runtime（企业 AI 运行时）**
>
> 整个平台所有 AI 能力的统一入口。

以后所有平台：

* core-notification（AI 写邮件）
* core-storage（AI OCR）
* core-user（AI 风控）
* Knowledge（AI RAG）
* Scale（AI 投资分析）
* Yggdrasil（AI 哲学）
* Symphony（AI 管理）

全部只依赖

```
core-ai
```

而不是依赖 OpenAI、Ollama、Claude 等 SDK。

---

# core-ai

> Enterprise AI Runtime

```
core-ai
├── Provider Runtime
├── Model Runtime
├── Prompt Runtime
├── Conversation Runtime
├── Tool Runtime
├── Embedding Runtime
├── RAG Runtime
├── Cost Runtime
├── Gateway Runtime
└── Enterprise AI Platform
```

推荐按照下面的路线推进。

---

# Phase 0：Provider Runtime ⭐⭐⭐⭐⭐

整个 AI 平台最重要的一层。

目标：

**统一所有 AI Provider。**

例如：

```
OpenAI

Azure OpenAI

Anthropic

Google Gemini

DeepSeek

Qwen

智谱

Moonshot

MiniMax

SiliconFlow

OpenRouter

Ollama

LM Studio

vLLM

Xinference

FastChat
```

全部统一成

```
AIProvider
```

接口。

例如

```
Chat()

Embedding()

Image()

Audio()

Speech()

Moderation()

Rerank()

Vision()

Reasoning()
```

统一。

---

## Provider

```
id

name

type

endpoint

apiKey

organization

proxy

timeout

status

priority

weight

enabled
```

例如

```
OpenAI

Claude

Ollama(Local)

Gemini

DeepSeek
```

---

## UI

左侧

```
Providers
```

右侧

```
OpenAI

Endpoint

API Key

Model

Test Connection

Health

Latency

Balance

Supported Capability
```

一键测试。

---

## UX

连接成功

绿色

```
Connected
```

失败

```
401

invalid key
```

直接展示。

不要让用户看日志。

---

# Phase 1：Model Runtime ⭐⭐⭐⭐⭐

Provider

下面有很多模型。

例如

OpenAI

```
GPT-4.1

GPT-5

o4-mini
```

Ollama

```
Qwen3

Llama3

DeepSeek-R1
```

---

模型能力

```
Chat

Vision

Embedding

Image

Reasoning

Audio
```

全部自动识别。

---

模型配置

```
Temperature

TopP

MaxTokens

Context

JSON Mode

Function Call

Reasoning Effort

Streaming
```

---

支持默认模型

例如

```
Default Chat

Default Embedding

Default Vision

Default OCR

Default Image

Default Audio
```

以后业务不用指定模型。

直接

```
AI.chat()
```

即可。

---

# Phase 2：Scene Runtime（场景运行时）⭐⭐⭐⭐⭐

这是体验最好的地方。

不要让用户面对几十个模型。

而是：

```
我要：

聊天

翻译

总结

代码生成

SQL

OCR

邮件写作

客服

知识问答

RAG

Agent

图片生成

视频生成

语音识别
```

用户选择

```
聊天
```

自动推荐

```
GPT5

Claude

Qwen

DeepSeek
```

以及推荐参数。

例如

```
Temperature

TopP

Prompt

MaxToken
```

全部自动配置。

---

例如

```
代码生成

推荐

Claude

GPT5

Qwen-Coder
```

用户点

```
使用
```

结束。

---

以后甚至可以：

```
最佳组合

Chat

↓

Claude

Embedding

↓

BGE

OCR

↓

Qwen-VL

Image

↓

Flux
```

全部自动生成。

---

## 场景模板

例如

```
AI Chat

AI Translate

AI Email

AI SQL

AI OCR

AI Coding

AI Review

AI Summarize

AI Agent

AI Meeting

AI Report
```

一键启用。

---

# Phase 3：Prompt Runtime ⭐⭐⭐⭐⭐

统一 Prompt。

例如

```
Prompt

Variables

Version

History

Preview

Debug
```

Prompt

支持：

```
System

User

Assistant
```

模板

```
{{name}}

{{date}}

{{knowledge}}

{{context}}
```

支持：

Prompt Version

Prompt Rollback

Prompt Test

Prompt Compare

---

UI

左侧

Prompt

右侧

实时预览。

---

# Phase 4：Conversation Runtime ⭐⭐⭐⭐⭐

聊天统一管理。

支持

```
Conversation

Session

Message

Memory

History
```

以后：

所有产品

聊天记录全部统一。

---

支持

```
Share

Export

Markdown

Search

Favorite
```

---

# Phase 5：Gateway Runtime ⭐⭐⭐⭐⭐

这是生产环境非常重要的一层。

所有请求：

```
↓

Gateway

↓

Provider
```

支持

```
Retry

Circuit Breaker

Fallback

LoadBalance

Timeout

RateLimit

Cache

Queue
```

例如

GPT 挂了。

自动

↓

Claude。

---

支持：

```
按成本

按速度

按质量

自动切换。
```

---

# Phase 6：Cost Runtime ⭐⭐⭐⭐⭐

企业最关注。

统计

```
Token

Prompt Token

Completion Token

Latency

Error

Provider

Model

User

Department

Project

```

成本

```
￥

$

Token

RPM

TPM
```

全部统计。

---

Dashboard

```
今天

昨天

本月

累计
```

TOP

```
模型

部门

用户

接口
```

全部可视化。

---

支持预算

例如

```
部门预算

1000/月
```

超过

报警。

---

# Phase 7：Log & Audit Runtime ⭐⭐⭐⭐☆

企业必须。

日志：

```
Request

Response

Latency

Provider

Model

Prompt

Tool

User

IP

```

支持

```
Replay

Search

Compare
```

错误：

```
429

500

Timeout

Safety

全部统计。
```

可一键重放请求（脱敏后）。

---

# Phase 8：Knowledge Runtime（RAG）⭐⭐⭐⭐⭐

以后 AI 都离不开知识库。

统一：

```
Document

Chunk

Embedding

Retriever

Vector

Rerank

KnowledgeBase
```

支持

```
PDF

Word

Excel

Markdown

网页

图片 OCR

代码仓库
```

索引统一。

业务只需：

```
AI.ask(
    knowledge="ProductDocs"
)
```

即可。

---

# Phase 9：Enterprise AI Platform ⭐⭐⭐⭐⭐

最终形成完整企业 AI 平台。

```
                        +----------------------+
                        |      AI Portal       |
                        +----------+-----------+
                                   |
        +--------------------------+---------------------------+
        |                          |                           |
+-------v-------+          +-------v-------+          +--------v--------+
| Provider      |          | Scene         |          | Prompt          |
| OpenAI/Ollama |          | Chat/OCR/RAG |          | Version/Template|
+-------+-------+          +-------+-------+          +--------+--------+
        |                          |                           |
+-------v------------------------------------------------------v--------+
|                    Gateway Runtime                                   |
| Retry | Fallback | Load Balance | Rate Limit | Cache | Routing       |
+-------+------------------------------------------------------+--------+
        |                                                      |
+-------v-------+                                      +-------v--------+
| Model Runtime |                                      | Conversation   |
| Chat/Vision   |                                      | Session/Memory |
+-------+-------+                                      +-------+--------+
        |                                                      |
+-------v------------------------------------------------------v--------+
|               Cost / Log / Audit / Analytics                       |
+-------+------------------------------------------------------+--------+
        |                                                      |
+-------v-------+                                      +-------v--------+
| Knowledge     |                                      | Tool Runtime   |
| RAG/Embedding |                                      | MCP/Function   |
+---------------+                                      +----------------+
```

## 核心设计原则

整个 `core-ai` 应遵循几个原则：

* **Provider 与 Model 解耦**：新增模型不影响业务代码，只需注册即可。
* **场景优先于模型**：业务配置应以“聊天、翻译、OCR”等场景为中心，而不是要求用户理解不同模型的能力差异。
* **统一 Gateway**：所有调用都经过网关，实现熔断、限流、降级、重试、路由和观测。
* **可观测性内置**：调用日志、Token 消耗、响应时间、错误率、成本统计、预算控制全部内置，而不是依赖外部系统。
* **默认最佳实践**：提供开箱即用的场景模板和推荐模型组合，新用户无需深入了解 AI 模型即可快速完成配置，同时保留高级参数供专业用户调优。

这套架构既能满足单机部署（如 Ollama、LM Studio），也能平滑扩展到企业级混合部署（本地模型 + 云端模型），成为整个平台所有 AI 能力的统一基础设施。
