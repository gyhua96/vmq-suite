# VMQ Android

Android 监听端通过 `NotificationListenerService` 识别微信和支付宝到账通知，提取金额并可靠上报至 VMQ API。

## 构建环境

- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.8.2
- minSdk 24

配置 `ANDROID_HOME` 或在当前目录创建不入库的 `local.properties`：

```properties
sdk.dir=C:\\Users\\your-name\\AppData\\Local\\Android\\Sdk
```

构建与测试：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Windows 使用 `gradlew.bat`。调试 APK 位于 `app/build/outputs/apk/debug/`。

仓库不包含共享或生产签名密钥。Gradle 会为 debug 构建使用本机标准调试签名；release 构建应由维护者在本地或 CI 中安全配置独立 keystore。

安装、授权、保活与排障见 [`docs/vmq-suite/ANDROID.md`](../../docs/vmq-suite/ANDROID.md)。

