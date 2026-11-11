# BiliClient

本项目是bilibili的一个第三方客户端，并精简了上游部分功能，增加了下载功能（下载会默认下载到系统默认视频文件夹路径，顺便移除了缓存功能）。
顺便一提，我们顺便加入了一个定制推送源-怀旧推荐。怀旧推荐会使用我们从internet archive 和common crawl采集得到的视频列表，并进行筛选、精简和定期剔除失效视频，并充分打乱了顺序。
这意味着你可以用这个模式看看6年之前疫情之前的00后们看到的B站是什么样子的，这个模式没有推送算法。推送源详情请看https://github.com/13513208952/Bili20HistoryVideoAV 项目。

## 0.2.1 版本说明

之前的版本是拿AI搓出来的，但是很烂，但是我们后来才发现这个更强大的开源项目，就借来源码用了。
本次是基于上游piliplus仓库的对本项目的重构版本，也可以视作对原仓库的风味版本，后面还会加入更多的一些小功能。

`0.2.1` 修复了下载末段音视频合流失败：恢复经旧版 BiliClient 验证的 FFmpegKit 无损合流，并保留原始视频、音频编码，不改变播放器实现。

## 构建信息

- Android 包名：`com.bilibilitakemeback`
- 版本：`0.2.1`（versionCode 3）
- Android：minSdk 29，targetSdk 36
- Flutter：3.44.7
- JDK：21

```bash
flutter pub get
flutter build apk --release
```

`0.2.1` 的 release 构建暂时使用 Android debug keystore，后续版本再切换正式发布签名。

## 上游与许可证

BiliClient `0.2.1` 基于 [PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus) / 机主提供的 pili20 源码修改。上游源码中的版权声明和第三方许可证均予保留。

本项目依照 [GNU General Public License v3.0](LICENSE) 发布。
