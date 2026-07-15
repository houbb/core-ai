这里我建议 **P8 不做 Analytics，而是先做 Agent Runtime。**

原因很简单。

到 P7 为止，我们已经拥有了：

* ✅ Provider
* ✅ Model
* ✅ Scene
* ✅ Prompt
* ✅ Tool
* ✅ Gateway
* ✅ Conversation
* ✅ Knowledge

**AI 已经具备了所有基础能力。**

真正缺少的只剩一个：

> **让 AI 自己完成任务。**

也就是 **Agent Runtime**。

Analytics 可以建立在 Agent 之上，而 Agent 无法建立在 Analytics 之上，所以从架构依赖来说，**Agent 应该先于 Analytics。**

---

# Phase 8：AI Agent Runtime ⭐⭐⭐⭐⭐

> AI Workers for Every Business

## 定位

Agent Runtime 负责：

> **把 Prompt + Tool + Memory + Knowledge + Workflow 组合成一个能够自主完成任务的 AI Agent。**

从这一阶段开始：

AI 不再只是：

```text
问

↓

答
```

而是：

```text
目标

↓

思考

↓

规划

↓

执行

↓

调用 Tool

↓

检查结果

↓

继续执行

↓

完成任务
```

这就是：

真正的 Agent。

---

# 一、设计目标

一句话：

> **让 AI 从回答问题升级为完成任务。**

例如：

用户：

```text
帮我分析这个项目。
```

Agent：

自动：

```text
读取 Git

↓

分析代码

↓

查文档

↓

运行测试

↓

生成报告

↓

发送邮件
```

整个：

自动。

---

# 二、整体架构

```text
User

↓

Agent

↓

Planner

↓

Task

↓

Memory

↓

Knowledge

↓

Tool

↓

Gateway

↓

Provider
```

Agent：

协调：

所有：

Runtime。

---

# 三、Agent 生命周期

建议：

```text
Draft

↓

Testing

↓

Published

↓

Running

↓

Paused

↓

Archived
```

支持：

暂停。

恢复。

---

# 四、Agent 管理界面

推荐：

```text
┌─────────────────────────────────────────────┐
│ Agents                                     │
├──────────────┬──────────────────────────────┤
│ Coding       │                              │
│ HR           │                              │
│ Customer     │        Agent Detail          │
│ Investment   │                              │
│ Research     │                              │
└──────────────┴──────────────────────────────┘
```

左：

Agent。

右：

详情。

---

# 五、Agent 基本信息

```text
Name

Description

Owner

Scene

Version

Status
```

支持：

标签。

头像。

图标。

颜色。

---

# 六、Agent Profile

建议：

每个：

Agent：

拥有：

Profile。

例如：

```text
Role

Goal

Personality

Language

Style

Constraint
```

例如：

```text
Role

Senior Java Architect
```

Goal：

```text
帮助开发者完成代码设计
```

以后：

Prompt：

自动：

生成。

---

# 七、Planner（核心）

建议：

Agent：

必须：

拥有：

Planner。

例如：

用户：

```text
分析订单异常
```

Planner：

自动：

拆：

```text
查询订单

↓

查询日志

↓

查询数据库

↓

分析原因

↓

生成报告
```

不是：

Prompt。

而是：

Task。

---

# 八、Task Runtime

一个：

Agent：

多个：

Task。

例如：

```text
Task1

↓

Task2

↓

Task3
```

支持：

```text
串行

并行

条件

循环

重试
```

以后：

Workflow：

直接：

复用。

---

# 九、Tool Calling

Agent：

自动：

决定：

调用：

哪个：

Tool。

例如：

```text
Get User

↓

Search Order

↓

Send Email
```

无需：

用户：

指定。

---

# 十、Knowledge Integration

Agent：

自动：

搜索：

Knowledge。

例如：

```text
Question

↓

Knowledge

↓

Chunk

↓

Answer
```

全部：

自动。

---

# 十一、Memory Integration

Agent：

自动：

读取：

Memory。

例如：

```text
用户喜欢：

Java
```

以后：

回答：

自动：

偏：

Java。

---

# 十二、Multi-Agent（企业重点）

建议：

支持：

多个：

Agent。

例如：

```text
CEO

↓

Architect

↓

Developer

↓

Tester
```

协作。

例如：

```text
Manager Agent

↓

Coder Agent

↓

Reviewer Agent

↓

QA Agent
```

企业：

非常：

重要。

---

# 十三、Agent Playground

建议：

右侧：

支持：

Run。

例如：

```text
Goal

↓

Run Agent

↓

Trace
```

实时：

展示：

```text
Planner

Task

Tool

Memory

Knowledge

Response
```

开发：

神器。

---

# 十四、Agent Trace

建议：

可视化：

```text
Goal

↓

Plan

↓

Task

↓

Tool

↓

Knowledge

↓

Memory

↓

LLM

↓

Output
```

全部：

展开。

---

# 十五、Agent Approval

企业：

必须。

例如：

```text
Delete User
```

↓

等待：

审批。

例如：

```text
Send Money
```

↓

人工：

确认。

Agent：

不能：

直接：

执行。

---

# 十六、Agent Schedule

支持：

定时。

例如：

```text
每天

9:00
```

自动：

运行。

例如：

```text
日报

↓

邮件
```

以后：

Workflow：

统一。

---

# 十七、Agent Marketplace

支持：

安装。

例如：

```text
Coding Agent

HR Agent

SQL Agent

Research Agent

Investment Agent

Marketing Agent
```

企业：

直接：

用。

---

# 十八、UX（重点）

## 首页

不要：

Prompt。

不要：

Model。

直接：

Agent。

例如：

```text
🤖 Coding Assistant

🤖 HR Assistant

🤖 Investment Assistant
```

---

## Planner

可视化。

例如：

```text
分析需求

↓

拆任务

↓

执行
```

不是：

黑盒。

---

## Tool

实时：

高亮。

例如：

```text
正在：

调用：

Search User
```

用户：

放心。

---

## Approval

危险：

操作：

弹：

确认。

---

## Trace

支持：

一步步：

展开。

像：

LangSmith。

---

# 十九、数据模型设计

建议拆成 **12 张核心表**。

---

## ai_agent

```sql
id

code

name

description

status

owner

created_at

updated_at
```

---

## ai_agent_profile

```sql
id

agent_id

role

goal

style

language

constraint
```

---

## ai_agent_planner

```sql
id

agent_id

planner_type

config_json
```

---

## ai_agent_task

```sql
id

agent_id

name

order_no

status
```

---

## ai_agent_tool

```sql
id

agent_id

tool_id

permission
```

---

## ai_agent_knowledge

```sql
id

agent_id

knowledge_id
```

---

## ai_agent_memory

```sql
id

agent_id

memory_policy
```

---

## ai_agent_execution

```sql
id

agent_id

conversation_id

status

started_at

ended_at
```

每次运行生成一个 Execution，便于恢复、审计和重放。

---

## ai_agent_trace

```sql
id

execution_id

step

action

result

latency
```

记录每一步规划、工具调用、模型调用和结果。

---

## ai_agent_schedule

```sql
id

agent_id

cron

enabled
```

---

## ai_agent_approval

```sql
id

execution_id

approval_type

status

approved_by

approved_at
```

---

## ai_agent_version

```sql
id

agent_id

version

config_json

created_at
```

Agent 配置（Profile、Planner、绑定资源等）统一版本化。

---

# 二十、Agent 运行流程（核心）

建议整个 Runtime 按固定流水线执行：

```text
Goal
    │
    ▼
Planner（生成计划）
    │
    ▼
Task Scheduler（拆分任务）
    │
    ▼
Context Builder
    │
    ├── Conversation
    ├── Memory
    ├── Knowledge
    └── Prompt
    │
    ▼
LLM Reasoning
    │
    ▼
Tool Runtime
    │
    ▼
Gateway Runtime
    │
    ▼
Provider
    │
    ▼
Result Evaluation
    │
    ├── 完成 → 返回结果
    ├── 失败 → Retry / Fallback
    └── 危险操作 → Approval
```

整个执行链路全部可追踪、可暂停、可恢复。

---

# 二十一、需要特别注意的设计

## ① Agent 是 Runtime，不是 Prompt

不要把 Agent 理解成：

```text
Prompt + Tool
```

真正的 Agent 包括：

* Planner
* Task
* Context Builder
* Tool
* Memory
* Knowledge
* Approval
* Retry
* Trace

Prompt 只是其中一个组成部分。

---

## ② Planner 与执行器分离

Planner 负责"决定做什么"；

Executor（Task Runtime）负责"真正执行"。

这样未来可以替换不同的规划算法，而不会影响执行层。

---

## ③ Agent 必须支持人工介入

企业环境中，AI 不应拥有无限权限。

对于：

* 删除数据
* 修改配置
* 转账付款
* 批量发送通知

必须支持：

```text
Agent
    ↓
Approval
    ↓
Human
    ↓
Continue
```

这是企业级 Agent 的关键能力。

---

## ④ Agent 不直接依赖具体模型

Agent 应依赖：

```text
Scene
    ↓
Prompt
    ↓
Gateway
```

而不是：

```text
GPT-5
```

这样模型升级或切换不会影响 Agent。

---

## ⑤ Agent 是整个 Core Platform 的统一执行入口

未来：

* `core-user`
* `core-storage`
* `core-notification`
* `core-payment`
* `core-workflow`

都应该把能力注册为 Tool，由 Agent Runtime 统一编排和调用，而不是各自实现独立的 AI 能力。

---

# P8 在整个 AI Runtime 中的位置

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
Conversation & Memory Runtime
        │
Knowledge Runtime
        │
────────────────────────────────
Agent Runtime ⭐⭐⭐⭐⭐
────────────────────────────────
        │
Analytics Runtime
        │
Enterprise AI Platform
```

**P8 的目标，是让 AI 从"回答问题"升级为"执行任务"。** 从这一阶段开始，`core-ai` 不再只是一个模型调用平台，而是真正成为整个 Core Platform 的智能执行引擎。未来所有业务系统都可以通过注册 Tool、Knowledge 和 Scene，被 Agent 自动组合和执行，形成完整的企业 AI Operating System。
