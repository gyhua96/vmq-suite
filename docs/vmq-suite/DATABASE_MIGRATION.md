# 数据库迁移发布清单

当前项目已升级至 Spring Boot 3.5.16 + Java 21。在此版本下，Flyway 已经可以与 PostgreSQL 16 协同工作，支持自动或手动执行迁移。

## 当前策略

- 迁移 SQL 统一放在 `apps/vmq-api/src/main/resources/db/migration/`。
- 唯一约束前检查脚本放在 `apps/vmq-api/src/main/resources/db/preflight/`。
- `SPRING_FLYWAY_ENABLED` 可以通过根目录 `.env` 控制，默认为 `false`。
- 生产环境默认仍使用 `SPRING_JPA_HIBERNATE_DDL_AUTO=update` 自动更新表结构。

## V1 非破坏索引、事件表和回调任务表

手动执行迁移示例如下：

```bash
psql "$SPRING_DATASOURCE_URL" -U "$SPRING_DATASOURCE_USERNAME" -f apps/vmq-api/src/main/resources/db/migration/V1__baseline_indexes.sql
```

如果使用标准 PostgreSQL 环境变量，也可以写成：

```bash
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql "$SPRING_DATASOURCE_URL" -U "$SPRING_DATASOURCE_USERNAME" -f apps/vmq-api/src/main/resources/db/migration/V1__baseline_indexes.sql
```

该脚本创建普通索引，并新增 `payment_event` 到账事件表用于 `/appPush` 幂等记录和排查，新增 `callback_task` 回调任务表用于记录同步回调结果和后续重试。脚本不会清理历史数据，也不会给既有业务表增加唯一约束。

`callback_task.state` 当前约定：

| 值 | 含义 |
| --- | --- |
| `0` | 初始待处理 |
| `1` | 回调成功 |
| `2` | 等待下次重试 |
| `3` | 达到最大重试次数后的最终失败 |

当前服务端最大重试次数为 10 次。达到上限后任务不再被定时任务扫描，后续可通过后台手动补发重新记录结果。

## 唯一约束前检查

执行：

```bash
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql "$SPRING_DATASOURCE_URL" -U "$SPRING_DATASOURCE_USERNAME" -f apps/vmq-api/src/main/resources/db/preflight/duplicate_order_keys.sql
```

必须确认以下三组查询都没有返回数据：

- `pay_order.order_id` 重复。
- `pay_order.pay_id` 重复。
- `pay_qrcode(type, price)` 重复。

如果存在重复数据，先清洗历史数据，再增加唯一索引。

## 切换 validate 的条件

满足以下条件后，才能把生产环境改为：

```text
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

- V1 索引已执行成功。
- `payment_event` 表已创建成功。
- `callback_task` 表已创建成功。
- 唯一约束前检查为空。
- 后续唯一索引迁移已发布并执行.
- 回滚方案已准备，包括数据库备份和索引删除 SQL。

## 自动 Flyway 迁移策略

当前项目已升级至 Spring Boot 3.5.16，可以在根目录 `.env` 中设置以下配置开启自动迁移：

```env
SPRING_FLYWAY_ENABLED=true
```

开启后，系统在每次部署或重启时将自动检测并运行最新的 Flyway 迁移文件。

## V2/V3 安全加固迁移（当前版本）

当前后端基线为 Spring Boot `3.5.16`、Java `21`、PostgreSQL `16`。升级已有数据库时必须按 `V1 -> V2 -> V3` 顺序执行，且先完成数据库备份。

V2 `V2__harden_money_and_order_keys.sql`：

- 将 `pay_order.price`、`pay_order.really_price`、`pay_qrcode.price`、`payment_event.price` 转换为 `numeric(19,2)`，避免浮点金额误差。
- 为 `pay_order.order_id`、`pay_order.pay_id` 和 `pay_qrcode(type, price)` 增加唯一约束。
- 执行 V2 前必须运行 `db/preflight/duplicate_order_keys.sql`，前三组查询必须返回零行。

V3 `V3__add_nonce_event_and_callback_claim.sql`：

- 新增 `request_nonce`，持久化 HMAC nonce 并按 `scope + nonce` 去重。
- 为 `payment_event` 增加处理状态和匹配订单字段。
- 为 `callback_task` 增加 claim 租约字段，并对 `order_id` 建立唯一约束，避免多实例重复回调。
- 执行 V3 前，预检脚本中的 `callback_task.order_id` 重复查询必须返回零行。若有重复，保留每个订单最新且状态最完整的一条任务，再执行迁移。

迁移后建议设置：

```env
SPRING_FLYWAY_ENABLED=true
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

不要在未完成预检时直接开启自动 Flyway；唯一约束失败会阻止应用启动，但不会替代历史数据清洗。
