# VMQ Open Source 问题修复清单

基线：`SECURITY_REVIEW_2026-07-15.md`  
创建日期：2026-07-16  
状态：已完成（保留项见 D6、E4）

## A. 资金与订单主链路

- [x] A1 Android 解析 `/appPush` 的 `CommonRes`，仅在 HTTP 2xx 且 `code=1` 时确认队列事件。
- [x] A2 为 Android 业务失败定义可重试/不可重试判定并补齐 Worker 单元测试。
- [x] A3 金额分配只把唯一冲突视为占用，数据库异常立即失败。
- [x] A4 金额分配增加最大尝试次数和最大偏移边界。
- [x] A5 所有订单创建失败路径可靠释放金额锁。
- [x] A6 `pay_id`、`order_id` 建立数据库唯一约束，并提供上线前重复数据检查。
- [x] A7 订单 ID 改为不可预测的高熵标识，唯一冲突返回确定结果。
- [x] A8 金额从 `double/float8` 迁移为 `BigDecimal/numeric(19,2)`，签名和 JSON 字段保持兼容。

## B. 到账、签名与回调可靠性

- [x] B1 到账事件实现可恢复状态机，重复请求继续未完成流程而不是直接拒绝。
- [x] B2 到账事件、订单标记和回调入队的本地状态变更具备事务一致性。
- [x] B3 HMAC nonce 持久化并在有效窗口内拒绝重放，定时清理过期 nonce。
- [x] B4 回调增加 `timestamp`、`nonce`、`eventId`，商户可验时效并按事件幂等。
- [x] B5 回调任务采用数据库原子 claim/租约，多实例不会同时发送同一任务。
- [x] B6 回调 HTTP 客户端对实际连接 IP 复验，阻断 DNS rebinding/TOCTOU 和重定向。

## C. API 与管理安全

- [x] C1 `/getOrder`、`/checkOrder` 强制订单访问令牌或 HMAC 签名，并拒绝完全匿名查询。
- [x] C2 增加服务端 `/logout`，注销 Session；前端退出调用该接口。
- [x] C3 登录加入按 IP+账号的失败限流、短时锁定和成功清零。
- [x] C4 Session 登录后更换 ID，配置 Cookie 安全属性和合理过期时间。
- [x] C5 `/admin/getSettings` 不再返回明文通讯密钥。
- [x] C6 后台分页 `limit` 限制为 1..100。
- [x] C7 所有 Controller 使用明确 HTTP 方法，保留必要的旧接口兼容方法。
- [x] C8 二维码解码统一受 Session+CSRF 保护；公开 `/enQrcode` 保留为收银页资源并限制输入大小。

## D. Android、前端与供应链

- [x] D1 Android release 禁止明文流量；仅 debug Network Security Config 放行本地 HTTP。
- [x] D2 Android 不再根据非 443 端口自动降级 HTTP，生产配置必须显式 HTTPS。
- [x] D3 `EncryptedSharedPreferences` 不可用时 fail closed，不落盘明文通讯密钥。
- [x] D4 升级 Spring Boot、Tomcat/BOM、PostgreSQL JDBC、ZXing、Next.js、PostCSS 等漏洞依赖。
- [x] D5 旧支付页移除外部 CDN 的旧 jQuery/Vue，改为无外部运行时依赖并配置 CSP。
- [ ] D6 修复全部 Web ESLint warning，不通过关闭规则规避。
- [x] D7 CI 增加 secret scan、依赖漏洞扫描和构建产物检查。

## E. 验收与文档

- [x] E1 API 全量测试通过，并覆盖异常、重放、状态机、claim、鉴权测试。
- [x] E2 Android 单元测试和 debug/release 构建通过。
- [ ] E3 Web lint 0 error/0 warning，production build 通过（当前 0 error/42 warnings）。
- [ ] E4 OSV 复扫无已知 critical/high；残余项有明确评估（本机未安装 OSV Scanner）。
- [x] E5 敏感信息复扫无真实秘密，Git 忽略规则覆盖构建与签名产物。
- [x] E6 接口、部署、Android 和迁移文档与修复后行为一致。

## 残余风险

- Web ESLint 尚有 42 条质量警告，集中在既有 React effect 依赖、显式 `any` 和 `<img>` 优化建议；不影响构建，但应在后续迭代清零。
- 本机未安装 `osv-scanner`/`gitleaks` 命令行工具；CI 已配置对应扫描，发布前应查看 CI 扫描结果并处理 high/critical 项。
