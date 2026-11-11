# v0.2.1 正式 Release

修复 `v0.2.0` 下载完成后的音视频合流失败。播放器继续使用现有 media_kit 实现，没有替换或回退播放器。

## 版本与包体

- versionName `0.2.1`，versionCode `3`，包名 `com.bilibilitakemeback`。
- 独立应用，由 ReBrowser 的 ReVideo 入口按提示引导安装；不再由 ReBrowser 内置携带。
- 本版沿用 Android debug keystore 签名，可覆盖升级 `v0.2.0`。

## 下载修复

- 恢复经旧版 BiliClient 验证的 FFmpegKit `-c copy` 无损合流，只复制视频和音频轨道，不重新编码。
- Release 构建加入 FFmpegKit JNI/R8 保留规则，避免压缩后 native 方法注册失败。
- 合流失败时保留错误日志；重新下载会清理可能损坏的旧分片，不会误把残缺分片继续合流。
- 分段下载严格校验 `Content-Range`，避免服务器返回范围异常时生成损坏文件。
- 下载完成文件仍写入系统视频目录 `Movies/BiliClient`。

## 实机验证

源码提交：`0006da1f518b15d211ab8f7cd1da0b7922f7934e`。

在 Android 16 ARM64 手机上完成 `av2`（`BV1xx411c7mD` / cid `62131`）真实下载与合流：

- 生成文件：`Movies/BiliClient/BV1xx411c7mD_62131.mp4`
- 文件大小：`57,898,740` 字节
- 视频：H.264，512×384
- 音频：AAC-LC，44.1 kHz，双声道
- 时长：`2055.637732` 秒

## 正式包

| 文件 | 字节 | SHA256 |
| --- | ---: | --- |
| `BiliClient-v0.2.1.apk` | 266,683,666 | `0d7258b1dffe34e13305f4cf3ed0715ee671c3567160f940eab8866b0f542743` |
