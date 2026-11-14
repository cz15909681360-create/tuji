# 原生构建与签名

本工程不使用 Flutter、Dart、Rust 或 NDK。

## 环境

- JDK 17 或更新版本，设置 JAVA_HOME。
- Android SDK：platforms;android-36、build-tools;36.0.0；若做设备测试还需 platform-tools。
- 根目录 local.properties：`sdk.dir=C:/your/Android/Sdk`。它不在源码包中，应在本机创建。
- 首次 Gradle 会下载 Android 插件与腾讯 SDK，需可访问 Google Maven 和 Maven Central。

## 开发与验证

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

调试包名为 io.tuji.app.debug，可与正式包并存。模拟器测试请运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

原始样本测试需要在 `.fixtures/` 放入 has-gps.jpg、no-gps.jpg：前者为本次含 GPS 原件，后者为被移除 GPS 的同图。样本未包含在源码包，避免把私人照片随代码分发。单元测试无样本时跳过这一项；仪器测试需要这两份样本。执行合成 30 张、510 MiB 压力测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest -Pstress=true
```

## 正式发布

当前机器上的正式签名保存在 `.secrets/tuji-release.p12` 与 signing.properties，未进入源码包。请单独备份这些本地材料；后续升级必须继续使用同一签名。

其他机器自行签名时，创建密钥并在 signing.properties 配置：

```properties
storeFile=.secrets/your-release.p12
storePassword=YOUR_PASSWORD
keyAlias=YOUR_ALIAS
keyPassword=YOUR_PASSWORD
```

```powershell
.\gradlew.bat :app:assembleRelease
```

产物：app/build/outputs/apk/release/app-release.apk。重签名后的同包名 APK 无法覆盖安装已经使用其他密钥签名的版本。

腾讯 SDK Key 可由用户在应用设置中填写，也可在 local.properties 配置 TENCENT_MAP_KEY；无有效 Key 不计为地图验收通过。
