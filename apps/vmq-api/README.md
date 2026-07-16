# VMQ API

VMQ API 是 VMQ Suite 的 Java 服务端，负责收款订单、二维码、Android 心跳与到账推送、后台认证和商户回调。

## 环境

- JDK 21
- Maven 3.9+
- PostgreSQL 16

## 运行

先创建数据库并设置环境变量：

```bash
export VMQ_ADMIN_PASSWORD='replace-with-a-long-random-password'
export SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/vmq'
export SPRING_DATASOURCE_USERNAME='vmq'
export SPRING_DATASOURCE_PASSWORD='replace-with-database-password'
mvn spring-boot:run
```

默认端口为 `50126`。服务拒绝在未配置 `VMQ_ADMIN_PASSWORD` 时启动。

## 验证

```bash
mvn test
mvn -DskipTests clean package
```

完整接口与部署说明见仓库根目录的 [`docs/vmq-suite`](../../docs/vmq-suite)。

