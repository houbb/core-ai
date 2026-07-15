经过 P0～P8：

* Provider Runtime：连接 AI
* Model Runtime：管理模型
* Scene Runtime：业务能力抽象
* Prompt Runtime：Prompt 资产
* Tool Runtime：连接世界
* Gateway Runtime：稳定运行
* Conversation & Memory Runtime：上下文与记忆
* Knowledge Runtime：企业知识
* Agent Runtime：自主执行

下一阶段应该建设：

# Phase 9：AI Analytics Runtime ⭐⭐⭐⭐⭐

> AI Observability & Intelligence Platform

## 定位

AI Analytics Runtime 负责：

> **让企业知道 AI 正在做什么、花多少钱、效果怎么样、哪里需要优化。**

没有 Analytics：

AI 系统会变成黑盒：

* 为什么这个回答变差？
* 哪个模型最贵？
* 哪个 Agent 最常失败？
* 哪个 Prompt 效果最好？
* 哪个部门浪费最多 Token？

全部无法回答。

---

# 一、设计目标

一句话：

> **让 AI 从不可控的黑盒，变成可观测、可优化的生产系统。**

核心关注：

```text
Usage

Cost

Performance

Quality

Security

Behavior
```

---

# 二、整体架构

```text
                AI Analytics Runtime

                        │

 ┌──────────────┬──────────────┬──────────────┐
 │              │              │              │
Usage          Cost          Quality        Audit
 │              │              │              │
Request        Token          Score          Trace
 │              │              │              │
Gateway ── Model ── Provider ── Agent ── Tool
```

---

# 三、Analytics 数据来源

统一采集：

## Gateway

产生：

```text
Request

Latency

Error

Provider

Model
```

---

## Model Runtime

产生：

```text
Model

Capability

Token
```

---

## Scene Runtime

产生：

```text
Scene

Business Usage
```

---

## Prompt Runtime

产生：

```text
Prompt Version

Prompt Performance
```

---

## Tool Runtime

产生：

```text
Tool Call

Success

Failure
```

---

## Agent Runtime

产生：

```text
Task

Plan

Execution

Trace
```

---

# 四、Analytics Dashboard

首页：

类似企业监控平台。

---

## AI Overview

展示：

```
Today

Requests

120,000


Success Rate

99.95%


Token

500M


Cost

$320


Avg Latency

1.2s
```

---

# 五、Usage Analytics

统计：

## 请求量

维度：

```text
时间

用户

部门

项目

Scene

Model

Provider
```

例如：

```
Chat

80%

OCR

10%

Agent

10%
```

---

# 六、Cost Analytics ⭐⭐⭐⭐⭐

企业最关注。

统计：

```
Token

Input Token

Output Token

Cache Token

Cost
```

---

支持：

成本排行：

```
TOP Models

GPT5        $500

Claude      $300

DeepSeek    $50
```

---

部门：

```
研发

$200

市场

$100

客服

$80
```

---

用户：

```
User A

$50

User B

$20
```

---

# 七、Budget Management

支持预算。

例如：

## 企业预算

```
AI Budget

$10000/month
```

---

## 部门预算

```
研发

$3000
```

---

## 项目预算

```
Customer Service Bot

$500
```

---

超过：

自动：

```
Warning

↓

Limit

↓

Disable
```

---

# 八、Performance Analytics

关注：

## Latency

例如：

```
GPT5

1.5s


DeepSeek

0.8s
```

---

## Error Rate

例如：

```
429

0.2%

500

0.1%

Timeout

0.05%
```

---

## Availability

例如：

```
OpenAI

99.9%

Claude

99.8%
```

---

# 九、Model Comparison

非常重要。

例如：

```
GPT5 VS Claude VS DeepSeek
```

比较：

```
Cost

Latency

Quality

Success

Token
```

帮助：

自动优化模型选择。

---

# 十、Prompt Analytics

连接 P3。

统计：

不同 Prompt：

效果。

例如：

```
Customer Reply Prompt V1

Success

85%


Customer Reply Prompt V2

Success

93%
```

发现：

最佳版本。

---

# 十一、Scene Analytics

分析：

业务场景。

例如：

```
AI Coding

10,000 requests

Success 98%


AI OCR

5,000 requests

Success 99%
```

---

发现：

哪些 AI 能力：

真正产生价值。

---

# 十二、Agent Analytics ⭐⭐⭐⭐⭐

连接 P8。

统计：

Agent：

```
Executions

Tasks

Tool Calls

Success

Failure
```

---

例如：

Coding Agent：

```
1000 Runs

800 Completed

150 Need Approval

50 Failed
```

---

查看：

失败原因：

```
Tool Error

Knowledge Missing

Model Error

Planning Error
```

---

# 十三、Trace Analytics

核心能力。

展示：

一次完整链路：

```
User Request

↓

Scene

↓

Prompt

↓

Agent

↓

Tool

↓

Knowledge

↓

Model

↓

Provider

↓

Response
```

类似：

APM Trace。

---

# 十四、Quality Analytics

未来非常重要。

因为：

成本低：

不代表：

效果好。

---

支持：

## 用户评分

```
👍

👎
```

---

## AI Judge

自动评分：

例如：

```
Accuracy

Completeness

Relevance

Safety
```

---

## Human Review

人工审核。

---

# 十五、AI Evaluation Runtime

建议内置。

支持：

测试集。

例如：

```
1000 Questions

↓

Run Model

↓

Compare Answer

↓

Score
```

类似：

AI 单元测试。

---

用于：

* Prompt 优化
* 模型选择
* Agent 升级

---

# 十六、异常检测

结合：

Analytics。

例如：

发现：

```
昨天平均成本

↑300%
```

自动：

报警。

---

异常：

```
Latency Increase

Error Increase

Token Explosion

Cost Spike
```

---

# 十七、UX 设计

## 首页

类似：

Grafana + Datadog。

布局：

```
AI Health


Requests
Cost
Latency
Quality


-----------------

Top Models

Top Scenes

Top Agents

-----------------

Alerts
```

---

# 十八、查询体验

不要：

要求 SQL。

提供：

自然语言：

例如：

输入：

```
为什么本周 AI 成本增加？
```

AI Analytics 自己分析：

```
原因：

1. GPT5 使用增加 40%

2. OCR 请求增加 30%

3. 某 Agent Token 异常
```

形成：

AI 管理 AI。

---

# 十九、数据模型设计

建议：

Analytics 不直接存业务数据。

采用：

Event Model。

---

## ai_usage_event

调用事件。

```sql
id

request_id

user_id

scene_id

model_id

provider_id

input_tokens

output_tokens

cost

latency

status

created_at
```

---

## ai_cost_record

成本。

```sql
id

resource_type

resource_id

token

price

amount

time
```

---

## ai_metric

指标。

```sql
id

metric_name

value

dimension

time
```

---

## ai_trace

链路。

```sql
id

trace_id

parent_id

span

duration

status
```

---

## ai_evaluation

评估。

```sql
id

target

score

judge

created_at
```

---

## ai_feedback

反馈。

```sql
id

conversation_id

message_id

rating

comment
```

---

## ai_alert_rule

报警规则。

```sql
id

metric

condition

threshold

action
```

---

# 二十、注意点

## ① Analytics 必须事件驱动

不要：

每个模块自己统计。

应该：

统一产生：

```text
AI Event
```

例如：

```
ModelCalled

ToolExecuted

AgentFinished

PromptRendered
```

然后：

Analytics 消费。

---

## ② 成本必须从第一天记录

不要：

以后补。

因为：

历史 Token 无法恢复。

---

## ③ Quality 比 Cost 更重要

企业最终优化目标：

不是：

最低成本。

而是：

```
价值 / 成本
```

---

## ④ Trace 是核心资产

未来：

AI 出问题：

不是查日志。

而是：

查完整链路。

---

## ⑤ Analytics 本身也应该 AI 化

未来：

管理员不应该每天看 Dashboard。

应该：

AI 主动告诉你：

```
昨天 AI 成本上涨 20%

原因：

客服 Agent 调用了 GPT5

建议：

切换 DeepSeek
```

---

# P9 在整个 core-ai 中的位置

```
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
Conversation Runtime
        │
Knowledge Runtime
        │
Agent Runtime
        │
────────────────────────
AI Analytics Runtime ⭐⭐⭐⭐⭐
────────────────────────
        │
Enterprise AI Platform
```

---

**P9 的目标，是建立 AI 平台的“控制中心”。**

前面的 Runtime 负责让 AI **运行起来**。

P9 负责让 AI：

* 看得见
* 管得住
* 优化得了
* 降低成本
* 提升质量

到这里，`core-ai` 基本形成一个完整的企业级 AI Operating System 雏形。
