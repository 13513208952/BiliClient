# v0.2.0 正式 Release

我们升级为了基于上游piliplus项目代码，进行一定精简和修改得到，从而提供更成熟更好的体验并减少维护成本。

## 版本与包体

- versionName `0.2.0`，versionCode `2`，包名 `com.bilibilitakemeback`。
- 独立应用，由 ReBrowser 的 ReVideo 入口按提示引导安装；不再由 ReBrowser 内置携带。
- 本版沿用 Android debug keystore 签名。

## 正式包

构建源码：`<COMMIT>`。

| 文件 | 字节 | SHA256 |
| --- | ---: | --- |
| `BiliClient-v0.2.0.apk` | 157,553,550 | `c4b5a76a01f40c9de833dd970ee423c182d1270e4493345e62631236b25ded11` |

## 已知范围

- 视频下载不再依赖 FFmpegKit：音视频合流改为系统 `MediaMuxer`，避免 `libffmpegkit_abidetect.so` 在部分 ABI 上加载失败导致的下载末段崩溃。
- 下载完成文件写入系统视频目录 `Movies/BiliClient`。
- 手机号验证码登录页、发送验证码输入校验、密码登录页与登录页入口已修正。
