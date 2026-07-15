这里我会调整一下整个路线。

很多 AI 平台做到 Prompt 就结束了，但真正企业落地之后，**最大的挑战不是 Prompt，而是工具调用（Tool Calling）**。

未来 AI 的能力，不是来自模型本身，而是来自：

* MCP（Model Context Protocol）
* Function Calling
* REST API
* 数据库
* 企业系统
* 插件
* Workflow

所以 **P4 不应该是 Conversation Runtime，而应该是 AI Tool Runtime（工具运行时）**。

这是整个 `core-ai` 与 `core-storage`、`core-user`、`core-notification` 等平台连接的桥梁。

---

# Phase 4：AI Tool Runtime ⭐⭐⭐⭐⭐

> Connect AI with Everything

## 定位

AI Tool Runtime 负责：

> **让 AI 能够安全、标准化地调用外部能力。**

AI 不再只是聊天。

而是真正能够：

```text
查询数据库

发送邮件

发送短信

创建用户

读取知识库

执行 Workflow

调用 HTTP API

执行 SQL

调用 MCP

执行插件
```

以后：

所有业务：

统一：

注册 Tool。

AI：

统一：

调用。

---

# 一、设计目标

一句话：

> **所有能力，都变成 AI 可以调用的 Tool。**

例如：

以后：

```text
core-storage

↓

File Search Tool
```

```text
core-user

↓

Get User Tool
```

```text
core-notification

↓

Send Email Tool
```

```text
core-payment

↓

Query Order Tool
```

AI：

无需知道：

业务。

只知道：

Tool。

---

# 二、整体架构

```text
AI

↓

Scene

↓

Prompt

↓

Tool Runtime

↓

Tool Registry

↓

Executor

↓

Business Service
```

所有 Tool：

统一：

注册。

统一：

执行。

---

# 三、Tool 生命周期

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

Disabled

↓

Archived
```

---

# 四、Tool 分类

建议：

不要：

按来源。

按能力。

```text
HTTP

MCP

Database

Plugin

Workflow

Shell

Python

Java

REST

GraphQL

File

Search

Knowledge

Notification

Payment
```

未来：

插件：

自己：

增加。

---

# 五、Tool 管理界面

推荐：

```text
┌─────────────────────────────────────────────┐
│ AI Tools                                   │
├──────────────┬──────────────────────────────┤
│ Send Email   │                              │
│ Search File  │                              │
│ OCR          │       Tool Detail            │
│ SQL          │                              │
│ Search User  │                              │
│ Create Order │                              │
└──────────────┴──────────────────────────────┘
```

---

# 六、Tool Card

例如：

```text
Send Email

REST

Published

Notification
```

或者：

```text
Knowledge Search

MCP

Published
```

一眼：

知道：

用途。

---

# 七、Tool 基本信息

```text
Name

Code

Category

Description

Owner

Version

Status
```

---

# 八、Tool Schema（核心）

Tool：

一定：

采用：

Schema。

例如：

```json
{
  "name":"sendEmail",
  "description":"Send Email",
  "parameters":{
      "to":"string",
      "subject":"string",
      "content":"string"
  }
}
```

以后：

AI：

自动：

理解。

---

支持：

JSON Schema。

OpenAPI。

MCP Tool。

Function Calling。

统一。

---

# 九、参数定义

支持：

```text
String

Integer

Boolean

Array

Object

Enum

DateTime
```

支持：

```text
Required

Default

Validation

Regex

Range
```

全部：

配置。

---

# 十、Tool 调试

右侧：

建议：

内置：

```text
Input

↓

Run

↓

Response
```

同时：

显示：

```text
Latency

Cost

Output

Exception
```

方便：

开发。

---

# 十一、Tool 权限

非常重要。

例如：

```text
Everyone

Admin

Finance

HR

Developer
```

甚至：

```text
AI Only
```

例如：

删除：

用户。

普通：

聊天。

不能：

调用。

---

# 十二、Tool 安全策略

建议：

内置：

```text
Read Only

Read Write

Dangerous

Manual Confirm
```

例如：

```text
Delete User
```

必须：

确认。

---

```text
Send Money
```

必须：

审批。

---

# 十三、Tool Chain

Tool：

支持：

组合。

例如：

```text
Search User

↓

Get Order

↓

Send Email
```

以后：

Workflow：

直接：

复用。

---

# 十四、Tool Playground

建议：

每个：

Tool：

都有：

Playground。

例如：

```text
Input JSON

↓

Execute

↓

Response
```

支持：

Mock。

方便：

开发。

---

# 十五、Tool Trace

建议：

展示：

```text
AI

↓

Prompt

↓

Tool

↓

HTTP

↓

Database

↓

Result
```

以后：

排查：

神器。

---

# 十六、MCP 支持（重点）

建议：

原生。

支持：

```text
Local MCP

Remote MCP
```

例如：

```text
Filesystem

GitHub

Postgres

Browser

Slack
```

全部：

注册。

---

以后：

AI：

无需：

知道：

实现。

---

# 十七、Tool Marketplace

未来：

支持：

安装。

例如：

```text
GitHub Tool

Jira Tool

Confluence Tool

Notion Tool

OpenSearch Tool

MySQL Tool
```

点击：

安装。

即可。

---

# 十八、UX（重点）

## Tool 图标

例如：

```text
📧 Send Email

📂 File Search

👤 User Query

📈 Dashboard
```

不要：

只有：

文字。

---

## Schema 自动生成

如果：

导入：

OpenAPI。

自动：

生成：

Tool。

不用：

重新写。

---

## 调试

支持：

复制：

```text
Request

Response

Curl
```

方便：

开发。

---

## 权限

危险：

Tool：

红色。

例如：

```text
Delete Database
```

不会：

误操作。

---

# 十九、数据模型设计

建议拆成 **9 张核心表**。

---

## ai_tool

Tool 定义。

```sql
id

code

name

description

category

type

owner

status

current_version

created_at

updated_at
```

---

## ai_tool_version

版本。

```sql
id

tool_id

version

schema_json

executor

change_log

created_by

created_at
```

---

## ai_tool_parameter

参数。

```sql
id

tool_version_id

name

type

required

default_value

validation_rule

description
```

---

## ai_tool_permission

权限。

```sql
id

tool_id

role

department

policy
```

---

## ai_tool_policy

执行策略。

```sql
id

tool_id

readonly

manual_confirm

approval_required

timeout

retry
```

---

## ai_tool_binding

绑定。

```sql
id

tool_id

target_type

target_id
```

例如：

```text
REST

/api/sendEmail
```

或者：

```text
MCP

filesystem
```

---

## ai_tool_testcase

测试。

```sql
id

tool_version_id

input_json

expected_result
```

---

## ai_tool_execution_log

执行日志。

```sql
id

tool_id

request_json

response_json

latency

status

created_at
```

建议默认对参数进行脱敏（如密码、Token、身份证号等），并支持配置日志保留期限。

---

## ai_tool_market

市场。

```sql
id

tool_name

publisher

version

install_count
```

---

# 二十、需要特别注意的设计

## ① Tool 是能力，不是接口

不要让 AI 直接调用：

```text
POST /api/user/create
```

应该：

```text
Create User
```

Tool Runtime：

负责：

转换。

---

## ② Tool 必须声明 Schema

AI：

不能：

猜。

必须：

知道：

参数。

返回。

类型。

---

## ③ Tool 必须安全

建议：

内置：

```text
Read

Write

Danger
```

三级。

以后：

Agent：

才能：

放心。

---

## ④ Tool 与业务解耦

Tool 不直接依赖某个 AI 模型，也不依赖某个具体 Agent。它是企业能力的标准封装，可以被 Chat、Workflow、Agent、RAG 等多个运行时复用。

---

## ⑤ Tool 必须可测试、可追踪、可审计

任何 Tool 都应支持：

* 独立 Playground 调试
* 请求/响应日志
* Trace 链路
* 权限校验
* 审计记录

这样企业才能放心把核心业务能力开放给 AI。

---

# P4 在整个 AI Runtime 中的位置

```text
Provider Runtime
        │
Model Runtime
        │
Scene Runtime
        │
Prompt Runtime
        │
────────────────────────────────────
Tool Runtime   ← AI 开始真正与企业系统连接
────────────────────────────────────
        │
Gateway Runtime
        │
Conversation Runtime
        │
Knowledge Runtime
        │
Agent Runtime
        │
Enterprise AI Platform
```

**P4 是整个 `core-ai` 从“会回答问题”走向“会执行任务”的分水岭。** 从这一阶段开始，AI 不再只是一个聊天模型，而是能够通过标准化 Tool 安全地操作企业系统、调用平台能力，并与整个 Core Platform 形成真正的生态闭环。
