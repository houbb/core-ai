# Development

## Backend

```bash
mvn spring-boot:run
```

## Frontend

```bash
cd web
npm ci
npm run dev
```

Vite 将 `/api` 代理到 `http://127.0.0.1:8104`。

## Verification

阶段全部功能完成后统一执行：

```bash
mvn clean verify
```

不得在编译、JUnit5、端到端或前端构建失败时宣告阶段完成。
