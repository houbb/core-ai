我建议这里再次调整路线。

**Knowledge Runtime 不应该只是 RAG。**

目前很多 AI 平台把 Knowledge Runtime 理解成：

> PDF → Chunk → Embedding → Vector

这已经落后了。

未来企业知识不仅包括：

* PDF
* Word
* Markdown

还包括：

* Git 仓库
* 数据库
* API
* Wiki
* Notion
* Jira
* Confluence
* 对话记录
* Tool
* Memory

所以 **P7 应该升级为 AI Knowledge Runtime（企业知识运行时）**。

它不仅负责 RAG，而是整个企业知识中枢。

---

# Phase 7：AI Knowledge Runtime ⭐⭐⭐⭐⭐

> Enterprise Knowledge Operating System

## 定位

Knowledge Runtime 负责：

> **统一管理企业所有可供 AI 理解和检索的知识。**

以后：

所有：

```text
Chat

Agent

Workflow

Search

Assistant

Customer Service

Copilot
```

全部：

依赖：

Knowledge Runtime。

---

# 一、设计目标

一句话：

> **让企业所有知识，都可以被 AI 使用。**

例如：

企业：

有：

```text
PDF

Word

Excel

Markdown

HTML

Image

Git

Wiki

SQL

API

FAQ

Manual

Meeting

Email

Conversation

Memory
```

全部：

统一。

形成：

Knowledge。

---

# 二、整体架构

```text
Document

↓

Parser

↓

Chunk

↓

Embedding

↓

Indexer

↓

Retriever

↓

Reranker

↓

Knowledge

↓

Scene / Agent
```

不是：

只有：

Vector。

而是：

完整：

Pipeline。

---

# 三、Knowledge 生命周期

建议：

```text
Imported

↓

Parsing

↓

Embedding

↓

Indexed

↓

Published

↓

Archived
```

支持：

重新：

Embedding。

重新：

Index。

---

# 四、Knowledge 管理界面

推荐：

```text
┌─────────────────────────────────────────────┐
│ Knowledge                                  │
├──────────────┬──────────────────────────────┤
│ Product Doc  │                              │
│ API          │                              │
│ HR           │      Knowledge Detail        │
│ Git          │                              │
│ FAQ          │                              │
└──────────────┴──────────────────────────────┘
```

左：

知识库。

右：

详情。

---

# 五、Knowledge 分类

建议：

不要：

只有：

Document。

建议：

```text
Document

Wiki

Git

Database

Website

Conversation

Memory

Tool

API

Workflow

FAQ
```

以后：

插件：

继续：

增加。

---

# 六、Knowledge Source

例如：

支持：

```text
Upload File

Directory

Git Repository

Notion

Confluence

Jira

MySQL

PostgreSQL

SQLite

OpenSearch

Website

REST API
```

未来：

统一。

---

# 七、Document Parser

建议：

内置：

Parser。

例如：

支持：

```text
PDF

DOCX

XLSX

PPTX

TXT

Markdown

CSV

JSON

XML

HTML
```

图片：

支持：

```text
OCR
```

代码：

支持：

```text
Java

Rust

Go

Python

Vue

React
```

自动：

解析。

---

# 八、Chunk Runtime（核心）

建议：

Chunk：

不要：

固定。

支持：

```text
By Paragraph

By Heading

By Token

By Page

By Section

Semantic Chunk
```

以后：

企业：

选择。

---

Chunk：

支持：

Overlap。

例如：

```text
Chunk

512

Overlap

128
```

全部：

配置。

---

# 九、Embedding

绑定：

Model。

例如：

```text
BGE

OpenAI

Jina

Nomic

Qwen Embedding
```

以后：

统一：

切换。

---

支持：

重新：

Embedding。

---

# 十、Index Runtime

建议：

支持：

多个：

Index。

例如：

```text
Vector

Keyword

Hybrid

Graph
```

不要：

只有：

Vector。

未来：

Graph RAG。

---

# 十一、Retriever

支持：

策略。

例如：

```text
Top K

Score

MMR

Hybrid

Metadata Filter

Time Weight
```

以后：

直接：

配置。

---

# 十二、Reranker

建议：

支持：

```text
BGE

Jina

Cross Encoder
```

统一：

切换。

---

# 十三、Metadata

每个：

Chunk：

建议：

支持：

```text
Title

Author

Department

Tag

Language

Created Time

Updated Time

Permission

Source
```

以后：

过滤：

非常：

方便。

---

# 十四、Knowledge Search

支持：

全文。

例如：

```text
SpringBoot
```

同时：

支持：

```text
Metadata Filter

Department

Tag

Language
```

未来：

企业：

必须。

---

# 十五、Knowledge Playground

建议：

右侧：

Playground。

例如：

```text
Question

↓

Retriever

↓

Chunk

↓

Prompt

↓

Answer
```

全部：

展示。

以后：

调：

RAG。

神器。

---

# 十六、Knowledge Trace

建议：

展示：

```text
Question

↓

Retriever

↓

Chunk

↓

Rerank

↓

Prompt

↓

LLM

↓

Answer
```

以后：

AI：

为什么：

这么：

回答。

全部：

知道。

---

# 十七、Knowledge Version

建议：

支持：

```text
V1

V2

V3
```

重新：

Index。

支持：

Rollback。

---

# 十八、知识权限（企业重点）

支持：

```text
Public

Department

Project

Private
```

例如：

```text
HR

工资制度
```

研发：

不能：

搜索。

---

# 十九、UX（重点）

## 导入

支持：

拖拽：

整个：

目录。

例如：

```text
docs/
```

自动：

解析。

---

## Index

展示：

进度。

例如：

```text
Parsing

██████

60%
```

用户：

知道：

没：

卡死。

---

## Search

搜索：

结果：

展示：

```text
Chunk

Score

Source

Highlight
```

不要：

只有：

答案。

---

## Chunk

点击：

展开。

查看：

原文。

---

## 引用

回答：

支持：

```text
Source

Page

Chunk
```

企业：

可信。

---

# 二十、数据模型设计

建议拆成 **12 张核心表**。

---

## ai_knowledge

知识库。

```sql
id

name

description

category

status

created_at

updated_at
```

---

## ai_knowledge_source

来源。

```sql
id

knowledge_id

type

config_json
```

例如：

Git。

Notion。

MySQL。

---

## ai_document

文档。

```sql
id

knowledge_id

title

path

size

language

status
```

---

## ai_chunk

Chunk。

```sql
id

document_id

chunk_no

content

token_count

metadata_json
```

---

## ai_embedding

Embedding。

```sql
id

chunk_id

model

vector_id

version
```

说明：向量本身建议交由向量数据库（如 pgvector、Milvus、OpenSearch、Qdrant）管理，这里保存引用信息，而不是直接存储高维向量。

---

## ai_index

索引。

```sql
id

knowledge_id

type

status
```

---

## ai_retriever_policy

检索。

```sql
id

knowledge_id

top_k

strategy

score_threshold
```

---

## ai_reranker

重排。

```sql
id

knowledge_id

model

enabled
```

---

## ai_knowledge_permission

权限。

```sql
id

knowledge_id

role

department
```

---

## ai_knowledge_version

版本。

```sql
id

knowledge_id

version

created_at
```

---

## ai_search_log

搜索。

```sql
id

question

knowledge_id

latency

created_at
```

---

## ai_reference

引用。

```sql
id

answer_id

chunk_id

score
```

---

# 二十一、注意点

## ① Knowledge ≠ Document

不要：

认为：

Knowledge：

就是：

PDF。

真正：

Knowledge：

包括：

代码。

数据库。

API。

Memory。

Conversation。

Tool。

全部。

---

## ② Chunk 不应该固定大小

不同：

文档。

不同：

Chunk。

建议：

策略。

配置。

---

## ③ Retrieval 是可插拔的

以后：

可以：

Vector。

Keyword。

Hybrid。

Graph。

不要：

写死。

---

## ④ 知识权限必须贯穿整个链路

权限不仅是文档级，还应该支持：

* 文档级（Document）
* Chunk 级（Chunk）
* Metadata 级（Department、Project）
* 用户上下文级（当前用户可访问的数据）

Retriever 返回结果前必须先经过权限过滤，而不是检索后再过滤，否则容易造成信息泄露。

---

## ⑤ Knowledge 与 Memory 分工明确

不要混淆：

**Knowledge** 是企业共享知识：

```text
产品文档
开发规范
制度
FAQ
API
```

而 **Memory** 是 AI 在 P6 中积累的个体或组织记忆：

```text
用户偏好
历史任务
长期上下文
```

两者可以共同参与 Context Builder，但生命周期和治理方式完全不同。

---

# P7 在整个 AI Runtime 中的位置

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
────────────────────────────────
Knowledge Runtime ⭐⭐⭐⭐⭐
────────────────────────────────
        │
Agent Runtime
        │
Analytics Runtime
        │
Enterprise AI Platform
```

**P7 的目标，是建立企业统一的 Knowledge OS。** 它不仅提供传统 RAG 能力，更承担企业知识的采集、解析、索引、检索、权限、版本和可追溯管理，让 AI 能够在正确的权限范围内，基于可信知识进行回答和决策。这也是后续 Agent Runtime 和企业 AI 平台最重要的知识底座。
