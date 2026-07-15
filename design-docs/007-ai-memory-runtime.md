这里我会再次调整路线。

从 P0～P5，我们解决了：

* 如何连接 AI（Provider）
* 如何管理模型（Model）
* 如何抽象业务（Scene）
* 如何管理 Prompt（Prompt）
* 如何调用工具（Tool）
* 如何稳定运行（Gateway）

但是企业真正上线之后，第一个暴露出来的问题不是模型，而是：

> **聊天记录、上下文、长期记忆怎么管理？**

所以我建议 **P6 不叫 Conversation，而升级为 AI Conversation & Memory Runtime（会话与记忆运行时）**。

这是 Agent、RAG、多轮对话的基础。

---

# Phase 6：AI Conversation & Memory Runtime ⭐⭐⭐⭐⭐

> Every Conversation Has Memory

## 定位

Conversation Runtime 负责：

> **统一管理 AI 的所有会话、上下文和记忆。**

以后：

* Chat
* AI Assistant
* Agent
* Workflow
* Customer Service
* Knowledge Base

全部依赖：

```text
Conversation Runtime
```

它不是聊天 UI。

而是：

AI 的大脑。

---

# 一、设计目标

一句话：

> **让 AI 能够持续记住用户，而不是每次都是新的聊天。**

例如：

今天：

```text
帮我写邮件
```

明天：

```text
继续昨天那个
```

AI：

能够：

找到：

昨天。

继续。

---

# 二、整体架构

```text
User

↓

Conversation

↓

Session

↓

Message

↓

Context

↓

Memory

↓

Gateway

↓

LLM
```

Conversation：

负责：

组织。

Memory：

负责：

长期。

---

# 三、Conversation 生命周期

建议：

```text
Created

↓

Active

↓

Archived

↓

Deleted(Logical)
```

删除：

建议：

逻辑。

避免：

日志：

丢失。

---

# 四、Conversation 管理界面

推荐：

```text
┌──────────────────────────────────────────────┐
│ Conversations                               │
├──────────────┬───────────────────────────────┤
│ Chat A       │                               │
│ OCR          │                               │
│ SQL          │       Conversation Detail     │
│ Knowledge    │                               │
│ Agent        │                               │
└──────────────┴───────────────────────────────┘
```

左：

会话。

右：

消息。

类似：

ChatGPT。

---

# 五、Conversation

基本信息：

```text
Conversation Name

Owner

Scene

Status

Created Time

Updated Time
```

支持：

重命名。

收藏。

归档。

标签。

---

# 六、Session

为什么：

需要：

Session？

例如：

同一个：

Conversation：

可以：

多个：

Session。

例如：

```text
客服

↓

2026-07

↓

Session1

↓

Session2
```

方便：

恢复。

---

# 七、Message

建议：

统一：

Message。

例如：

```text
System

User

Assistant

Tool

Function

Reasoning
```

以后：

全部：

统一。

---

Message：

支持：

Markdown。

图片。

文件。

JSON。

代码。

全部。

---

# 八、Context Window

建议：

内置：

Context Builder。

例如：

```text
History

↓

Summary

↓

Memory

↓

Knowledge

↓

Prompt

↓

LLM
```

不是：

全部：

历史。

否则：

Token：

爆炸。

---

支持：

自动：

摘要。

例如：

超过：

100 条。

自动：

生成：

Summary。

---

# 九、Memory（核心）

建议：

分层。

例如：

## 短期

```text
Session Memory
```

本轮：

聊天。

---

## 中期

```text
Conversation Memory
```

整个：

会话。

---

## 长期

```text
User Memory
```

例如：

```text
用户喜欢：

Java
```

以后：

一直：

记住。

---

## 企业

```text
Organization Memory
```

整个：

团队：

共享。

---

# 十、Memory 类型

建议：

```text
Preference

Fact

Task

Knowledge

Summary

Todo

Profile
```

例如：

```text
用户喜欢：

Vue3
```

Preference。

---

# 十一、Memory 管理

支持：

查看。

例如：

```text
Memory

↓

Preference

↓

Java
```

管理员：

可以：

删除。

修改。

冻结。

---

# 十二、Conversation 搜索

支持：

全文。

例如：

```text
搜索：

SpringBoot
```

直接：

找到：

聊天。

---

支持：

过滤。

```text
Scene

User

Date

Tag
```

---

# 十三、Conversation Share

支持：

导出：

```text
Markdown

JSON

PDF
```

分享。

例如：

```text
Chat Link
```

以后：

很好。

---

# 十四、Conversation Replay

建议：

支持：

Replay。

例如：

```text
Message1

↓

Message2

↓

Tool

↓

Response
```

重新：

执行。

方便：

调试。

---

# 十五、Conversation Trace

建议：

展示：

```text
Prompt

↓

Context

↓

Memory

↓

Tool

↓

Gateway

↓

Provider
```

完整：

链路。

---

# 十六、UX（重点）

## 左侧

Conversation。

类似：

ChatGPT。

---

## 中间

Message。

支持：

Markdown。

代码。

图片。

引用。

---

## 右侧

Context。

例如：

```text
Prompt

Memory

Tool

Cost
```

开发：

模式。

---

## 自动命名

第一句话：

自动：

生成：

标题。

例如：

```text
Spring Boot 登录设计
```

不用：

用户：

输入。

---

## 收藏

支持：

```text
⭐
```

以后：

快速：

找到。

---

# 十七、数据模型设计

建议拆成 **10 张核心表**。

---

## ai_conversation

```sql
id

title

owner_id

scene_id

status

created_at

updated_at
```

---

## ai_session

```sql
id

conversation_id

session_name

status

created_at
```

---

## ai_message

```sql
id

session_id

role

content

content_type

token

created_at
```

---

## ai_message_attachment

```sql
id

message_id

file_id

type
```

---

## ai_memory

统一记忆。

```sql
id

owner_type

owner_id

memory_type

content

importance

source

created_at
```

其中：

```text
owner_type
```

可以：

```text
User

Conversation

Organization
```

---

## ai_summary

摘要。

```sql
id

conversation_id

summary

message_start

message_end
```

---

## ai_context_snapshot

上下文。

```sql
id

session_id

prompt

context

memory

created_at
```

用于调试，不建议永久保存全部 Prompt，可配置保留策略和脱敏。

---

## ai_conversation_tag

标签。

```sql
id

conversation_id

tag
```

---

## ai_conversation_share

分享。

```sql
id

conversation_id

share_code

expired_at
```

---

## ai_replay_log

Replay。

```sql
id

conversation_id

request_json

response_json

created_at
```

---

# 十八、Memory 策略（企业级）

建议不要把所有历史消息都塞给模型，而是采用分层策略：

```text
最新消息（Last N）
        │
自动摘要（Conversation Summary）
        │
长期记忆（Memory）
        │
知识库（Knowledge）
        │
Prompt
        │
LLM
```

这样：

* Token 消耗稳定
* 上下文不会无限增长
* 长期聊天仍能保持连续性

---

# 十九、注意点

## ① Conversation ≠ Chat UI

很多项目：

Conversation：

就是：

聊天框。

其实：

不是。

Conversation：

是：

整个：

上下文。

---

## ② Message 永远不可修改

修改：

产生：

新版本。

不要：

覆盖。

这样：

Replay：

才能：

一致。

---

## ③ Memory 与 Conversation 解耦

Memory：

不是：

聊天记录。

Memory：

是：

AI：

长期：

记住：

的信息。

例如：

```text
用户偏好 Java

用户喜欢中文回答

用户项目使用 Vue3
```

这些不应该依赖某一个 Conversation，而应该作为独立资产管理。

---

## ④ Summary 是降低 Token 成本的关键

企业长对话不能依赖完整历史消息，应自动生成摘要，并作为 Context Builder 的输入。

---

## ⑤ 所有会话必须可审计、可回放

任何一次 AI 回复，都应能够追踪：

```text
Conversation
→ Session
→ Messages
→ Memory
→ Prompt
→ Tool
→ Gateway
→ Provider
→ Response
```

这不仅方便调试，也是企业环境中的审计基础。

---

# P6 在整个 AI Runtime 中的位置

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
Gateway Runtime
        │
────────────────────────────
Conversation & Memory Runtime ⭐⭐⭐⭐⭐
────────────────────────────
        │
Knowledge Runtime
        │
Agent Runtime
        │
Analytics Runtime
        │
Enterprise AI Platform
```

**P6 的目标，是为整个 AI 平台建立统一的会话、上下文和记忆体系。** 从这一阶段开始，AI 不再只是“一次请求一次响应”，而是真正具备连续对话、长期记忆、上下文管理和企业级可追溯能力，为后续 RAG、Agent 和多智能体协作提供坚实基础。
