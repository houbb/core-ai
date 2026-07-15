我建议 **P2 不叫 Prompt Runtime，而叫 AI Scene Runtime（AI 场景运行时）**。

这是整个 `core-ai` 最有价值的一层，也是用户每天都会接触的能力。

原因是：

> **90% 的用户不知道该选什么模型，但他们知道自己要做什么。**

用户不会说：

> 我要 GPT-5、Claude Opus、Gemini 2.5。

用户会说：

> 我要翻译一篇文章。
>
> 我要写邮件。
>
> 我要 OCR。
>
> 我要总结 PDF。
>
> 我要分析代码。

所以，对于企业平台来说，**场景（Scene）应该成为 AI 的第一入口，而不是模型（Model）。**

---

# Phase 2：AI Scene Runtime ⭐⭐⭐⭐⭐

> AI Capability as a Service

## 定位

AI Scene Runtime 负责：

> **把 AI 能力封装成业务场景。**

上层业务：

不关心：

* Provider
* Model
* Temperature
* TopP
* Max Token

业务只关心：

```text
AI.chat()

AI.translate()

AI.summarize()

AI.ocr()

AI.review()

AI.generateImage()
```

Scene Runtime 自动选择：

```text
Scene

↓

Alias

↓

Model

↓

Provider
```

整个系统以后：

全部依赖 Scene。

---

# 一、设计目标

一句话：

> **用户选择"我要做什么"，而不是"我要用哪个模型"。**

例如：

不要：

```text
GPT5

Claude

Gemini

DeepSeek
```

而是：

```text
聊天

翻译

总结

OCR

代码

图片生成

知识问答

SQL

邮件

Agent
```

---

# 二、Scene 分类

建议系统内置。

```text
Conversation

Writing

Translate

Summarize

Coding

Code Review

SQL

OCR

Vision

Speech

Image

Video

Embedding

Knowledge

RAG

Reasoning

Workflow

Agent
```

以后：

插件还能增加：

```text
Medical

Finance

Legal

Education
```

---

# 三、Scene 生命周期

```text
Draft

↓

Testing

↓

Published

↓

Disabled

↓

Archived
```

业务：

只能看到：

Published。

---

# 四、Scene 管理界面

建议：

```text
┌─────────────────────────────────────────────┐
│ AI Scenes                                  │
├──────────────┬──────────────────────────────┤
│ Chat         │                              │
│ Translate    │                              │
│ OCR          │        Scene Detail          │
│ SQL          │                              │
│ Coding       │                              │
│ Image        │                              │
└──────────────┴──────────────────────────────┘
```

左侧：

Scene。

右侧：

配置。

---

# 五、Scene Card

例如：

```text
Chat

💬

GPT5

Published
```

或者：

```text
OCR

👁

Qwen-VL

Published
```

一眼知道：

这个场景：

用什么模型。

---

# 六、Scene 配置

建议：

分 Tab。

---

## Basic

```text
Scene Name

Description

Category

Enabled

Version
```

---

## Model

绑定：

```text
Primary Model

Fallback Model

Alias
```

例如：

```text
Chat

↓

GPT5

↓

Claude

↓

DeepSeek
```

以后：

Gateway：

自动切换。

---

## Parameter

默认：

```text
Temperature

TopP

Max Output

Streaming

Reasoning

JSON Mode
```

这些：

场景级默认值。

不是模型默认值。

---

## Prompt

绑定：

```text
System Prompt

Template

Variables
```

以后：

Prompt Runtime：

读取。

---

## Permission

支持：

```text
Everyone

Admin

Developer

Department
```

以后：

企业版：

很好用。

---

# 七、Scene 推荐

推荐：

系统：

内置：

```text
聊天

↓

GPT5
```

```text
OCR

↓

Qwen-VL
```

```text
Embedding

↓

BGE
```

```text
SQL

↓

Claude
```

用户：

直接：

启用。

即可。

---

# 八、Scene 模板

建议：

内置。

例如：

```text
AI Chat

AI Email

AI Translate

AI OCR

AI SQL

AI Meeting

AI Coding

AI Review

AI Summarize

AI Search

AI Report
```

点击：

```text
Create
```

立即：

生成。

---

# 九、Scene Workflow

一个 Scene：

不仅一个模型。

例如：

```text
OCR

↓

Vision

↓

Text

↓

Summarize

↓

Translate
```

未来：

支持：

```text
Pipeline
```

例如：

```text
PDF

↓

OCR

↓

Embedding

↓

RAG

↓

Answer
```

全部：

一个：

Scene。

---

# 十、Scene 测试

右侧：

建议：

内置：

Playground。

例如：

```text
Input

↓

Run

↓

Output
```

右侧：

实时：

展示：

```text
Prompt

Model

Latency

Cost

Output
```

以后：

调 Prompt：

非常舒服。

---

# 十一、Scene 调试

支持：

```text
Trace
```

例如：

```text
Input

↓

Prompt

↓

Model

↓

Provider

↓

Output
```

全部：

可视化。

---

# 十二、Scene 版本

建议：

支持：

```text
V1

V2

V3
```

例如：

Prompt：

升级。

不用：

覆盖。

支持：

Rollback。

---

# 十三、Scene 发布

建议：

发布：

```text
Draft

↓

Test

↓

Publish
```

企业：

不要：

直接：

上线。

---

# 十四、Scene 分享

支持：

导出：

```text
JSON
```

例如：

```text
AI Chat Scene
```

别人：

导入。

即可。

以后：

形成：

Scene Marketplace。

---

# 十五、UX（重点）

## 首页：

不要：

Provider。

不要：

Model。

直接：

```text
你想做什么？
```

例如：

```
┌──────────────┐
│ 💬 聊天       │
├──────────────┤
│ 🌍 翻译       │
├──────────────┤
│ 📄 总结       │
├──────────────┤
│ 👁 OCR       │
├──────────────┤
│ 💻 写代码     │
└──────────────┘
```

体验：

像：

ChatGPT。

---

## 推荐

每个：

Scene：

显示：

```text
⭐ Recommended
```

减少：

用户：

纠结。

---

## 成本

Scene：

右上角：

建议：

显示：

```text
$

Cheap
```

或者：

```text
High Quality
```

一眼知道。

---

# 十六、数据模型设计

建议拆成 **7 张表**。

---

## ai_scene

场景定义。

```sql
id

code

name

description

category

icon

status

enabled

version

created_at

updated_at
```

---

## ai_scene_model

模型绑定。

```sql
id

scene_id

model_alias

priority

fallback

enabled
```

支持：

多个模型。

以后：

Gateway：

自动：

切。

---

## ai_scene_parameter

默认参数。

```sql
scene_id

temperature

top_p

max_output_tokens

reasoning_effort

json_mode

streaming
```

---

## ai_scene_prompt

绑定 Prompt。

```sql
scene_id

prompt_id

version
```

以后：

Prompt Runtime：

管理。

---

## ai_scene_permission

权限。

```sql
scene_id

role

department

user_group
```

---

## ai_scene_version

版本。

```sql
id

scene_id

version

config_json

created_by

created_at
```

支持：

Rollback。

---

## ai_scene_template

模板。

```sql
id

scene

template_name

builtin

config_json
```

以后：

Marketplace：

直接：

安装。

---

# 十七、需要特别注意的设计

## ① Scene 是业务接口，不是技术接口

业务代码永远应该调用：

```java
AI.execute("translate")
AI.execute("ocr")
AI.execute("knowledge-search")
```

而不是：

```java
AI.chat(model="gpt-5")
```

这样模型替换、成本优化、供应商迁移都不会影响业务。

---

## ② Scene 可以绑定多个模型

不要限制：

一个：

Scene。

例如：

```text
Code Review

↓

Claude（优先）

↓

GPT5（备用）

↓

Qwen-Coder（本地）
```

Gateway：

自动：

Failover。

---

## ③ Scene 不应该保存 Prompt 内容

这里只保存：

```text
Prompt ID
```

真正：

Prompt：

属于：

下一阶段：

Prompt Runtime。

否则：

Prompt：

以后：

无法：

版本管理。

---

## ④ Scene 可以组合多个能力

未来：

Scene：

不是：

一个模型。

而是：

一个：

Pipeline。

例如：

```text
PDF

↓

OCR

↓

Chunk

↓

Embedding

↓

Vector Search

↓

LLM

↓

Answer
```

这也是后续 Agent Runtime 和 Workflow Runtime 的基础。

---

## ⑤ 系统必须内置高质量场景

安装完成后，用户无需配置即可直接使用，例如：

* AI Chat
* AI Translate
* AI OCR
* AI Meeting Summary
* AI SQL Assistant
* AI Coding Assistant
* AI Knowledge Search
* AI Email Writer

做到**开箱即用**，用户后续只需要替换底层模型即可获得更好的效果，而无需重新搭建整个 AI 应用。

---

**P2 的核心目标，是把 AI 从“模型中心”升级为“场景中心”，建立统一的 AI 能力目录（AI Capability Catalog），让整个企业平台都以业务场景的方式消费 AI，而不是直接操作底层模型。**
