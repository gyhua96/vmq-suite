# VMQ Suite 接口对接指南

本文面向商户服务端、Android 监听端和管理后台的接入开发者。内容按当前源码整理；字段级兼容契约另见 [INTERFACE_SPEC.md](INTERFACE_SPEC.md)。

## 1. 接入概览

典型链路：

```text
商户 -> /createOrder -> VMQ 待支付订单
用户 -> 微信/支付宝付款 -> Android 通知监听
Android -> /appPush -> VMQ 按 type + reallyPrice 匹配订单
VMQ -> notifyUrl -> 商户验签、幂等入账并返回 success
商户/支付页 -> /getOrder 或 /checkOrder -> 查询最终状态
```

地址约定：

| 场景 | 基础地址 | 示例 |
| --- | --- | --- |
| 直连 API | `http(s)://<api-host>:50126` | `http://localhost:50126/createOrder` |
| Docker/Nginx 网关 | `http(s)://<gateway>` | `http://localhost:8080/createOrder` |
| 网关前缀 | `http(s)://<gateway>/vmq-api` | `http://localhost:8080/vmq-api/createOrder` |
| 本地 Web 代理 | `http://localhost:3080/vmq-api` | `http://localhost:3080/vmq-api/getState` |

Nginx 同时保留根路径兼容入口和 `/vmq-api/*`。生产环境必须使用 HTTPS，且不要把 `50126` 直接暴露到公网。

除 `/deQrcode2` 外，接口参数均按 Spring MVC 表单参数处理，可使用查询字符串或 `application/x-www-form-urlencoded`。`@RequestMapping` 接口当前同时接受 GET、POST 等方法；接入方应按本文推荐方法调用，避免依赖过宽的历史行为。

## 2. 通用数据模型

### 2.1 CommonRes

```json
{
  "code": 1,
  "msg": "成功",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | `1` 成功；通常 `-1` 失败；个别业务失败可为 `-2` |
| `msg` | string | 可读提示，不能作为稳定错误码使用 |
| `data` | any/null | 业务数据 |

重要：HTTP 200 只表示请求到达应用，必须继续检查 `code == 1`。

### 2.2 PageRes

```json
{
  "code": 0,
  "msg": "成功",
  "count": 25,
  "data": []
}
```

分页接口以 `code == 0` 表示成功；这与 `CommonRes` 不同。

### 2.3 枚举和时间

| 名称 | 值 | 含义 |
| --- | --- | --- |
| 支付类型 | `1` | 微信 |
| 支付类型 | `2` | 支付宝 |
| 订单状态 | `0` | 待支付 |
| 订单状态 | `1` | 已支付且回调成功/待异步回调 |
| 订单状态 | `2` | 已支付但回调最终失败 |
| 订单状态 | `-1` | 已关闭或超时 |
| 监听状态 | `1` | 在线 |
| 监听状态 | `0` | 离线 |
| 监听状态 | `-1` | 未初始化 |

时间参数和响应时间字段均为 Unix 毫秒时间戳。金额单位为元，创建订单最多两位小数。当前实现和数据库使用浮点数，接入方应把金额作为十进制定点值处理，并以回调原始字符串参与验签。

## 3. 签名协议

通讯密钥是后台设置项 `key`。新接入使用 `HMAC_SHA256`；MD5 只为兼容旧客户端。

### 3.1 HMAC-SHA256 规范

1. 收集该接口规定的签名参数。
2. 排除键名 `sign`（忽略大小写）和所有值为 `null` 的参数；空字符串仍参与。
3. 按 Java `TreeMap` 的键顺序升序排列。
4. 用原始值拼接为 `key=value&key=value`。计算 HMAC 前不做 URL 编码。
5. 使用 UTF-8 和通讯密钥计算 HMAC-SHA256，输出小写十六进制。
6. 再对参数和值做 URL 编码并发送。

示例 canonical string：

```text
nonce=2b2df3c8&payId=ORDER001&param=user-42&price=49.95&signType=HMAC_SHA256&timestamp=1782870000000&type=2
```

时间窗口：商户 HMAC 请求为服务器时间前后 5 分钟；Android HMAC 请求为前后 60 秒。`nonce` 当前只参与签名，服务端没有保存已用 nonce，因此时间窗口内仍可重放。

### 3.2 Java 签名示例

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

static String sign(Map<String, String> input, String secret) throws Exception {
    TreeMap<String, String> sorted = new TreeMap<>();
    input.forEach((k, v) -> {
        if (k != null && !"sign".equalsIgnoreCase(k) && v != null) sorted.put(k, v);
    });
    String canonical = sorted.entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .reduce((a, b) -> a + "&" + b).orElse("");
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] bytes = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
    StringBuilder out = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) out.append(String.format("%02x", b));
    return out.toString();
}
```

### 3.3 JavaScript/TypeScript 签名示例

```ts
import { createHmac } from "node:crypto";

export function sign(params: Record<string, string | null>, secret: string) {
  const canonical = Object.entries(params)
    .filter(([key, value]) => key.toLowerCase() !== "sign" && value !== null)
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([key, value]) => `${key}=${value}`)
    .join("&");
  return createHmac("sha256", secret).update(canonical, "utf8").digest("hex");
}
```

### 3.4 Python 签名示例

```python
import hashlib
import hmac

def sign(params: dict[str, str | None], secret: str) -> str:
    items = sorted(
        (k, v) for k, v in params.items()
        if k.lower() != "sign" and v is not None
    )
    canonical = "&".join(f"{k}={v}" for k, v in items)
    return hmac.new(
        secret.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256
    ).hexdigest()
```

### 3.5 旧 MD5 公式

| 接口 | 拼接原文 |
| --- | --- |
| `/createOrder` | `payId + param + type + price + key` |
| `/closeOrder` | `orderId + key` |
| `/getOrder`、`/checkOrder` | `orderId + key` |
| `/getState`、`/appHeart` | `t + key` |
| `/appPush` | `type + price + t + key` |

`param` 未传时，创建订单的历史签名路径存在 Java `null` 拼接兼容行为；旧接入必须按实际客户端行为联调。新接入应明确传入空字符串并使用 HMAC。

## 4. 全部路由索引

### 4.1 商户和 Android 接口

| 推荐方法 | 路径 | 认证 | 成功响应 | 用途 |
| --- | --- | --- | --- | --- |
| POST | `/createOrder` | MD5/HMAC | `CommonRes<CreateOrderRes>` 或 HTML | 创建订单 |
| POST | `/closeOrder` | MD5/HMAC | `CommonRes` | 关闭待支付订单 |
| GET | `/getOrder` | 当前可选签名 | `CommonRes<CreateOrderRes>` | 查询订单详情 |
| GET | `/checkOrder` | 当前可选签名 | `CommonRes<string>` | 获取支付后跳转 URL |
| GET | `/getState` | MD5 | `CommonRes<object>` | 查询监听端状态 |
| POST | `/appHeart` | MD5/HMAC | `CommonRes` | Android 心跳 |
| POST | `/appPush` | MD5/HMAC | `CommonRes` | Android 到账上报 |

### 4.2 管理和二维码接口

| 推荐方法 | 路径 | 认证 | 响应/说明 |
| --- | --- | --- | --- |
| POST | `/login` | 用户名/密码 | 建立 `JSESSIONID`，返回 `csrfToken` |
| GET | `/admin/getMenu` | Session | 菜单数组 |
| POST | `/admin/saveSetting` | Session + CSRF | 保存设置 |
| GET | `/admin/getSettings` | Session | `CommonRes<map>` |
| GET | `/admin/getOrders` | Session | `PageRes<PayOrder>` |
| POST | `/admin/setBd` | Session + CSRF | 手工补发回调 |
| GET | `/admin/getPayQrcodes` | Session | `PageRes<PayQrcode>` |
| POST | `/admin/addPayQrcode` | Session + CSRF | 新增固定金额码 |
| POST | `/admin/updatePayQrcode` | Session + CSRF | 更新固定金额码 |
| POST | `/admin/delPayQrcode` | Session + CSRF | 删除固定金额码 |
| GET | `/admin/getMain` | Session | 首页统计 |
| POST | `/admin/delOrder` | Session + CSRF | 删除单个订单 |
| POST | `/admin/delGqOrder` | Session + CSRF | 删除全部关闭订单 |
| POST | `/admin/delLastOrder` | Session + CSRF | 删除 7 天前非待支付订单 |
| GET | `/enQrcode` | 无 | `image/png` 二维码 |
| POST | `/deQrcode` | Session | 解码 Base64 二维码 |
| POST | `/deQrcode2` | Session | 解码上传文件 |

`/admin/*` 的 POST/PUT/DELETE/PATCH 需要 `X-CSRF-Token`，`/admin/getMenu` 是唯一写方法 CSRF 例外。二维码解码接口只检查 Session，不经过 `/admin/*` 拦截器。

## 5. 商户订单接口

### 5.1 创建订单 `/createOrder`

参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `payId` | 是 | 商户订单号；业务上应全局唯一 |
| `param` | 否 | 透传参数，回调原样返回 |
| `type` | 是 | `1` 微信，`2` 支付宝 |
| `price` | 是 | 原始金额，正数且最多两位小数 |
| `notifyUrl` | 否 | 订单级异步回调地址，空则使用全局设置 |
| `returnUrl` | 否 | 订单级同步跳转地址，空则使用全局设置 |
| `signType` | HMAC 是 | 固定 `HMAC_SHA256` |
| `timestamp` | HMAC 是 | 当前毫秒时间戳 |
| `nonce` | 建议 | 随机串，参与签名 |
| `sign` | 是 | 签名 |
| `isHtml` | 否 | `0` JSON（默认），`1` 返回跳转脚本 |

HMAC 参数集合：`payId,param,type,price,timestamp,nonce,signType`。`notifyUrl`、`returnUrl` 和 `isHtml` 当前不参与签名，因此调用方必须使用 HTTPS，并避免中间人修改这些字段。

```bash
curl -X POST 'https://vmq.example.com/createOrder' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'payId=ORDER001' \
  --data-urlencode 'param=user-42' \
  --data-urlencode 'type=2' \
  --data-urlencode 'price=49.95' \
  --data-urlencode 'signType=HMAC_SHA256' \
  --data-urlencode 'timestamp=1782870000000' \
  --data-urlencode 'nonce=2b2df3c8' \
  --data-urlencode 'sign=<lowercase-hmac-hex>'
```

成功 `data`：

| 字段 | 说明 |
| --- | --- |
| `payId` | 商户订单号 |
| `orderId` | VMQ 订单号 |
| `payType` | 支付类型 |
| `price` | 商户提交金额 |
| `reallyPrice` | 用户实际应付金额；收银页必须展示此值 |
| `payUrl` | 二维码内容 |
| `isAuto` | `1` 通用二维码，`0` 固定金额二维码 |
| `state` | 初始为 `0` |
| `timeOut` | 有效期，分钟 |
| `date` | 创建时间，毫秒 |

### 5.2 查询、检查和关闭

`/getOrder` 参数：`orderId,sign,signType,timestamp,nonce`，返回与创建订单相同的 `data` 结构。

`/checkOrder` 参数相同。待支付、已关闭或不存在时返回失败；已支付时 `data` 为拼接了回调参数的 `returnUrl`。

`/closeOrder` 参数相同且 `sign` 必填。仅 `state=0` 可关闭，成功后状态变为 `-1` 并释放占用金额。

```bash
curl -G 'https://vmq.example.com/getOrder' \
  --data-urlencode 'orderId=202607011030301234' \
  --data-urlencode 'signType=HMAC_SHA256' \
  --data-urlencode 'timestamp=1782870000000' \
  --data-urlencode 'nonce=7cc8e200' \
  --data-urlencode 'sign=<lowercase-hmac-hex>'
```

当前服务端允许完全不带 `sign` 查询 `/getOrder` 和 `/checkOrder`。这属于已知安全缺陷，不应被接入方当作稳定契约；部署前应修复为强制验签。

### 5.3 监听状态 `/getState`

参数为 `t,sign`，仅支持旧 MD5：`md5(t + key)`。成功数据包含 `state,lastheart,lastpay`，后三者中的时间值为字符串形式的毫秒时间戳。

## 6. Android 接口

### 6.1 心跳 `/appHeart`

参数：`t,signType,nonce,sign`。HMAC 参数集合是 `t,nonce,signType`，有效窗口为前后 60 秒。旧 MD5 为 `md5(t + key)`。

### 6.2 到账推送 `/appPush`

参数：`type,price,t,signType,nonce,sign`。HMAC 参数集合是 `type,price,t,nonce,signType`。旧 MD5 外层接受前后 10 分钟，但 HMAC 校验仍把 `t` 限制在前后 60 秒。

```bash
curl -X POST 'https://vmq.example.com/appPush' \
  --data-urlencode 'type=2' \
  --data-urlencode 'price=49.95' \
  --data-urlencode 't=1782870000000' \
  --data-urlencode 'signType=HMAC_SHA256' \
  --data-urlencode 'nonce=app-6d14' \
  --data-urlencode 'sign=<lowercase-hmac-hex>'
```

服务端以 `type + price + state=0` 匹配订单。重复 `payDate=t` 会返回业务失败；未匹配时会创建“无订单转账”记录。Android 只有在 HTTP 成功且 JSON `code == 1` 时才应从本地队列删除事件。

## 7. 商户异步回调

VMQ 使用 GET 请求：

```text
GET {notifyUrl}?payId=...&param=...&type=...&price=...&reallyPrice=...&signType=HMAC_SHA256&sign=...
```

参与回调 HMAC 的字段为 `payId,param,type,price,reallyPrice,signType`，规则与第 3 节一致。回调没有 `timestamp` 和 `nonce`；商户必须以 `payId` 做幂等，并核对订单状态、币种语境、`price` 和 `reallyPrice`。

处理成功必须返回纯文本：

```text
success
```

不要返回 JSON、HTML、引号或附加说明。失败后每 60 秒重试，累计最多 10 次。`callbackAsync=0` 表示首次同步发送并记录任务；`1` 表示先入队再异步发送。多实例部署当前可能并发取得同一回调任务，商户幂等不可省略。

回调验签伪代码：

```text
receivedSign = query.sign
expected = hmacSha256Hex(key, canonicalize(query without sign))
constantTimeEquals(receivedSign, expected)
```

## 8. 管理后台接口

### 8.1 登录和 CSRF

```bash
curl -i -c vmq.cookies -X POST 'https://vmq.example.com/login' \
  --data-urlencode 'user=admin' \
  --data-urlencode 'pass=<password>'
```

成功响应的 `Set-Cookie` 包含 `JSESSIONID`，`data.csrfToken` 是后续写请求令牌：

```bash
curl -b vmq.cookies -X POST 'https://vmq.example.com/admin/delOrder' \
  -H 'X-CSRF-Token: <csrfToken>' \
  --data-urlencode 'id=123'
```

当前没有服务端 logout 接口；前端退出只清除本地令牌，Session 直到过期仍有效。

### 8.2 设置项

| key/参数 | 说明 |
| --- | --- |
| `user` | 后台账号 |
| `pass` | BCrypt 密码；读取时为 `********`，提交含 `****` 时不更新 |
| `key` | 通讯密钥；读取接口当前会返回明文 |
| `notifyUrl` | 全局异步回调 URL |
| `returnUrl` | 全局同步跳转 URL |
| `wxpay` | 微信通用二维码内容 |
| `zfbpay` | 支付宝通用二维码内容 |
| `close` | 订单有效分钟数，正整数 |
| `payQf` | 金额冲突时 `1` 递增，`2` 递减 |
| `callbackAsync` | `0` 首次同步回调，`1` 异步入队 |
| `lastheart` | 最近心跳毫秒时间戳 |
| `lastpay` | 最近到账毫秒时间戳 |
| `jkstate` | 监听状态 |

`/admin/saveSetting` 接收上述前 9 个参数。`/admin/getSettings` 返回全部设置；因此该接口只允许受信管理员通过 HTTPS 使用。

### 8.3 分页、二维码和维护参数

| 路径 | 参数 |
| --- | --- |
| `/admin/getOrders` | `page,limit,type?,state?` |
| `/admin/getPayQrcodes` | `page,limit,type?` |
| `/admin/setBd` | `id`（订单主键，int） |
| `/admin/addPayQrcode` | `payUrl,price,type` |
| `/admin/updatePayQrcode` | `id,payUrl,price,type` |
| `/admin/delPayQrcode` | `id`（二维码主键，long） |
| `/admin/delOrder` | `id`（订单主键，long） |

分页 `page` 从 1 开始。当前 `limit` 没有服务端上限，调用方应限制在合理值（建议不超过 100），服务端也应在发布前补上上限。

`/admin/getMain` 返回字符串字段：`todayOrder,todaySuccessOrder,todayCloseOrder,todayMoney,countOrder,countMoney`。

## 9. 二维码工具

| 路径 | 参数 | 限制/返回 |
| --- | --- | --- |
| `/enQrcode` | `url` | 生成 PNG；当前无需登录 |
| `/deQrcode` | `base64` | 需要 Session；Base64 最大字符数由 `QrcodeService` 限制 |
| `/deQrcode2` | multipart `file` | 需要 Session；应用和网关默认最大 2 MB |

```bash
curl -b vmq.cookies -X POST 'https://vmq.example.com/deQrcode2' \
  -F 'file=@config-qr.png'
```

## 10. 上线对接检查表

- 使用 HTTPS，并将 API 放在反向代理后面。
- 生成足够长的随机通讯密钥，禁止复用管理员密码。
- 商户请求使用 HMAC-SHA256，服务器时间保持同步。
- 创建订单后展示 `reallyPrice`，不要只展示 `price`。
- 所有响应按业务 `code` 判断，分页响应单独按 `code == 0`。
- 回调先验签，再按 `payId` 幂等处理，最后返回纯文本 `success`。
- 主动轮询只作为补偿；不要依赖当前无签名查询行为。
- 回调和返回地址使用固定的公网 HTTPS 域名，禁止内网、环回和动态跳转 URL。
- 管理写接口同时携带 `JSESSIONID` 和 `X-CSRF-Token`。
- 在修复 [安全审查报告](SECURITY_REVIEW_2026-07-15.md) 中的发布阻断项前，不用于真实资金生产环境。

## 11. 加固版本接入变更

- `/createOrder` 返回 `accessToken` 和 `accessExpiresAt`。浏览器收银页使用该令牌查询订单；商户服务端继续使用签名。
- `/getOrder`、`/checkOrder` 已禁止完全匿名查询，必须提供订单访问令牌或有效签名。
- HMAC 请求的 nonce 会持久化去重；每次请求都要生成新 nonce，不能重试复用。
- 商户回调新增 `eventId`、`timestamp`、`nonce`，这些字段参与 HMAC-SHA256。接收方应校验时间窗口和 nonce，并按 `payId` 幂等。
- Android 业务成功条件为 HTTP 2xx 且响应 JSON `code == 1`；重复的已处理到账事件也返回 `code=1`。
- `POST /logout` 会注销服务端 Session。后台二维码解码接口与其他写接口一样需要 `X-CSRF-Token`。
- 回调 URL 和同步返回 URL 仅接受公网 HTTPS，拒绝 HTTP、环回、内网、链路本地地址和重定向跳转。

建议接入方将 `payId`、`orderId`、`eventId`、回调 nonce 和验签结果写入审计日志，但必须脱敏通讯密钥、访问令牌、Session Cookie 和签名原文。
