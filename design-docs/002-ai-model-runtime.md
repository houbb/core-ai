我建议 **P1 不叫 Model Runtime，而叫 AI Model Runtime**。

因为从这里开始，**Provider（连接）和 Model（能力）彻底分离**。

很多 AI 平台把这两层混在一起，导致以后：

* 一个模型不能属于多个 Provider
* 一个 Provider 不能配置多个默认模型
* 模型参数越来越乱
* Gateway 无法切换模型

所以 **Provider = 如何连接，Model = 如何使用**。

---

# Phase 1：AI Model Runtime ⭐⭐⭐⭐⭐

> Unified AI Model Management

## 定位

AI Model Runtime 负责管理**所有可调用模型**。

它不关心 API Key。

不关心 Endpoint。

只关心：

> **这个模型是什么？它能干什么？应该如何调用？**

以后：

* Prompt Runtime
* Agent Runtime
* RAG Runtime
* Workflow Runtime
* Chat Runtime

全部依赖这里。

---

# 一、设计目标

一句话：

> **把几十上百个 AI 模型，管理成统一的资源。**

例如：

```text
GPT-5
Claude Opus
Claude Sonnet
DeepSeek-V3
DeepSeek-R1
Qwen3
Qwen3-Coder
Gemma
Llama
BGE-M3
BAAI Reranker
Whisper
Flux
SDXL
```

全部统一管理。

业务永远不要直接写：

```java
model = "gpt-5"
```

而应该：

```java
AI.chat(...)
```

或者：

```java
scene = "chat"
```

Model Runtime 自动选择。

---

# 二、整体架构

```text
Provider Runtime
       │
       ▼
Model Discovery
       │
       ▼
Model Registry
       │
       ▼
Capability Analysis
       │
       ▼
Scene Binding
       │
       ▼
Business Runtime
```

---

# 三、模型生命周期

建议：

```text
Discovered

↓

Registered

↓

Enabled

↓

Deprecated

↓

Disabled

↓

Deleted(Logical)
```

其中：

**Discovered**

来自：

```text
GET /models
```

自动发现。

管理员确认：

↓

Registered。

---

# 四、模型分类（最重要）

不要按照厂商。

建议：

```text
Chat

Reasoning

Vision

Embedding

Rerank

Image

Video

Audio

Speech

Moderation

OCR
```

以后：

Scene Runtime：

直接：

```text
需要 OCR

↓

过滤

↓

全部 OCR 模型
```

---

## 一个模型可以多个能力

例如：

GPT5

```text
✓ Chat

✓ Vision

✓ Reasoning

✕

Embedding
```

DeepSeek

```text
✓ Chat

✓ Reasoning
```

BGE

```text
Embedding
```

Whisper

```text
Speech
```

---

# 五、Model 管理界面

推荐：

```text
┌─────────────────────────────────────────────┐
│ Models                                      │
├──────────────┬──────────────────────────────┤
│ GPT-5        │                              │
│ GPT-5-mini   │                              │
│ Claude       │        Detail                │
│ DeepSeek     │                              │
│ Qwen         │                              │
│ Whisper      │                              │
│ BGE          │                              │
└──────────────┴──────────────────────────────┘
```

左侧：

模型。

右侧：

详情。

---

# 六、Model Card

例如：

```text
GPT-5

OpenAI

Chat

Vision

Reasoning

Context

256K

Enabled
```

或者：

```text
BGE-M3

Embedding

1024

Enabled
```

一眼知道用途。

---

# 七、模型详情

建议分 Tab。

---

## Basic

```text
Name

Display Name

Provider

Model ID

Description

Status
```

---

## Capability

自动识别。

```text
Chat

✓

Vision

✓

Embedding

✕

Reasoning

✓

Streaming

✓

Function Call

✓

JSON

✓
```

---

## Context

```text
Max Context

Input Token

Output Token

Default Max Token
```

---

## Pricing

支持：

```text
Prompt

Completion

Cache Read

Cache Write
```

单位：

```text
$/1M Token
```

以后：

Cost Runtime：

直接计算。

---

## Parameter

支持默认参数。

例如：

```text
Temperature

0.7

TopP

0.95

FrequencyPenalty

PresencePenalty

Seed

Reasoning Effort
```

这些都是默认值。

业务可以覆盖。

---

# 八、模型别名（Alias）

非常重要。

不要业务写：

```text
gpt-5
```

建议：

```text
chat-default

coding-default

ocr-default

reasoning-default

embedding-default
```

以后：

真正调用：

```java
AI.chat()
```

↓

Alias

↓

GPT5

以后：

GPT6

只改：

Alias。

不用改业务。

---

# 九、Scene Binding

例如：

聊天：

```text
推荐

GPT5

Claude

DeepSeek
```

Embedding：

```text
BGE

Embedding3
```

OCR：

```text
Qwen-VL

GPT Vision
```

Scene Runtime：

直接读取。

---

# 十、模型搜索

支持：

```text
Chat

Vision

Embedding

Claude

OpenAI

Context>100K

Streaming

FunctionCall
```

全部过滤。

---

# 十一、模型比较

推荐支持：

Compare。

例如：

```text
GPT5

VS

Claude
```

比较：

```text
Context

Cost

Reasoning

Vision

Latency

Capability
```

以后非常好用。

---

# 十二、默认模型

建议：

```text
Chat

GPT5

Embedding

BGE

Vision

GPT Vision

OCR

Qwen-VL

Reasoning

DeepSeek-R1

Image

Flux
```

以后：

业务：

不用选模型。

---

# 十三、模型推荐

根据：

```text
能力

成本

速度

上下文
```

推荐。

例如：

```text
便宜

DeepSeek
```

```text
最快

Gemini Flash
```

```text
最好

GPT5
```

以后：

Scene Runtime：

直接：

```text
Recommended
```

---

# 十四、自动同步

点击：

```text
Refresh Models
```

同步：

```text
Provider

↓

Models

↓

Capability

↓

Pricing

↓

Context
```

全部更新。

无需重新配置。

---

# 十五、UX（重点）

## 不要出现 Model ID

例如：

不要：

```text
gpt-5-2026-07-01-preview
```

用户根本不知道。

应该：

```text
GPT-5
```

显示。

后台保存：

真正 ID。

---

## 支持标签

例如：

```text
Production

Coding

OCR

Cheap

Vision

Fast

Chinese

English
```

非常方便。

---

## 推荐模式

模型右上角：

```text
⭐ Recommended
```

减少选择困难。

---

## 支持收藏

例如：

```text
❤️ Favorite
```

以后：

Scene：

优先。

---

# 十六、数据模型设计

建议拆为 **6 张核心表**。

---

## ai_model

统一模型定义。

```sql
id

provider_id

model_id

display_name

category

description

status

enabled

created_at

updated_at
```

---

## ai_model_capability

模型能力。

```sql
model_id

chat

vision

embedding

image

video

audio

speech

rerank

reasoning

tool_call

json_mode

streaming
```

---

## ai_model_parameter

默认参数。

```sql
model_id

temperature

top_p

frequency_penalty

presence_penalty

max_output_tokens

reasoning_effort

seed
```

---

## ai_model_pricing

价格。

```sql
model_id

currency

prompt_price

completion_price

cache_read

cache_write

effective_time
```

支持价格版本，便于厂商调价后保留历史。

---

## ai_model_alias

业务别名。

```sql
id

alias

model_id

scene

priority

enabled
```

例如：

```text
chat-default

GPT5
```

---

## ai_model_tag

标签。

```sql
id

model_id

tag
```

例如：

```text
Cheap

Vision

Coding

Chinese
```

---

# 十七、需要特别注意的设计

### 1. 模型与 Provider 是多对一，但业务永远不要依赖 Provider

业务依赖：

```text
Scene

↓

Alias

↓

Model

↓

Provider
```

而不是：

```text
Provider

↓

Model
```

这样未来 Gateway 才能灵活切换。

---

### 2. 不要把价格写死

模型价格变化非常频繁。

应支持：

* 历史价格
* 生效时间
* 手工覆盖（例如企业代理商价格）

---

### 3. 能力由系统识别，可人工修正

部分 Provider 返回的能力信息不完整，因此：

* 首次自动识别；
* 管理员可手工修正；
* 后续同步时保留人工覆盖项。

---

### 4. 默认参数只是建议值

Temperature、TopP 等属于模型默认配置，不应限制业务调用。调用时可覆盖，但系统应提供经过验证的推荐默认值。

---

### 5. Alias 是长期稳定接口

业务代码、Prompt、Agent、Workflow 应尽量引用 Alias（如 `chat-default`、`embedding-default`），而不是具体模型名称。这样未来模型升级（GPT-5 → GPT-6）无需修改业务代码，只需调整 Alias 指向即可。

---

**P1 的核心目标只有一个：建立一个统一、稳定、可演进的 AI 模型目录（Model Registry），让所有 AI 能力都通过标准化模型资源访问，而不是直接绑定具体厂商或模型名称。**
