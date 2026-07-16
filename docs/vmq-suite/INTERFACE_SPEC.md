# VMQ Suite 接口文档

本文档基于当前源码整理，目标是作为重构前后的接口兼容契约。

## 1. 通用约定

### 1.1 支付类型

| 值 | 含义 |
| --- | --- |
| `1` | 微信 |
| `2` | 支付宝 |

### 1.2 订单状态

| 值 | 含义 |
| --- | --- |
| `0` | 待支付 |
| `1` | 支付成功，回调成功 |
| `2` | 支付成功，回调失败 |
| `-1` | 已关闭或已过期 |

### 1.3 通用响应

大多数接口返回：

```json
{
  "code": 1,
  "msg": "成功",
  "data": {}
}
```

当前约定：

- `code=1`：成功。
- `code=-1` 或其他非 `1`：失败。
- `msg`：中文提示。
- `data`：业务数据，失败时可能为空。

### 1.4 后台分页响应

Layui 表格接口返回：

```json
{
  "code": 0,
  "msg": "成功",
  "count": 100,
  "data": []
}
```

当前约定：

- `code=0`：分页查询成功。
- `code=-1`：分页查询失败或未登录。

### 1.5 时间

除特别说明外，时间字段均为 Unix 毫秒时间戳。

### 1.6 签名模式

当前服务端兼容两种签名。

#### 旧版 MD5

旧版签名为字段拼接后求 MD5 小写十六进制。

该模式必须保留，原因是当前 Android 客户端仍使用 MD5。

#### HMAC-SHA256

当请求参数 `signType=HMAC_SHA256` 时，服务端按以下方式校验：

1. 收集参与签名的参数。
2. 排除 `sign`。
3. 排除值为 `null` 的参数。
4. 按参数名升序排序。
5. 拼接为 `key=value&key=value`。
6. 使用通讯密钥计算 `HmacSHA256` 小写十六进制。

示例：

```text
nonce=abc&payId=ORDER001&param=user1&price=49.95&signType=HMAC_SHA256&timestamp=1782870000000&type=2
```

## 2. 商户支付接口

### 2.1 创建订单

```text
POST /createOrder
GET  /createOrder
```

商户系统调用该接口创建 VMQ 收款订单。

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `payId` | 是 | string | 商户订单号，必须唯一 |
| `param` | 否 | string | 透传参数，回调时原样返回；为空时按空字符串参与旧签名 |
| `type` | 是 | int | 支付类型，`1=微信`、`2=支付宝` |
| `price` | 是 | string | 订单金额，单位元；必须大于 0，最多 2 位小数 |
| `notifyUrl` | 否 | string | 订单级异步回调地址；为空时使用后台全局配置 |
| `returnUrl` | 否 | string | 同步跳转地址；为空时使用后台全局配置 |
| `sign` | 是 | string | 请求签名 |
| `isHtml` | 否 | int | `0=返回JSON`，`1=返回跳转脚本`；默认 `0` |
| `signType` | 否 | string | 传 `HMAC_SHA256` 时启用 HMAC 签名 |
| `timestamp` | HMAC 时建议 | string | 毫秒时间戳 |
| `nonce` | HMAC 时建议 | string | 随机串 |

#### 旧版签名

```text
sign = md5(payId + param + type + price + key)
```

其中 `key` 为后台系统设置中的通讯密钥。

#### HMAC 签名参数

参与参数：

- `payId`
- `param`
- `type`
- `price`
- `timestamp`
- `nonce`
- `signType`

#### 成功响应

```json
{
  "code": 1,
  "msg": "成功",
  "data": {
    "payId": "ORDER202607010001",
    "orderId": "202607011030301234",
    "payType": 2,
    "price": 49.95,
    "reallyPrice": 49.96,
    "payUrl": "https://qr.alipay.com/xxx",
    "isAuto": 1,
    "state": 0,
    "timeOut": 5,
    "date": 1782873030123
  }
}
```

#### 响应字段

| 字段 | 说明 |
| --- | --- |
| `payId` | 商户订单号 |
| `orderId` | VMQ 云端订单号 |
| `payType` | 支付类型 |
| `price` | 商户提交的原始订单金额 |
| `reallyPrice` | 实际应付金额，用于到账匹配 |
| `payUrl` | 二维码内容或支付页面内容 |
| `isAuto` | `1=通用二维码`，`0=固定金额二维码` |
| `state` | 订单状态 |
| `timeOut` | 订单有效期，单位分钟 |
| `date` | 创建时间，毫秒时间戳 |

#### 兼容 HTML 模式

当 `isHtml=1` 且创建成功时，接口返回：

```html
<script>window.location.href = '/payPage/pay.html?orderId=...'</script>
```

创建失败时返回错误消息文本。

#### 业务规则

- `payId` 不能为空且不能重复。
- `type` 必须为 `1` 或 `2`。
- `price` 必须能转换为数字、必须大于 0，且最多支持 2 位小数。
- `notifyUrl`、`returnUrl` 如果传入，必须是安全公网 HTTP/HTTPS 地址。
- 系统会通过实际金额区分并发订单，商户应展示并要求用户支付 `reallyPrice`。

### 2.2 关闭订单

```text
POST /closeOrder
GET  /closeOrder
```

关闭未支付订单。

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `orderId` | 是 | string | VMQ 云端订单号 |
| `sign` | 是 | string | 签名 |
| `signType` | 否 | string | `HMAC_SHA256` |
| `timestamp` | HMAC 时建议 | string | 毫秒时间戳 |
| `nonce` | HMAC 时建议 | string | 随机串 |

#### 旧版签名

```text
sign = md5(orderId + key)
```

#### 成功响应

```json
{
  "code": 1,
  "msg": "成功",
  "data": null
}
```

#### 业务规则

- 只有 `state=0` 的订单允许关闭。
- 关闭后 `state=-1`。
- 关闭时释放该订单占用的实际金额。

### 2.3 查询订单

```text
GET /getOrder
POST /getOrder
```

查询订单详情。

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `orderId` | 是 | string | VMQ 云端订单号 |
| `sign` | 否 | string | 可选签名；传入时服务端会校验 |
| `signType` | 否 | string | `HMAC_SHA256` |
| `timestamp` | 否 | string | 毫秒时间戳 |
| `nonce` | 否 | string | 随机串 |

#### 旧版签名

```text
sign = md5(orderId + key)
```

#### 成功响应

同 `/createOrder` 中的 `CreateOrderRes`。

### 2.4 检查订单支付结果

```text
GET /checkOrder
POST /checkOrder
```

用于支付页检查订单是否已支付，并获取同步跳转 URL。

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `orderId` | 是 | string | VMQ 云端订单号 |
| `sign` | 否 | string | 可选签名；传入时服务端会校验 |
| `signType` | 否 | string | `HMAC_SHA256` |
| `timestamp` | 否 | string | 毫秒时间戳 |
| `nonce` | 否 | string | 随机串 |

#### 成功响应

```json
{
  "code": 1,
  "msg": "成功",
  "data": "https://merchant.example.com/return?payId=...&param=...&type=2&price=49.95&reallyPrice=49.96&signType=HMAC_SHA256&sign=..."
}
```

#### 业务规则

- 订单不存在：失败。
- `state=0`：失败，提示未支付。
- `state=-1`：失败，提示已过期。
- `state=1` 或 `state=2`：返回同步跳转地址加回调参数。
- `returnUrl` 优先使用订单级配置；为空时使用后台全局配置。
- `returnUrl` 必须通过安全 URL 校验。

### 2.5 查询监听端状态

```text
GET /getState
POST /getState
```

查询 Android 监听端状态。

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `t` | 是 | string | 毫秒时间戳 |
| `sign` | 是 | string | 签名 |

#### 旧版签名

```text
sign = md5(t + key)
```

#### 成功响应

```json
{
  "code": 1,
  "msg": "成功",
  "data": {
    "state": "1",
    "lastheart": "1782873030123",
    "lastpay": "1782873000000"
  }
}
```

#### 状态说明

| 字段 | 说明 |
| --- | --- |
| `state` | `1=在线`、`0=离线`、`-1=未初始化` |
| `lastheart` | 最近心跳时间 |
| `lastpay` | 最近到账推送时间 |

## 3. Android 监听端接口

### 3.1 心跳

```text
GET /appHeart
POST /appHeart
```

Android 监听端定时调用，表示监听端仍在线。

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `t` | 是 | string | Android 当前毫秒时间戳 |
| `sign` | 是 | string | 签名 |
| `signType` | 否 | string | `HMAC_SHA256` |
| `nonce` | 否 | string | 随机串 |

#### 旧版签名

```text
sign = md5(t + key)
```

#### HMAC 签名参数

参与参数：

- `t`
- `nonce`
- `signType`

#### 成功响应

```json
{
  "code": 1,
  "msg": "成功",
  "data": null
}
```

#### 业务规则

- 服务端检查客户端时间与服务端时间差，当前旧模式窗口约为 50 秒。
- 成功后更新：
  - `setting.lastheart = t`
  - `setting.jkstate = 1`

### 3.2 到账推送

```text
GET /appPush
POST /appPush
```

Android 监听端识别到账通知后调用。

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `type` | 是 | int | `1=微信`、`2=支付宝` |
| `price` | 是 | string | 监听到的到账金额 |
| `t` | 是 | string | Android 当前毫秒时间戳 |
| `sign` | 是 | string | 签名 |
| `signType` | 否 | string | `HMAC_SHA256` |
| `nonce` | 否 | string | 随机串 |

#### 旧版签名

```text
sign = md5(type + price + t + key)
```

注意：旧 Android 代码中 `price` 来自 `double` 字符串，例如 `1.0`、`49.95`。后端重构时必须兼容此格式。

#### HMAC 签名参数

参与参数：

- `type`
- `price`
- `t`
- `nonce`
- `signType`

#### 成功响应

```json
{
  "code": 1,
  "msg": "成功",
  "data": null
}
```

#### 业务规则

- 校验时间窗口。
- 校验签名。
- 更新 `setting.lastpay = t`。
- 如果已存在 `payDate=t` 的订单，返回重复推送错误。
- 按 `type + price + state=0` 匹配订单。
- 匹配成功：
  - 删除 `tmp_price` 中的 `type-price`。
  - 设置订单 `state=1`。
  - 设置 `payDate=t`、`closeDate=t`，其中 `t` 为 App 推送的支付事件时间。
  - 发起异步回调。
  - 回调返回纯文本 `success` 时保持 `state=1`。
- 回调失败时设置 `state=2`。兼容性修正要求：实现时应以数据库当前状态做条件更新，只有当前仍为 `state=1` 的订单才允许转为 `state=2`；如果订单已被并发关闭、删除或改为其他状态，不得用迟到的回调失败结果覆盖当前状态。异步回调模式下，回调任务最终失败同样应把当前仍为 `state=1` 的订单标记为 `state=2`；后续补发或重试成功时，当前为 `state=2` 的订单可恢复为 `state=1`。
- 未匹配订单：
  - 创建一条“无订单转账”记录。
  - `state=1`。
  - `payId/orderId` 使用 `无订单转账-{type}-{t}`，保留可识别前缀并避免未来唯一索引冲突。
  - `param/payUrl` 使用“无订单转账”标识。

## 4. 回调商户系统

当 `/appPush` 匹配到订单后，VMQ Server 会请求商户系统。

### 4.1 请求方式

当前实现使用 HTTP GET：

```text
GET {notifyUrl}?payId=...&param=...&type=...&price=...&reallyPrice=...&signType=HMAC_SHA256&sign=...
```

### 4.2 回调参数

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `payId` | string | 商户订单号 |
| `param` | string | 创建订单时传入的透传参数 |
| `type` | int | 支付类型 |
| `price` | number/string | 原始订单金额 |
| `reallyPrice` | number/string | 实际到账金额 |
| `signType` | string | 当前为 `HMAC_SHA256` |
| `sign` | string | 回调签名 |

### 4.3 当前回调签名

当前源码构造方式为：

```text
params = {
  payId,
  param,
  type,
  price,
  reallyPrice,
  signType=HMAC_SHA256
}
canonical = 按参数名升序拼接 key=value&key=value
sign = hmacSha256Hex(key, canonical)
```

### 4.4 历史 MD5 回调签名

旧文档曾描述：

```text
sign = md5(payId + param + type + price + reallyPrice + key)
```

当前源码默认发送 HMAC-SHA256。重构时如果要完全兼容旧商户，可通过配置支持旧回调签名。

### 4.5 成功响应

商户系统必须返回纯文本：

```text
success
```

否则 VMQ 会认为回调失败，并将订单设置为 `state=2`。

## 5. 后台认证接口

### 5.1 登录

```text
POST /login
```

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `user` | 是 | string | 管理员账号 |
| `pass` | 是 | string | 管理员密码 |

#### 成功响应

```json
{
  "code": 1,
  "msg": "成功",
  "data": {
    "csrfToken": "token..."
  }
}
```

#### 业务规则

- 登录成功后写入 Session 属性 `login=1`。
- 登录成功后生成 CSRF Token。
- 后台 `/admin/**` 写接口必须携带 `X-CSRF-Token` Header 或 `csrfToken` 参数。
- 检测到默认不安全密码时拒绝登录。

## 6. 后台管理接口

后台接口除 `/login` 外均要求已登录。

### 6.1 获取菜单

```text
GET  /admin/getMenu
POST /admin/getMenu
```

返回后台左侧菜单数组。未登录时返回 `null` 或拦截器错误。

### 6.2 保存系统设置

```text
POST /admin/saveSetting
```

#### 请求参数

| 参数 | 说明 |
| --- | --- |
| `user` | 后台账号 |
| `pass` | 后台密码；包含 `****` 时不更新 |
| `notifyUrl` | 全局异步回调地址 |
| `returnUrl` | 全局同步跳转地址 |
| `key` | 通讯密钥；为空时不更新 |
| `wxpay` | 微信通用二维码内容 |
| `zfbpay` | 支付宝通用二维码内容 |
| `close` | 订单有效期，单位分钟 |
| `payQf` | 金额区分方向，`1=递增`，`2=递减` |
| `callbackAsync` | 可选。回调模式，`0=同步发送并记录任务`，`1=只入队任务并快速返回`；默认 `0` |

#### 成功响应

```json
{
  "code": 1,
  "msg": "成功",
  "data": null
}
```

### 6.3 获取系统设置

```text
GET  /admin/getSettings
POST /admin/getSettings
```

返回所有 `setting` key-value。密码字段返回 `********`。

#### 成功响应示例

```json
{
  "code": 1,
  "msg": "成功",
  "data": {
    "user": "admin",
    "pass": "********",
    "notifyUrl": "https://merchant.example.com/callback",
    "returnUrl": "https://merchant.example.com/return",
    "key": "secret",
    "close": "5",
    "payQf": "1",
    "callbackAsync": "0",
    "wxpay": "...",
    "zfbpay": "...",
    "lastheart": "1782873030123",
    "lastpay": "1782873000000",
    "jkstate": "1"
  }
}
```

### 6.4 获取订单列表

```text
GET  /admin/getOrders
POST /admin/getOrders
```

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `page` | 是 | int | 页码，从 1 开始 |
| `limit` | 是 | int | 每页数量 |
| `type` | 否 | int | 支付类型 |
| `state` | 否 | int | 订单状态 |

#### 成功响应

```json
{
  "code": 0,
  "msg": "成功",
  "count": 1,
  "data": [
    {
      "id": 1,
      "orderId": "202607011030301234",
      "payId": "ORDER202607010001",
      "createDate": 1782873030123,
      "payDate": 0,
      "closeDate": 0,
      "param": "user1",
      "type": 2,
      "price": 49.95,
      "reallyPrice": 49.96,
      "notifyUrl": "https://merchant.example.com/callback",
      "returnUrl": "https://merchant.example.com/return",
      "state": 0,
      "isAuto": 1,
      "payUrl": "..."
    }
  ]
}
```

### 6.5 手动补发回调

```text
POST /admin/setBd
```

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `id` | 是 | int | 订单数据库 ID |

#### 业务规则

- 查找订单。
- 构造回调参数。
- 请求订单级 `notifyUrl` 或全局 `notifyUrl`。
- 返回 `success` 时：
  - 如果订单仍是 `state=0`，释放金额锁。
  - 设置订单 `state=1`。
- 回调失败时返回错误。
- 兼容性修正要求：实现时应以数据库当前状态做条件更新。只有当前仍为 `state=0` 的订单才需要释放金额锁；`state=2` 的订单补发成功只应更新为 `state=1`。不得用请求开始时查询到的过期对象状态覆盖已经被 App 到账、关闭或删除改变的订单状态。

### 6.6 获取固定二维码列表

```text
GET  /admin/getPayQrcodes
POST /admin/getPayQrcodes
```

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `page` | 是 | int | 页码 |
| `limit` | 是 | int | 每页数量 |
| `type` | 否 | int | 支付类型 |

#### 成功响应

```json
{
  "code": 0,
  "msg": "成功",
  "count": 1,
  "data": [
    {
      "id": 1,
      "payUrl": "https://qr.alipay.com/xxx",
      "price": 49.95,
      "type": 2
    }
  ]
}
```

### 6.7 新增固定二维码

```text
POST /admin/addPayQrcode
```

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `payUrl` | 是 | string | 二维码内容 |
| `price` | 是 | number | 固定金额；必须大于 0，最多 2 位小数 |
| `type` | 是 | int | 支付类型 |

### 6.8 删除固定二维码

```text
POST /admin/delPayQrcode
```

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `id` | 是 | long | 二维码 ID |

### 6.9 首页统计

```text
GET  /admin/getMain
POST /admin/getMain
```

统计口径：

- `todaySuccessOrder` 和 `countOrder` 均统计已到账订单，包括 `state=1` 支付成功和 `state=2` 支付成功但回调失败。
- `todayMoney` 和 `countMoney` 与成功订单口径一致，同样包含 `state=1` 和 `state=2`。
- `todayCloseOrder` 统计 `state=-1` 的关闭订单。

#### 成功响应

```json
{
  "code": 1,
  "msg": "成功",
  "data": {
    "todayOrder": "10",
    "todaySuccessOrder": "8",
    "todayCloseOrder": "1",
    "todayMoney": "199.90",
    "countOrder": "100",
    "countMoney": "2999.00"
  }
}
```

### 6.10 删除订单

```text
POST /admin/delOrder
```

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `id` | 是 | long | 订单 ID |

#### 业务规则

- 如果订单当前 `state=0`，先释放金额锁。
- 删除订单记录。
- 兼容性修正要求：实现时应以数据库当前状态判断是否释放金额锁。后台删除与 App 到账、定时关闭可能并发发生，不能依赖请求开始时查询到的旧对象状态释放金额锁。

### 6.11 删除过期订单

```text
POST /admin/delGqOrder
```

删除所有 `state=-1` 的订单。

### 6.12 删除 7 天前订单

```text
POST /admin/delLastOrder
```

删除 `createDate < now - 7天` 且 `state != 0` 的订单。待支付订单不会被该批量接口直接删除，避免绕过金额锁释放；如需删除单个待支付订单，应使用 `/admin/delOrder`。

## 7. 二维码工具接口

### 7.1 生成二维码图片

```text
GET /enQrcode
```

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `url` | 是 | string | 需要编码为二维码的内容 |

#### 响应

返回 PNG 图片流，尺寸约 `200x200`。

### 7.2 Base64 解码二维码

```text
POST /deQrcode
```

后台已登录后使用。

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `base64` | 是 | string | 图片 Base64，不含 data URL 前缀 |

#### 限制

- Base64 字符串最大约 3MB。

#### 成功响应

```json
{
  "code": 1,
  "msg": "成功",
  "data": "二维码内容"
}
```

### 7.3 文件上传解码二维码

```text
POST /deQrcode2
Content-Type: multipart/form-data
```

后台已登录后使用。

#### 请求参数

| 参数 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `file` | 是 | file | 图片文件 |

#### 限制

- 文件最大 2MB。

## 8. 定时任务行为

后端每 30 秒执行一次：

1. 读取 `setting.close`，默认 `5` 分钟。
2. 将 `createDate < now - close分钟` 且 `state=0` 的订单关闭为 `state=-1`。
3. 对本轮关闭的订单释放 `tmp_price`。
4. 读取 `lastheart` 和 `jkstate`。
5. 如果 `jkstate=1` 且最近心跳超过 60 秒，设置 `jkstate=0`。

## 9. 兼容性清单

重构后必须保持：

- 旧 Android 使用 MD5 调 `/appHeart` 可成功。
- 旧 Android 使用 MD5 调 `/appPush` 可成功。
- `/appPush` 的非法金额返回业务错误，不写入 `lastpay`，不记录到账事件，不创建无订单转账，也不进入订单匹配。
- 旧商户使用 MD5 调 `/createOrder` 可成功。
- `isHtml=1` 仍返回支付页跳转脚本。
- 后台 Layui 表格分页响应仍为 `code=0,count,data`。
- 后台 Session 登录和 CSRF 可继续服务现有页面。
- `/enQrcode`、`/deQrcode`、`/deQrcode2` 路径不变。
- 回调成功判断仍为纯文本 `success`。

## 10. 技术栈升级期间的接口边界

当前实现使用 Java 21 + Spring Boot 3.5.16。未来升级框架、替换 JSON 库或改变打包方式时，本接口文档仍是旧接口兼容契约，不应直接改变旧接口语义。

### 10.1 旧接口不可变项

以下内容默认不可变，除非经过兼容测试和迁移公告：

- URL 路径不改名，包括 `/createOrder`、`/closeOrder`、`/getOrder`、`/checkOrder`、`/getState`、`/appHeart`、`/appPush`、`/login`、`/admin/*`、二维码工具接口。
- 请求参数名不改名，旧表单提交和 query string 提交继续可用。
- 旧 MD5 签名继续可用，`price=1.0` 这类历史字符串格式必须继续参与旧签名。
- 通用响应继续使用 `code/msg/data`。
- Layui 分页响应继续使用 `code/msg/count/data`，成功码仍为 `0`。
- 后台登录继续兼容 Session 和 CSRF token。
- 回调商户继续使用 HTTP GET，商户返回纯文本 `success` 仍判定成功。
- 订单状态值继续使用 `-1/0/1/2`。
- 支付类型值继续使用 `1/2`。

### 10.2 允许的兼容性修正

以下修改属于合理优化，但必须补充测试：

- 原先会触发 500 的非法入参，可以收敛为业务错误响应。
- 缺失或非法分页参数可以兜底为 `page=1&limit=10`。
- 缺失非关键配置可以使用兼容默认值。
- 回调失败可以记录到任务表并重试，但旧同步回调语义在默认配置下仍需保留；异步任务最终失败和后续重试成功也必须同步订单 `state=2/state=1`，避免后台统计与实际回调结果脱节。
- 内部金额可以使用 `BigDecimal` 或金额分，但旧响应字段短期继续保持现有 JSON 形态。

### 10.3 技术栈升级不得借机改变的行为

以下行为在 Java 21、Spring Boot 3.5.x、Spring Boot 4.1.x、Jackson、Spring Security、Vue 3 后台等迁移过程中仍必须保持：

- 参数绑定继续兼容 query string、form 表单和历史空值语义。
- 旧接口响应字段名、大小写、成功码和失败码不因 JSON 库替换而改变。
- 后台 Layui 表格接口继续返回 `code/msg/count/data`。
- Session 登录、CSRF token、未登录跳转或错误语义继续服务旧后台页面。
- 商户回调继续支持 HTTP GET 和纯文本 `success` 成功判断。
- MD5 签名兼容继续保留；HMAC-SHA256 只能作为增强能力或 v2 强制能力。
- 金额字段内部可改为精确类型，但旧签名和旧响应必须兼容 `1.0`、`49.95` 等历史字符串/数字格式。

技术栈升级期间确需收紧的安全行为，例如回调 URL 拒绝内网地址、非法时间戳返回业务错误、二维码空文件返回业务错误，应在变更说明中记录，并补充回归测试。

### 10.4 v2 接口演进边界

新能力应优先放入 `/api/v2/*`，不直接改变旧接口：

- 新订单接口可以使用 JSON 请求体、强制 HMAC-SHA256、明确业务错误码。
- 新 Android 接口可以统一金额字符串、nonce、timestamp 和设备标识。
- 新后台接口可以返回更标准的分页结构，但旧 Layui 接口继续保留。
- OpenAPI 文档只作为 v2 的机器可读文档；旧接口仍以本文档为准。
- v2 可以要求更严格的 URL 白名单、签名时间窗和幂等键；旧接口只逐步增加可选增强项。

### 10.5 技术栈升级适配测试清单

从 Spring Boot 2.1 升到 Boot 3.5.x 或 Boot 4.1.x 前，必须用自动化测试证明以下旧接口行为没有被框架、JSON 库、Servlet 容器、JPA 或安全组件改变：

- `/createOrder` 同时覆盖 GET、POST、form/query 参数、`param` 为空、`isHtml=1`、旧 MD5、HMAC-SHA256、金额 `1.0/1.00/49.95`。
- `/appPush` 覆盖旧 Android MD5、金额字符串原样参与签名、重复推送返回 `重复推送`、非法时间返回 `客户端时间错误`、非法金额不写入到账事件。
- `/closeOrder`、`/getOrder`、`/checkOrder`、`/getState` 覆盖旧签名、可选签名、空参数、订单不存在、待支付、已支付、回调失败和已关闭状态。
- `/login` 和 `/admin/*` 覆盖 Session 登录、CSRF token、未登录响应、Layui 分页 `code/msg/count/data`、后台写接口旧参数名。
- `/admin/setBd`、`/admin/delOrder`、`/admin/delLastOrder` 覆盖基于数据库当前状态的条件更新/删除，避免后台操作与 App 到账或定时关闭并发时释放错误金额锁。
- `/enQrcode`、`/deQrcode`、`/deQrcode2` 覆盖登录态、空文件、超限文件、不可解码图片、成功 PNG/JSON 响应。
- 商户回调覆盖 GET 参数顺序、HMAC-SHA256 签名、纯文本 `success` 成功判定、非 `success` 失败判定、同步/异步回调状态同步。
- 数据库集成测试至少在真实 PostgreSQL 上覆盖订单创建、`tmp_price` 金额占用、`payment_event` 幂等、`callback_task` 重试、唯一约束和 Flyway 迁移脚本。

如果上述清单中任一项因技术栈升级产生行为变化，必须先判断是否属于 `10.2` 中允许的兼容性修正；否则应回退或通过 `/api/v2/*` 提供新行为，不能直接改变旧接口。

## 11. 当前安全契约（覆盖前文旧行为）

本节描述加固后的实际行为；若前文出现“允许匿名查询”“回调无 nonce”或“重复推送返回失败”等旧描述，以本节为准。

### 11.1 订单访问令牌

`/createOrder` 成功响应新增：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessToken` | string | 与 `orderId` 绑定的短期随机访问令牌 |
| `accessExpiresAt` | long | 令牌过期时间，Unix 毫秒 |

`/getOrder` 和 `/checkOrder` 不允许完全匿名访问，调用方必须二选一：

- 商户服务端：提供有效 MD5/HMAC 签名；HMAC 还必须提供时间戳和未使用过的 nonce。
- 内置收银页：提供创建订单响应中的 `accessToken`。令牌只能访问绑定订单且过期后失效。

不要把 `accessToken` 写入服务端日志、分析平台或 Referer。`isHtml=1` 时令牌会随内置支付页 URL 传递。

### 11.2 HMAC nonce

HMAC 请求的 nonce 在服务端持久化，并按接口 scope 去重。相同 scope 下重放同一 nonce 会失败；调用方每次请求必须生成新的高熵随机值。时间戳必须在服务端允许窗口内。

### 11.3 商户回调字段

当前 GET 回调完整字段为：

```text
payId,param,type,price,reallyPrice,eventId,timestamp,nonce,signType,sign
```

| 字段 | 说明 |
| --- | --- |
| `eventId` | 到账事件唯一标识，商户可与 `payId` 共同用于审计幂等 |
| `timestamp` | VMQ 生成回调的 Unix 毫秒时间戳 |
| `nonce` | 每次构造回调时生成的随机值 |

验签时排除 `sign`，其余非空字段按参数名升序组成 canonical string，再计算 HMAC-SHA256。商户必须校验时间窗口、nonce 重放和 `payId` 幂等，成功后仅返回纯文本 `success`。

### 11.4 到账与回调幂等

- Android 重复上报已处理事件时返回 `code=1`，使客户端安全删除本地队列项。
- 回调任务先在数据库中 claim，claim 租约期内其他实例不能重复发送；租约过期后允许恢复。
- Android 仅在 HTTP 2xx 且 JSON `code == 1` 时确认事件成功。

### 11.5 后台与二维码接口

- `POST /logout` 会使当前 Session 失效。
- 登录按 IP + 账号限制连续失败尝试；成功登录后轮换 Session ID。
- `/admin/getSettings` 中管理员密码和通讯密钥均返回 `********`；提交该占位符不会覆盖真实值。
- 后台分页 `limit` 最大为 `100`。
- `/deQrcode`、`/deQrcode2` 需要已登录 Session 和 `X-CSRF-Token`。
