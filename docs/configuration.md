# Configuration

## Profiles

- `sqlite`：默认数据库。
- `mysql`：MySQL 数据库。
- `local`：仅本机访问，不依赖外部身份服务。
- `jwt`：生产 JWT/JWKS 验证。

## Environment

| Variable | Purpose | Default |
|---|---|---|
| `SERVER_PORT` | 服务端口 | `8104` |
| `SERVER_ADDRESS` | 监听地址 | `127.0.0.1` |
| `CORE_AI_DB_PATH` | SQLite 文件 | `./data/core-ai.db` |
| `CORE_AI_MASTER_KEY` | AES-256 Base64 主密钥 | 仅 local 有开发值 |
| `CORE_IDENTITY_JWKS_URI` | JWT 公钥地址 | jwt 模式必填 |
| `CORE_AI_MYSQL_URL` | MySQL JDBC URL | 本地 MySQL |
| `CORE_AI_MYSQL_USERNAME` | MySQL 用户名 | `core_ai` |
| `CORE_AI_MYSQL_PASSWORD` | MySQL 密码 | 空 |

## Runtime defaults

- Gateway Provider、HTTP/MCP Tool、外部 Knowledge Source 和 LLM Planner 未配置时返回
  `PREVIEW`，不会阻塞本地核心能力。
- `core.agent.schedule-delay-ms` 控制 Agent 到期计划扫描间隔，默认 `30000`。
- Prompt、Tool、Context 和 Search 日志默认只保存脱敏内容、摘要或哈希。
