# Android 监听端使用与排障

本文档说明 `apps/vmq-android/` 目录下 VMQ Android 监听端的配置、权限、保活和排障方式。

## 1. 作用

Android 监听端用于监听微信 / 支付宝到账通知，并把识别到的金额推送到 VMQ Server。

流程：

```text
微信/支付宝到账通知
        ↓
Android NotificationListenerService
        ↓
识别收款通知并提取金额
        ↓
调用 VMQ Server /appPush
        ↓
VMQ Server 匹配订单并回调业务系统
```

## 2. 构建环境

当前整理版：

| 项目 | 值 |
| --- | --- |
| Android Gradle Plugin | `8.8.2` |
| compileSdk | `35` |
| targetSdk | `35` |
| minSdk | `24` |
| Java | `17` |
| OkHttp | `4.12.0` |
| ZXing | `3.5.3` |
| AndroidX Work | `2.10.0` |

构建前需要配置 Android SDK。任选一种方式：

```bash
export ANDROID_HOME=/path/to/Android/Sdk
```

Windows PowerShell：

```powershell
$env:ANDROID_HOME = 'C:\Users\你的用户名\AppData\Local\Android\Sdk'
```

或创建 `apps/vmq-android/local.properties`：

```properties
sdk.dir=C:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

构建：

```bash
cd apps/vmq-android
./gradlew --no-daemon assembleDebug
```

Windows PowerShell 使用 `gradlew.bat --no-daemon assembleDebug`。

APK 输出：

```text
apps/vmq-android/app/build/outputs/apk/debug/app-debug.apk
```

## 3. 首次配置

1. 安装 APK。
2. 授权“通知使用权”。
3. 在 VMQ 后台打开监控端设置页。
4. Android App 点击“扫码配置”扫描后台二维码，或点击“手动配置”。
5. 点击“检测心跳”，确认能访问后台。
6. 点击“检测监听”，确认通知监听权限正常。
7. 按 App 内“防杀后台设置”完成系统设置。

## 4. 需要的权限

| 权限/能力 | 用途 |
| --- | --- |
| 通知使用权 | 监听微信/支付宝到账通知 |
| 通知权限 | Android 13+ 显示前台常驻通知 |
| WakeLock | 降低息屏后被系统挂起的概率 |
| 开机广播 | 设备重启后恢复监听 |
| 相机 | 扫码配置后台地址和通讯密钥 |
| 可选无障碍服务 | 仅用于状态感知和稳定性提醒 |

可选无障碍增强模块的边界：

- 不读取聊天/支付窗口内容。
- 不执行自动点击。
- 不代替用户授权。
- 仅用于感知微信/支付宝/本应用前台状态，并提供可见状态提醒。

## 5. 保活建议

不同厂商 ROM 对后台限制不同，建议全部完成：

| 设置项 | 建议 |
| --- | --- |
| 电池优化 | 设置为“不受限制” |
| 后台锁定 | 最近任务列表中锁定 VMQ App |
| 自启动 | 允许开机自启 / 自启动 |
| 通知权限 | 保持 VMQ、微信、支付宝通知开启 |
| 前台通知 | 不要关闭“正在监听收款通知”的常驻通知 |

## 6. 应用内运行日志

当前整理版已新增应用内运行日志：

- 主界面入口：`查看运行日志`
- 支持：查看、复制、清空
- 日志保存在 App 私有目录
- 默认控制文件大小，避免无限增长

日志会记录：

- App 启动
- 配置保存 / 扫码配置
- 心跳检测
- 通知监听连接 / 断开
- 收款通知识别
- 金额提取
- 推送成功 / 失败
- WakeLock 申请 / 释放
- 开机自启事件
- 可选无障碍服务状态

> 普通 Android App 不能读取全系统 logcat。应用内运行日志只记录本 App 自身关键运行链路。

## 7. ADB 调试

如需更底层排障，可使用：

```bash
adb logcat -s VmqNotification:D MainActivity:D BootReceiver:D PaymentAccessibilityService:D
```

常见日志含义：

| 日志 | 含义 |
| --- | --- |
| `NotificationListener 已连接` | 通知监听服务连接成功 |
| `心跳成功` | 与后台通信正常 |
| `心跳失败` | 网络、后台地址或密钥异常 |
| `微信 新通知捕获` | 收到微信通知 |
| `支付宝 新通知捕获` | 收到支付宝通知 |
| `收款金额提取成功` | 已识别到账金额 |
| `推送响应: success` | 后台收款推送成功 |
| `去重跳过` | 短时间重复通知被过滤 |

## 8. 常见问题

### 检测监听失败

1. 重新打开系统“通知使用权”。
2. 关闭后再开启 VMQ 通知监听权限。
3. 重启 App。
4. 必要时重启手机。

### 收款后没有回调

1. 确认微信/支付宝通知权限开启。
2. 确认 VMQ 常驻通知仍在。
3. 查看应用内运行日志。
4. 确认后台地址和通讯密钥正确。
5. 确认后台 `/appHeart` 心跳正常。

### 服务频繁被杀

1. 关闭电池优化。
2. 锁定后台。
3. 开启自启动。
4. 检查厂商管家/安全中心是否限制后台联网。

### 金额识别不准

复制应用内运行日志，检查原始通知标题/内容和金额提取过程。如果支付平台通知格式变化，需要更新 `NeNotificationService2.java` 中的关键词或正则。

## 9. 生产安全要求

- 生产配置 URL 必须是 `https://`。扫码或手工配置中的 HTTP 地址会被拒绝。
- Release 构建禁止明文流量；Debug 仅允许 `localhost` 和 `10.0.2.2`，用于模拟器联调。
- 配置和通讯密钥只保存在 Android Keystore 支持的加密存储中。Keystore 不可用时拒绝保存，不回退到普通 `SharedPreferences`。
- `/appPush` 队列项只有在 HTTP 2xx 且响应 JSON `code == 1` 时删除。网络成功但业务失败仍保留并重试。
- HMAC 模式每次心跳/到账请求使用新的 nonce；失败重试应重新签名并生成新 nonce。
