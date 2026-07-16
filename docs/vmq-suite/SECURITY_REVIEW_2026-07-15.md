# VMQ Open Source 安全与代码审查报告

审查日期：2026-07-15  
范围：`vmq-open-source` 中可发布文件、Spring Boot API、Next.js 管理端、Android 监听端、部署文件和直接/锁定依赖。  
结论：未发现已提交的真实密钥或个人信息，但存在多个会导致资金事件丢失、服务不可用、越权查询或已知组件漏洞的问题。修复“发布阻断”项前，不建议连接真实资金业务或公开部署。

## 1. 敏感信息检查

### 1.1 结果

- 扫描了 262 个拟发布文件，没有发现真实账号密码、API Token、私钥、证书、keystore、生产域名、个人公网 IP 或真实支付二维码。
- `.env.example` 使用占位配置；仓库未包含 `.env`。
- Android 发布签名文件未包含在仓库中。
- 对名称可能与支付相关的图片做了人工检查，内容为 Logo、界面素材或示例资源，不是个人收款码。
- 当前 Git 仓库没有 commit 和 remote，因而没有可供检查的历史提交；全部源文件仍是 untracked。首次提交前仍需再运行一次 secret scan。

### 1.2 不能视为敏感信息证明的边界

扫描只能证明当前工作树未命中已知模式，不能证明未来提交、构建产物、Issue、CI 日志或运行数据库没有秘密。发布前应启用 Gitleaks/TruffleHog 类预提交和 CI 检查，并对管理员密码、数据库密码、通讯密钥、release keystore 使用外部 Secret 管理。

## 2. 发布阻断问题

### [严重] Android 把 HTTP 2xx 业务失败当作上报成功

证据：`apps/vmq-android/app/src/main/java/com/vone/vmq/PaymentPushWorker.java:88-100`。

Worker 只检查 `response.isSuccessful()`，没有解析 `CommonRes.code`。`/appPush` 对“重复推送”“订单已被处理”“回调失败”等情况通常返回 HTTP 200 + `code != 1`。调用返回后队列项会被删除，到账事件可能永久丢失。

修复要求：解析 JSON，仅在 HTTP 2xx 且 `code == 1` 时确认队列项；为可重试错误与永久业务错误建立明确错误码和测试。

### [严重] 金额分配可因数据库故障无限循环

证据：`apps/vmq-api/src/main/java/com/vone/mq/service/OrderApplicationService.java:58-80`。

`tryLock` 的所有异常都被等同为“金额已占用”。递增模式没有尝试次数或金额上界，数据库持续异常时请求线程不会结束，并持续调用数据库，最终可能拖垮服务。

修复要求：只处理唯一冲突异常；其他异常立即失败并记录；设置最大尝试次数和可接受偏移上限。

### [严重] 未配置收款码时金额锁泄漏

证据：`OrderApplicationService.java:28` 已成功申请金额；`OrderApplicationService.java:43-45` 直接失败返回，没有调用 `PriceLockService.release`。

反复创建订单会持续占用候选金额，最终触发不断偏移或上述无限循环。

修复要求：把锁的生命周期放入 `try/finally` 或在取得二维码后再申请锁，并补充失败路径测试。

### [严重] 商户订单号和 VMQ 订单号缺少数据库唯一约束

证据：`PaymentOrderCreationService.java:22-43` 采用 find-then-save；`V1__baseline_indexes.sql:6-8` 对 `order_id`、`pay_id` 仅创建普通索引。

并发请求可同时通过存在性检查并写入重复 `payId`。`orderId` 是秒级时间加 4 位伪随机数（`OrderApplicationService.java:83-85`），同秒并发也可能碰撞。

修复要求：清理历史重复数据后，对 `pay_id`、`order_id` 建唯一索引；保存时捕获唯一冲突并返回确定的幂等结果；订单 ID 使用安全随机或数据库生成方案。

### [严重] 订单查询接口可绕过签名

证据：`apps/vmq-api/src/main/java/com/vone/mq/service/WebService.java:158-176`。

`/getOrder` 和 `/checkOrder` 只有在 `sign != null` 时才验签。攻击者可省略签名查询订单信息或取得支付后跳转地址。订单号结构可预测，扩大了枚举风险。

修复要求：两个接口强制签名；商户查询最好同时绑定 `payId`/租户标识；加入请求速率限制和安全审计日志。

### [严重] 依赖包含大量已公开漏洞

2026-07-15 使用 OSV Scanner 2.4.0 检查后端 CycloneDX BOM和前端锁文件：后端命中 11 个包、49 条 advisory，其中 6 条 critical、18 条 high。重点包括 Tomcat `10.1.34`、Spring Boot `3.4.1`/Spring `6.2.1`、Jackson `2.18.2`、Logback `1.5.12` 和 PostgreSQL JDBC `42.7.7`。前端命中 Next.js `16.1.6` 和锁定的 PostCSS `8.4.31`。

OSV 在检查日给出的至少修复线包括 Tomcat `10.1.55`、Next.js `16.2.6`、PostCSS `8.5.10`、PostgreSQL JDBC `42.7.11`；升级时必须以最新 Spring Boot BOM和当日 advisory 为准，并做完整回归，不能只覆盖单个传递依赖。

Android `build.gradle` 列出的 10 个直接 Maven 依赖经 OSV Batch API 查询未命中已知漏洞；该结果不覆盖完整传递依赖和仓库内复制的旧 ZXing 源码。

## 3. 高风险问题

### 到账事件先提交去重记录，后续失败无法可靠重试

`PaymentEventService.recordIfNew` 先在独立事务保存事件（`PaymentEventService.java:16-34`），随后才匹配订单、改状态和创建回调任务（`AppEventService.java:36-75`）。后续任一步异常时，Android 重发会被视为重复，事件可能停留在未完成状态。

建议用一个事务/Outbox 状态机记录 `RECEIVED -> MATCHED -> CALLBACK_QUEUED`，重复请求应恢复未完成流程，而不是简单拒绝。

### 回调任务多实例并发重复执行

`CallbackTaskService.java:58-65` 查询到期任务后逐条执行，DAO 查询没有 `FOR UPDATE SKIP LOCKED`、租约或原子 claim。多个 API 实例会同时发送同一回调。

商户端必须以 `payId` 幂等；服务端应增加 claim 状态、版本号和超时租约。

### HMAC nonce 不防重放，回调本身也无时效字段

`SignatureService` 校验时间窗口和 HMAC，但没有持久化 nonce。商户请求可在 5 分钟窗口内重放，Android HMAC 可在 60 秒内重放。回调 payload 没有 timestamp/nonce，只能依靠 `payId` 幂等。

建议持久化调用方 + nonce 并设置 TTL；回调增加版本化协议中的 timestamp、eventId，保留旧协议兼容。

### Android 默认允许明文通信且会自动降级 HTTP

`AndroidManifest.xml:50` 设置 `usesCleartextTraffic=true`；`ProtocolUtil.java:22-42` 对非 443 端口自动选择 HTTP。通讯密钥、签名请求和到账信息可被窃听或篡改。

建议生产 flavor 禁止明文，要求显式 `https://` URL，并通过 Network Security Config 仅为本地开发单独放行。

### 加密偏好不可用时回退明文存储

`SecurePrefs.java:32-47` 在 Android Keystore/EncryptedSharedPreferences 异常时回退到普通私有 SharedPreferences，通讯密钥会以明文落盘。

建议 fail closed：提示设备不支持安全存储并停止监听；不要静默降低安全等级。

### 管理员认证缺少完整会话生命周期保护

- 前端退出只删除 `localStorage` 令牌（`apps/vmq-web/app/(admin)/layout.tsx:25-28`），没有服务端注销和 Session invalidation。
- 登录没有频率限制、失败锁定或渐进延迟。
- `/admin/getSettings` 会向已登录 Session 返回明文通讯密钥，增加 XSS/会话劫持后的影响面。

建议增加 `POST /logout`、登录限流、Session 固定攻击防护、合理过期时间和密钥仅写不读展示策略。

## 4. 中风险和工程风险

- `AdminPageSupport.java:14-17` 只保证 `limit >= 1`，没有最大值，可造成大查询和内存压力。
- URL 安全校验能阻止常见内网/环回目标且禁用跳转，但解析校验与真正连接之间仍有 DNS rebinding/TOCTOU 窗口；HTTP 客户端应对最终解析 IP 再校验。
- 旧支付页从 `lib.baomitu.com` 加载旧 jQuery/Vue，存在外部供应链、可用性和 EOL 风险；应改为本地锁定依赖并配置 CSP/SRI。
- 金额在 Java 和 PostgreSQL 中使用 `double/float8`，不符合资金精度要求；应迁移为最小货币单位整数或 `BigDecimal + numeric`。
- `/admin/getOrders`、`/admin/getPayQrcodes` 等只读接口仍由宽泛 `@RequestMapping` 暴露多种方法，扩大 CSRF 和行为歧义；应显式声明 HTTP 方法。
- `/enQrcode` 无需登录且没有输入长度/频率限制，可被用于消耗 CPU/内存。
- Web ESLint 报告 42 条 warning（0 error），主要是 effect 内同步 setState、缺失 Hook 依赖、`any`、未使用变量和未优化图片。缺失依赖可能导致闭包读取旧状态，应在发布前逐项处理，不能简单关闭规则。

## 5. 已验证项目

| 项目 | 结果 |
| --- | --- |
| API 单元/集成测试 | 210 passed |
| Android `testDebugUnitTest` | passed |
| Android `assembleDebug` / `assembleRelease` | passed |
| Android 直接依赖 OSV 查询 | 10/10 未命中已知漏洞 |
| Web production build | passed，Next.js 生成 10 个静态/动态路由 |
| Web ESLint | 0 errors，42 warnings |
| 敏感模式和二进制资产检查 | 未发现真实秘密或个人支付素材 |

测试通过不能覆盖本文中的并发、故障恢复、鉴权缺失和依赖漏洞。建议新增：并发重复下单、数据库故障金额分配、无二维码锁释放、HTTP 200 业务失败队列保留、多实例 callback claim、完整到账状态机恢复、强制订单查询签名。

## 6. 建议修复顺序

1. 修复 Android 业务响应确认、金额锁/无限循环、强制查询签名和数据库唯一约束。
2. 升级后端与前端依赖并重新运行 OSV、测试和构建。
3. 把到账事件、订单匹配和回调入队改造成可恢复的事务状态机。
4. 加固 Android HTTPS/安全存储、管理登录/注销和回调并发 claim。
5. 将金额迁移为精确类型，收紧分页、HTTP 方法、二维码工具和旧前端供应链。

## 7. 2026-07-16 修复复核结果

本轮已在 `vmq-open-source` 工作树完成清单 A/B/C/D/E 中除明确残余项外的修复：

- 订单金额已统一为 `BigDecimal`/`numeric(19,2)`，金额锁、唯一约束、UUID 订单号和失败释放路径已覆盖。
- 到账事件已具备 `RECEIVED/PROCESSED` 可恢复状态；HMAC nonce 持久化去重；回调任务使用数据库 claim 租约。
- 订单查询改为订单访问令牌或 HMAC 签名；后台加入注销、登录失败限流、Session ID 轮换、CSRF、密钥脱敏和分页上限。
- 回调 payload 新增 `eventId/timestamp/nonce`，HTTP 客户端对 HTTPS、公网解析和重定向执行 SSRF 防护。
- Android release 禁止明文流量，Keystore 失败时 fail closed，Worker 只在 HTTP 2xx + `code=1` 删除队列项。
- legacy 支付页已移除外部 CDN；Nginx 已增加安全响应头；CI 已加入 Gitleaks/OSV 配置。
- 数据库迁移、接口对接、Android 和运维文档已同步到加固后契约。

复核命令结果：后端 Maven 全量测试 210 项通过；Android `testDebugUnitTest`、`assembleDebug` 与 `assembleRelease` 通过；Web production build 通过；Web ESLint 为 0 errors、42 warnings。

### 当前残余风险

1. Web ESLint 的 42 条 warning 尚未清零，主要为 React effect 依赖、显式 `any` 和图片优化建议，不影响构建但应在后续迭代处理。
2. 本机未安装 `osv-scanner`/`gitleaks` CLI；CI 工作流已配置扫描，首次推送后必须查看扫描结果并处理 high/critical advisory。
3. 生产部署仍需执行 V2/V3 迁移前重复数据预检，并将 `SPRING_JPA_HIBERNATE_DDL_AUTO` 设置为 `validate`。
