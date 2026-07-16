# VMQ Suite 运维与安全建议

本文档整理 VMQ Suite 的上线检查、备份、日志和安全注意事项。

## 1. 上线前检查

### 后台服务

- [ ] 使用 PostgreSQL 16，不再使用 H2 文件数据库。
- [ ] `.env` 中数据库密码已修改，不使用示例密码。
- [ ] 已通过 `VMQ_ADMIN_PASSWORD` 配置强后台密码，且未使用默认后台密码。
- [ ] 后台只通过 HTTPS 域名暴露给 Android 端和业务系统。
- [ ] 管理后台建议限制访问来源 IP 或放在内网/VPN。
- [ ] 已配置定期数据库备份。

### Android 监听端

- [ ] 通知使用权已授权。
- [ ] VMQ、微信、支付宝通知权限已开启。
- [ ] 电池优化已关闭。
- [ ] 后台已锁定。
- [ ] 自启动已允许。
- [ ] 应用内“检测心跳”和“检测监听”均正常。

## 2. 日志查看

### Docker 日志

```bash
docker compose logs -f vmq-api
docker compose logs -f db
```

### Android 应用内日志

在 App 主界面点击：

```text
查看运行日志
```

可复制后用于排障。

### ADB 日志

```bash
adb logcat -s VmqNotification:D MainActivity:D BootReceiver:D PaymentAccessibilityService:D
```

## 3. 数据备份

### Docker Compose 备份

```bash
source .env
docker compose exec db pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > "vmq-$(date +%F).sql"
```

### 恢复

```bash
source .env
cat vmq-2026-06-29.sql | docker compose exec -T db psql -U "$POSTGRES_USER" "$POSTGRES_DB"
```

### Volume 级备份

也可以备份 Docker volume：

```text
postgres_data
```

但推荐优先使用 `pg_dump`，更易跨版本恢复。

## 4. 升级流程

1. 备份 PostgreSQL。
2. 拉取最新代码。
3. 检查 `.env.example` 是否新增变量，并同步到 `.env`。
4. 重新构建并启动：

```bash
docker compose up -d --build
```

5. 查看日志确认启动成功：

```bash
docker compose logs -f vmq-api
```

6. Android 端点击“检测心跳”。

## 5. 安全边界

VMQ 是个人收款监听方案，不等价于官方支付接口。

主要安全点：

- 通知监听权限敏感，只在可信设备上安装。
- App 与后台通信建议使用 HTTPS。
- 旧版签名算法为 MD5 拼接，建议额外依赖 HTTPS 和访问控制。
- `notifyUrl` 为业务系统回调地址，应限制可信域名。
- 后台密码必须通过 `VMQ_ADMIN_PASSWORD` 初始化，系统拒绝默认密码启动。
- 数据库端口不要直接暴露公网。

## 6. 故障定位顺序

### 支付后业务系统没收到回调

1. Android 应用内日志是否识别到收款通知。
2. Android 推送后台是否返回 `success`。
3. VMQ Server 日志是否匹配到订单。
4. VMQ Server 是否调用了业务系统 `notifyUrl`。
5. 业务系统是否返回纯文本 `success`。

### 心跳失败

1. 手机能否访问后台域名。
2. 后台服务是否启动。
3. 后台地址是否带正确协议和端口。
4. 通讯密钥是否一致。
5. 反向代理是否转发到正确端口。

### 数据库连接失败

1. `docker compose ps` 查看 `db` 是否健康。
2. `.env` 密码是否一致。
3. `SPRING_DATASOURCE_URL` 是否指向 `db:5432`（Compose 内）或正确主机（裸机）。
4. PostgreSQL 用户是否为数据库 owner。

## 7. 生产部署建议

- 使用 HTTPS 反向代理。
- 后台管理路径尽量限制访问。
- PostgreSQL 仅内网访问。
- 定期备份数据库。
- 手机保持充电和稳定网络。
- 使用专用微信/支付宝账号和专用 Android 设备。

## 8. 加固版本上线检查

- [ ] 已运行 `db/preflight/duplicate_order_keys.sql`，包括 `callback_task.order_id` 在内的四组查询均为零行。
- [ ] 已按顺序执行 V1、V2、V3，并将 JPA schema 模式切换为 `validate`。
- [ ] `SESSION_COOKIE_SECURE=true`，外部入口只提供 HTTPS。
- [ ] Android 生产配置为 HTTPS，未通过 debug 明文规则连接生产。
- [ ] 商户已支持回调 `eventId/timestamp/nonce` 验签和 nonce 重放防护。
- [ ] 监控了回调 `CLAIMED` 租约超时、最终失败任务和未匹配到账事件。
- [ ] CI 已执行后端测试、Android 测试/构建、Web lint/build、依赖漏洞扫描和 secret scan。
