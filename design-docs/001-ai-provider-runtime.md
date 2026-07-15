我建议把 **P0 不叫 Provider Runtime，而叫 AI Provider Runtime**。

原因是以后还会有：

* AI Model Runtime
* AI Prompt Runtime
* AI Agent Runtime
* AI Gateway Runtime

如果直接叫 Provider，边界容易模糊。

---

# Phase 0：AI Provider Runtime ⭐⭐⭐⭐⭐

> Connect Every AI Model.

**定位：**

整个 `core-ai` 最底层能力。

负责连接所有 AI 服务，无论是：

* 云端 API
* 企业私有模型
* 本地模型
* OpenAI Compatible 服务

所有上层能力（Prompt、Chat、Agent、RAG、Workflow）全部只依赖这一层。

---

# 一、设计目标

一句话：

> **让 AI Provider 像数据库连接一样容易管理。**

例如今天增加一个 DeepSeek：

不是改代码。

而是：

```
新增 Provider

↓

填写地址

↓

填写 Key

↓

测试连接

↓

启用

↓

完成
```

整个系统即可使用。

以后切换 GPT → Claude：

也是配置，不改代码。

---

# 二、支持 Provider 类型

不要按照厂商分类。

而是按照协议。

因为未来模型越来越多。

建议：

```
OpenAI Compatible
```

例如：

```
OpenAI

DeepSeek

Qwen

智谱

Moonshot

SiliconFlow

OpenRouter

Together

Groq

Fireworks

LocalAI

vLLM

LiteLLM
```

其实全部都是：

```
OpenAI Compatible
```

所以 Adapter 可以共用。

---

第二类：

```
Anthropic API
```

Claude。

---

第三类：

```
Google Gemini
```

---

第四类：

```
Ollama
```

本地。

---

第五类：

```
LM Studio
```

本地。

---

第六类：

```
Azure OpenAI
```

---

第七类：

```
Custom Provider
```

以后插件实现。

例如：

```
Company LLM
```

---

最终：

```
Provider Type

OpenAI Compatible

Anthropic

Gemini

Ollama

LM Studio

Azure OpenAI

Custom
```

---

# 三、Provider 生命周期

每个 Provider：

```
Draft

↓

Testing

↓

Available

↓

Disabled

↓

Deleted
```

注意：

删除建议：

```
逻辑删除
```

否则日志无法关联。

---

# 四、Provider 管理界面

推荐采用：

```
┌──────────────────────────────────────────────┐
│ AI Providers                                │
├───────────────┬──────────────────────────────┤
│               │                              │
│ OpenAI        │      Provider Detail         │
│ DeepSeek      │                              │
│ Claude        │                              │
│ Ollama        │                              │
│ Gemini        │                              │
│               │                              │
│ + New         │                              │
│               │                              │
└───────────────┴──────────────────────────────┘
```

这是 VSCode 风格。

非常适合。

---

左边：

Provider List。

右边：

Detail。

---

# 五、Provider Card

例如：

```
OpenAI

🟢 Online

15 Models

120ms

Default
```

或者

```
Ollama

🟡 Local

5 Models

Disconnected
```

支持：

颜色即可知道状态。

---

# 六、新建 Provider

点击：

```
+

New Provider
```

弹出：

第一步：

```
Choose Provider Type
```

例如：

```
○ OpenAI Compatible

○ Anthropic

○ Ollama

○ Gemini

○ Azure

○ Custom
```

最好不要让用户自己填。

---

第二步：

自动带默认配置。

例如：

OpenAI

自动：

```
Endpoint

https://api.openai.com/v1
```

DeepSeek

自动：

```
https://api.deepseek.com
```

Gemini

自动：

Google Endpoint。

---

用户只需要：

```
API Key
```

即可。

---

# 七、配置界面

例如：

```
Basic

Name

Description

Type

Enabled

--------------------

Connection

Endpoint

API Key

Organization

Proxy

Timeout

Retry

--------------------

Advanced

Headers

TLS

Custom Parameters
```

高级默认折叠。

不要吓到用户。

---

# 八、Connection Test（最重要）

一定要做。

按钮：

```
Test Connection
```

执行：

```
Ping

↓

Authentication

↓

Get Models

↓

Latency

↓

Capability
```

最后：

```
✓ Success

Endpoint

OK

API Key

OK

Latency

86ms

Models

32

Vision

Supported

Embedding

Supported
```

失败：

不要：

```
500
```

这种垃圾提示。

应该：

```
Authentication failed

API Key Invalid

请检查 API Key 是否正确。
```

用户一眼就知道。

---

# 九、自动同步模型

连接成功：

自动：

```
GET /models
```

同步。

例如：

```
GPT-5

GPT-5-mini

o4-mini

Embedding-3-large
```

不用用户自己填。

---

支持：

```
Refresh Models
```

以后模型升级：

点击即可。

---

# 十、Provider Health

建议内置。

例如：

```
Availability

99.9%

Latency

85ms

RPM

120

TPM

400K

Last Success

10 sec ago

Last Error

Yesterday
```

以后排查非常方便。

---

# 十一、Provider Capability

自动识别。

例如：

```
Chat

✓

Vision

✓

Embedding

✓

Image

✕

Speech

✕

Audio

✓

Reasoning

✓
```

以后：

Scene Runtime：

直接过滤。

---

# 十二、Provider 排序

建议支持：

```
Priority

1

2

3
```

以后：

Gateway：

自动：

```
Priority

↓

Weight

↓

Fallback
```

---

# 十三、Provider 标签

例如：

```
Cloud

Local

China

US

Production

Testing

Cheap

Fast

Vision
```

方便筛选。

---

# 十四、搜索

支持：

```
Search Provider

OpenAI
```

过滤。

支持：

```
Enabled

Only Cloud

Vision

Embedding
```

---

# 十五、UX（重点）

## 不要暴露 Provider 差异

例如：

不要：

```
Temperature

Reasoning

TopP

Seed

```

放这里。

这是：

Model Runtime。

这里：

只有：

连接。

---

## 新手模式

第一次：

```
欢迎使用 AI。

请选择：

OpenAI

DeepSeek

Ollama

Claude
```

四个大卡片。

即可。

---

## 高级模式

右上角：

```
Advanced
```

展开。

否则：

90% 用户：

根本不用。

---

# 十六、数据模型设计

建议拆成 **5 张表**，避免未来难以扩展。

---

## ai_provider

Provider 基本信息。

```sql
id

code

name

description

provider_type

endpoint

enabled

status

priority

weight

created_at

updated_at
```

---

## ai_provider_secret

敏感配置。

```sql
id

provider_id

api_key

organization

proxy

tls

headers

encrypted

updated_at
```

独立存储。

以后可以接：

```
core-config Secret Runtime
```

甚至：

Vault。

---

## ai_provider_capability

自动同步。

```sql
provider_id

chat

vision

embedding

image

audio

speech

rerank

reasoning
```

---

## ai_provider_health

实时状态。

```sql
provider_id

latency

availability

rpm

tpm

last_success

last_error

last_error_message
```

可以定时刷新，也可以按调用结果异步更新，不建议每次进入页面都主动探测。

---

## ai_provider_model_cache

模型缓存。

```sql
id

provider_id

model_id

display_name

capability

context_length

status

last_sync_at
```

真正的模型详细配置将在 **Phase 1：Model Runtime** 中管理，这里只负责缓存 Provider 返回的模型列表，避免重复请求远程接口。

# 十七、需要特别注意的设计

这是很多 AI 平台容易踩的坑：

1. **Provider 与 Model 必须彻底解耦**。Provider 是连接，Model 是能力，不要把默认模型写进 Provider 配置。
2. **API Key 永远加密存储**，UI 只显示掩码（例如 `sk-****abcd`），提供“重新填写”而不是直接显示明文。
3. **连接测试不要修改业务数据**，测试接口只做认证、获取模型和能力探测。
4. **支持多个同类型 Provider**。例如可以同时配置两个 OpenAI Compatible 服务，一个用于生产，一个用于测试。
5. **所有连接操作记录审计日志**，包括创建、修改、启用、禁用、测试连接，便于企业排查问题。
6. **本地与云端统一管理**。Ollama、LM Studio 等本地模型不应该拥有特殊 UI，而应作为普通 Provider 管理，这样上层运行时无需关心模型来自哪里。

**P0 的目标只有一个：把任何 AI 服务安全、稳定、可观测地接入平台，为后续的 Model Runtime、Gateway Runtime、Prompt Runtime 和 RAG Runtime 提供统一、可靠的基础。**
