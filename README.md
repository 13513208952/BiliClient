# BiliClient

一个第三方哔哩哔哩（Bilibili）安卓客户端，基于 Kotlin + Jetpack Compose 开发，同时适配**手机**与**Android TV**两种形态。

> ⚠️ **仅供个人学习与技术研究使用，禁止任何商业用途，使用后果自负。** 本项目与哔哩哔哩官方无任何关联，暂不附带开源许可证（保留所有权利）。

## 功能

- **视频播放**：Media3 / ExoPlayer 播放 DASH 音视频流，弹幕渲染，倍速、清晰度切换。
- **多类型视频**：常规视频、分 P、合集、番剧（PGC）。
- **下载**：多线程分块下载、断点续传、镜像 failover、音视频合流。
- **账号**：扫码 / 短信登录，点赞 / 投币 / 收藏 / 三连 / 评论 / 追番等互动。
- **客户端门面**：首页推荐、搜索（含联想与历史）、收藏夹、历史记录、个人页。
- **Android TV**：遥控器操控的首页 / 浏览 / 搜索 / 桌面 / 番剧全屏播放等。

## 技术栈

Kotlin · Jetpack Compose · Hilt · Media3 ExoPlayer · Room · Retrofit / OkHttp · WorkManager · Wire(protobuf)

## 构建

- 需要 **JDK 21**。可通过环境变量 `JAVA_HOME`，或在用户级 `~/.gradle/gradle.properties` 中设置：
  ```
  org.gradle.java.home=/path/to/jdk-21
  ```
- 编译与打包：
  ```
  ./gradlew assembleDebug      # 调试包
  ./gradlew assembleRelease    # 发布包（R8 混淆裁剪）
  ```
- `minSdk = 29`，`targetSdk / compileSdk = 36`，ABI：`arm64-v8a` + `x86_64`。

## 签名说明

为便于个人安装验证，release 构建**刻意沿用 Android 公共 debug keystore** 签名（密码为公开的 `android`），**不使用专用发布密钥**。这意味着本仓库**面向学习/个人使用，并非用于正式分发**；如需正式发布，请自行配置独立 keystore。

## 参考

下载与解析思路参考了开源项目 [Bili23-Downloader](https://github.com/ScottSloan/Bili23-Downloader)（MIT，开源、跨平台 B 站下载工具）。
