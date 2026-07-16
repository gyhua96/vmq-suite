# VMQ Suite 部署指南

本文档对应独立仓库中的 Spring Boot 3.5.16 API、Next.js 管理端、PostgreSQL 16 和 Nginx 网关。

## Docker Compose

```bash
cp .env.example .env
```

修改以下占位值：

```env
VMQ_ADMIN_PASSWORD=replace_with_a_long_random_admin_password
POSTGRES_PASSWORD=replace_with_a_long_random_database_password
```

启动：

```bash
docker compose up -d --build
docker compose ps
```

默认访问地址：

- 管理端：`http://localhost:8080/vmq/`
- API 网关前缀：`http://localhost:8080/vmq-api/`
- Android 与旧商户兼容接口：`http://localhost:8080/appHeart`、`/appPush`、`/createOrder` 等根路径

修改公开端口：

```env
VMQ_HTTP_PORT=18080
```

查看日志：

```bash
docker compose logs -f vmq-api vmq-web gateway
```

停止并保留数据：

```bash
docker compose down
```

删除数据库卷会永久清空数据：

```bash
docker compose down -v
```

## 网络与 HTTPS

Compose 默认只公开 Nginx 网关，PostgreSQL、API 和 Web 容器不直接映射宿主机端口。正式部署应再使用 Caddy、Nginx、Traefik 或云负载均衡终止 HTTPS，并将请求转发到 `VMQ_HTTP_PORT`。

Android 后台地址和商户 `notifyUrl` 应使用可验证证书的 HTTPS 域名。数据库端口不得暴露公网。

## 本地 API

创建 PostgreSQL 用户和数据库：

```sql
CREATE USER vmq WITH PASSWORD 'replace_with_database_password';
CREATE DATABASE vmq OWNER vmq;
```

启动：

```bash
cd apps/vmq-api
export VMQ_ADMIN_PASSWORD='replace-with-a-long-random-password'
export SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/vmq'
export SPRING_DATASOURCE_USERNAME='vmq'
export SPRING_DATASOURCE_PASSWORD='replace_with_database_password'
mvn spring-boot:run
```

默认 API 端口为 `50126`。

## 本地 Web

```bash
pnpm install
pnpm dev:web
```

Web 默认位于 `http://localhost:3080/vmq/`，并将 `/vmq-api/*` 转发到 `http://localhost:50126/*`。

## 数据库策略

首次评估部署默认使用：

```env
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_FLYWAY_ENABLED=false
```

生产环境应先备份并审查迁移脚本，再根据团队的 DDL 管理方式切换为 `validate` 或启用 Flyway。详细说明见 [DATABASE_MIGRATION.md](DATABASE_MIGRATION.md)。

## 备份

```bash
docker compose exec -T db pg_dump -U vmq vmq > vmq-backup.sql
```

恢复前请停止写入并确认目标数据库为空或已完成备份：

```bash
docker compose exec -T db psql -U vmq vmq < vmq-backup.sql
```
