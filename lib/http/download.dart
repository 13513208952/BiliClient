import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/http/video.dart';
import 'package:PiliPlus/models/common/account_type.dart';
import 'package:PiliPlus/models/common/video/video_quality.dart';
import 'package:PiliPlus/models/common/video/video_type.dart';
import 'package:PiliPlus/models/video/play/url.dart';
import 'package:PiliPlus/utils/accounts.dart';
import 'package:PiliPlus/utils/storage_pref.dart';
import 'package:PiliPlus/utils/video_utils.dart';
import 'package:collection/collection.dart';

class DownloadMediaInfo {
  const DownloadMediaInfo({
    required this.videoUrls,
    required this.qn,
    required this.videoDescription,
    this.audioUrls,
    this.audioQn,
    this.audioDescription,
  });

  final List<String> videoUrls;
  final List<String>? audioUrls;
  final int qn;
  final int? audioQn;
  final String videoDescription;
  final String? audioDescription;

  bool get needsMux => audioUrls?.isNotEmpty == true;
}

abstract final class DownloadHttp {
  static Future<DownloadMediaInfo> resolve({
    int? avid,
    String? bvid,
    required int cid,
    int qn = 80,
    int audioQn = 30280,
    int? epid,
    int? seasonId,
    String? sourceType,
  }) async {
    final isLogin = Accounts.get(AccountType.video).isLogin;
    final res = await VideoHttp.videoUrl(
      avid: avid,
      bvid: bvid,
      cid: cid,
      qn: qn,
      epid: epid,
      seasonId: seasonId,
      tryLook: !isLogin,
      videoType: switch (sourceType) {
        'pugv' => VideoType.pugv,
        != null when isLogin => VideoType.pgc,
        _ => VideoType.ugc,
      },
    );
    final response = res.dataOrNull;
    if (response == null) {
      throw Exception(res is Error ? res.errMsg : '下载地址获取失败');
    }

    final dash = response.dash;
    if (dash?.video?.isNotEmpty == true) {
      final video = _selectVideo(dash!.video!, qn);
      final actualQn = video.id ?? video.quality.code;
      final format = response.supportFormats?.firstWhereOrNull(
        (e) => e.quality == actualQn,
      );
      final audio = dash.audio?.isNotEmpty == true
          ? _selectAudio(dash.audio!, audioQn)
          : null;
      return DownloadMediaInfo(
        videoUrls: _orderedUrls(video.playUrls, isAudio: false),
        audioUrls: audio == null
            ? null
            : _orderedUrls(audio.playUrls, isAudio: true),
        qn: actualQn,
        audioQn: audio?.id,
        videoDescription:
            format?.displayDesc ??
            format?.newDesc ??
            VideoQuality.fromCode(actualQn).desc,
        audioDescription: audio?.quality,
      );
    }

    final durl = response.durl;
    if (durl == null || durl.isEmpty || durl.first.playUrls.isEmpty) {
      throw const FormatException('该视频不提供可下载媒体流');
    }
    final actualQn = response.quality ?? qn;
    final format = response.supportFormats?.firstWhereOrNull(
      (e) => e.quality == actualQn,
    );
    return DownloadMediaInfo(
      videoUrls: _orderedUrls(durl.first.playUrls, isAudio: false),
      qn: actualQn,
      videoDescription:
          format?.displayDesc ??
          format?.newDesc ??
          VideoQuality.fromCode(actualQn).desc,
    );
  }

  static VideoItem _selectVideo(List<VideoItem> videos, int requestedQn) {
    final qualities = videos.map((e) => e.id ?? e.quality.code).toSet().toList()
      ..sort((a, b) => b.compareTo(a));
    final targetQn = qualities.contains(requestedQn)
        ? requestedQn
        : qualities.firstWhere(
            (e) => e <= requestedQn,
            orElse: () => qualities.last,
          );
    final candidates = videos
        .where((e) => (e.id ?? e.quality.code) == targetQn)
        .toList();
    if (candidates.length == 1) {
      return candidates.first;
    }
    final codecs = candidates.map((e) => e.codecs).whereType<String>().toList();
    if (codecs.isNotEmpty) {
      final codec = VideoUtils.selectCodec(codecs, Pref.preferCodecs);
      return candidates.firstWhere(
        (e) =>
            codec.codes.any((prefix) => e.codecs?.startsWith(prefix) == true),
        orElse: () => candidates.first,
      );
    }
    return candidates.first;
  }

  static AudioItem _selectAudio(List<AudioItem> audios, int requestedQn) {
    return audios.firstWhere(
      (e) => e.id == requestedQn,
      orElse: () => audios.first,
    );
  }

  static List<String> _orderedUrls(
    Iterable<String> source, {
    required bool isAudio,
  }) {
    final urls = source.where((e) => e.isNotEmpty).toList();
    if (urls.isEmpty) {
      throw const FormatException('媒体流地址为空');
    }
    final preferred = VideoUtils.getCdnUrl(urls, isAudio: isAudio);
    return <String>{preferred, ...urls}.toList(growable: false);
  }
}
