# Deployment

## Build

```bash
mvn clean verify
```

产物：

```text
target/core-ai-0.1.0-SNAPSHOT.jar
```

## Local

```bash
java -jar target/core-ai-0.1.0-SNAPSHOT.jar
```

## Production

```bash
java -jar target/core-ai-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=mysql,jwt
```

生产必须配置：

- `CORE_AI_MASTER_KEY`
- `CORE_IDENTITY_JWKS_URI`
- MySQL 连接信息
- `SERVER_ADDRESS`

## Rollback

1. 停止服务。
2. 备份当前数据库。
3. 恢复上一版本 JAR。
4. 若迁移不可兼容，恢复升级前数据库备份。
5. 验证 `/actuator/health`、Provider 列表和一次非写入连接测试。
