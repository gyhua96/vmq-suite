# VMQ Server API 文档入口

本文件保留为历史入口，避免旧书签失效。当前项目的完整接口契约已经收敛到：

- [INTERFACE_SPEC.md](INTERFACE_SPEC.md)：旧商户接口、Android 监听端接口、后台接口、二维码工具接口、签名规则、回调规则和技术栈升级期间的兼容边界。

## 当前兼容原则

- 旧路径继续保留：`/createOrder`、`/closeOrder`、`/getOrder`、`/checkOrder`、`/getState`、`/appHeart`、`/appPush`、`/login`、`/admin/*`、`/enQrcode`、`/deQrcode`、`/deQrcode2`。
- 旧 MD5 签名继续兼容。
- `signType=HMAC_SHA256` 是增强签名能力，新接入方推荐优先使用。
- 商户回调当前默认发送 HMAC-SHA256 参数；如需历史 MD5 回调签名兼容，应按 [INTERFACE_SPEC.md](INTERFACE_SPEC.md) 的回调章节和重构设计方案处理。
- 回调成功判断仍为纯文本 `success`。
- 后台分页接口继续使用 Layui 的 `code/msg/count/data` 响应结构。

## 对接建议

新接入或重构对接时，请不要再以本文件作为字段级依据。字段、错误文案、状态值、签名字符串、HMAC canonical 规则、回调任务行为和后台接口语义均以 [INTERFACE_SPEC.md](INTERFACE_SPEC.md) 为准。

重构设计、技术栈迁移路线和必要优化项见：

- [DATABASE_MIGRATION.md](DATABASE_MIGRATION.md)
