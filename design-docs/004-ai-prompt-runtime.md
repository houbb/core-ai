这里我建议对整体路线做一个调整。

实际上，**Prompt 不应该只是一个模板管理器，而应该升级为 Prompt Engineering Runtime（提示词工程运行时）**。

因为现代 AI 应用中，Prompt 已经不是一段字符串，而是一套完整的资产：

* System Prompt
* Prompt Template
* Variables
* Version
* A/B Test
* Evaluation
* Guardrail
* Output Schema
* Prompt Chain

所以 **P3 应该做成 Prompt Runtime，而不是 Prompt Editor。**

---

# Phase 3：AI Prompt Runtime ⭐⭐⭐⭐⭐

> Prompt as Code

## 定位

Prompt Runtime 负责：

> **统一管理企业所有 Prompt 资产。**

所有 AI 场景：

```text
AI Chat
AI OCR
AI SQL
AI Coding
AI Translation
AI Knowledge
AI Agent
```

都不直接写 Prompt。

而是引用：

```text
Prompt
```

例如：

```java
scene = "translate"

↓

prompt = "translate-v3"

↓

model

↓

provider
```

Prompt 成为企业的可复用资产。

---

# 一、设计目标

一句话：

> **Prompt 像代码一样管理。**

支持：

* 编辑
* 调试
* 发布
* 回滚
* 比较
* 版本
* 权限
* 测试

全部内置。

---

# 二、整体架构

```text
Prompt

↓

Template

↓

Variable

↓

Renderer

↓

Validator

↓

Output

↓

LLM
```

Prompt Runtime：

只负责：

生成最终 Prompt。

---

# 三、Prompt 生命周期

建议：

```text
Draft

↓

Testing

↓

Published

↓

Deprecated

↓

Archived
```

企业：

生产环境：

只能：

Published。

---

# 四、Prompt 管理界面

推荐：

```text
┌──────────────────────────────────────────────┐
│ Prompts                                     │
├───────────────┬──────────────────────────────┤
│ Chat          │                              │
│ Translate     │                              │
│ OCR           │      Prompt Detail           │
│ SQL           │                              │
│ Coding        │                              │
│ Email         │                              │
└───────────────┴──────────────────────────────┘
```

左：

Prompt。

右：

编辑。

---

# 五、Prompt 基本信息

```text
Name

Code

Description

Category

Scene

Status

Version
```

例如：

```text
translate-v3
```

---

# 六、Prompt 编辑器

建议：

不要：

普通 textarea。

而是：

IDE。

例如：

```text
System

----------------------------------

你是一名专业翻译助手。

请根据用户要求翻译。

{{language}}

-------------------------------

User

{{content}}
```

支持：

高亮。

变量。

折叠。

行号。

自动补全。

---

# 七、变量系统（核心）

例如：

```text
{{user}}

{{date}}

{{history}}

{{knowledge}}

{{context}}

{{language}}

{{document}}
```

全部：

自动提示。

例如：

输入：

```text
{{
```

自动：

弹：

```text
content

history

language

knowledge
```

---

变量：

支持：

类型。

```text
String

Integer

Boolean

JSON

List

Object
```

以后：

自动校验。

---

# 八、Prompt 渲染

例如：

模板：

```text
Translate

{{language}}

{{content}}
```

输入：

```text
language

中文

content

Hello
```

实时：

预览：

```text
Translate

中文

Hello
```

不用：

点：

Run。

---

# 九、Prompt Playground

右侧：

建议：

内置：

Playground。

```text
Input

↓

Render

↓

Send

↓

Output
```

同时：

展示：

```text
Prompt

Token

Latency

Cost

Output
```

调 Prompt：

神器。

---

# 十、Prompt Compare

支持：

版本：

比较。

例如：

```text
V2

VS

V3
```

Diff：

```text
- 请翻译

+ 请专业翻译
```

类似：

Git。

---

# 十一、Prompt Version

支持：

```text
V1

V2

V3

V4
```

每次：

发布：

自动：

生成。

支持：

Rollback。

---

# 十二、Prompt Chain

Prompt：

不仅：

一个。

例如：

```text
Summary

↓

Review

↓

Translate

↓

Output
```

形成：

Chain。

以后：

Agent：

直接：

调用。

---

# 十三、Prompt Output

建议：

支持：

Schema。

例如：

```json
{
    "title":"",
    "summary":"",
    "score":0
}
```

Prompt：

要求：

必须：

输出：

JSON。

以后：

程序：

不用：

解析。

---

# 十四、Prompt Guardrail

支持：

限制。

例如：

```text
Sensitive

Injection

Illegal

Length

JSON Validate
```

Prompt：

发送前：

自动：

检查。

---

# 十五、Prompt 测试

建议：

支持：

Test Case。

例如：

```text
Input

↓

Expected

↓

Actual

↓

Pass
```

以后：

Prompt：

升级：

不会：

翻车。

---

# 十六、Prompt A/B Test

支持：

例如：

```text
Prompt A

Prompt B
```

统计：

```text
Success

Latency

Cost

User Score
```

以后：

自动：

推荐：

最佳 Prompt。

---

# 十七、Prompt 权限

支持：

```text
Public

Project

Department

Private
```

例如：

财务：

Prompt。

只有：

财务：

能看。

---

# 十八、UX（重点）

## Prompt 编辑器

建议：

像：

VSCode。

不要：

普通：

Textarea。

---

## 自动补全

输入：

```text
{{
```

立即：

变量：

提示。

---

## Prompt Preview

编辑：

左边。

右边：

实时：

最终：

Prompt。

不用：

点击。

---

## 一键复制

支持：

```text
Copy Prompt

Copy Render

Copy JSON
```

方便：

调试。

---

## Token 预估

实时：

显示：

```text
Prompt

312 Token
```

避免：

超长。

---

# 十九、数据模型设计

建议拆成 **8 张表**。

---

## ai_prompt

Prompt 定义。

```sql
id

code

name

description

category

scene_id

status

current_version

created_at

updated_at
```

---

## ai_prompt_version

版本。

```sql
id

prompt_id

version

system_prompt

user_prompt

assistant_prompt

change_log

created_by

created_at
```

---

## ai_prompt_variable

变量。

```sql
id

prompt_version_id

name

type

required

default_value

description
```

---

## ai_prompt_output_schema

输出。

```sql
id

prompt_version_id

schema_json

strict_mode
```

---

## ai_prompt_testcase

测试。

```sql
id

prompt_version_id

input_json

expected_output

enabled
```

---

## ai_prompt_abtest

A/B。

```sql
id

scene_id

version_a

version_b

traffic_ratio
```

---

## ai_prompt_guardrail

安全。

```sql
id

prompt_version_id

rule

enabled
```

---

## ai_prompt_render_log

渲染。

```sql
id

prompt_version_id

variables

rendered_prompt

created_at
```

可配置保留策略，仅用于调试，避免长期存储包含敏感信息的完整 Prompt。

---

# 二十、需要特别注意的设计

## ① Prompt 是资产，不是字符串

任何业务都不要：

```java
prompt = """
你是...
"""
```

应该：

```java
prompt = "translate-v3"
```

统一：

管理。

---

## ② Prompt 与 Scene 解耦

一个 Prompt：

可以：

多个：

Scene：

共用。

例如：

```text
Translation Prompt
```

可以：

```text
Chat

OCR

Knowledge
```

全部：

引用。

---

## ③ Prompt 永远版本化

任何：

修改：

都是：

新版本。

不要：

覆盖。

---

## ④ Prompt 支持测试再发布

企业：

不要：

改完：

立即：

上线。

应该：

```text
Draft

↓

Playground

↓

Test Case

↓

Publish
```

---

## ⑤ Prompt 与模型解耦

Prompt 不绑定 GPT、Claude 或其他具体模型，而是描述**任务和输出要求**。真正使用哪个模型，应由 Scene Runtime 或 Gateway Runtime 决定。这样同一份 Prompt 可以在不同模型间复用，并便于后续进行模型切换和成本优化。

---

## ⑥ Prompt 支持结构化输出

不要鼓励业务解析自然语言。Prompt Runtime 应原生支持 JSON Schema、结构化输出约束以及输出校验，为后续 Workflow、Agent 和业务系统提供稳定的数据接口。

---

**P3 的核心目标，是把 Prompt 从“一段文本”升级为“企业级 Prompt 资产”，让 Prompt 具备代码工程一样的版本管理、测试、发布、回滚、复用和治理能力，真正实现 Prompt as Code。**
