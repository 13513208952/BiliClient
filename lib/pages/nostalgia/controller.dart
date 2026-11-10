import 'dart:async';

import 'package:PiliPlus/http/api.dart';
import 'package:PiliPlus/http/init.dart';
import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/models/model_rec_video_item.dart';
import 'package:PiliPlus/pages/rcmd/controller.dart';
import 'package:PiliPlus/services/nostalgia_list_service.dart';
import 'package:PiliPlus/utils/storage.dart';
import 'package:PiliPlus/utils/storage_key.dart';

/// 怀旧推荐：只依赖持久化 av_list.txt 与一个持久化游标。
///
/// 每次取一页时按游标顺序在线请求视频信息，游标随消耗前进；
/// 清单耗尽后回到开头继续循环。失效条目按有界次数跳过，
/// 不会因为坏数据无限扫表。
class NostalgiaController extends RcmdController {
  static const int pageSize = 20;
  static const int _concurrency = 4;
  static const int _maxAttemptsPerPage = 200;

  int _cursor = 0;

  @override
  void onInit() {
    _cursor = NostalgiaListService.currentCursor();
    super.onInit();
  }

  @override
  Future<LoadingState<List<dynamic>?>> customGetData() async {
    final entries = await NostalgiaListService.entries();
    if (entries.isEmpty) {
      return const Success([]);
    }
    if (_cursor < 0 || _cursor >= entries.length) {
      _cursor = 0;
    }

    final result = <RcmdVideoItemModel>[];
    int attempts = 0;
    final maxAttempts = _maxAttemptsPerPage < entries.length * 2
        ? _maxAttemptsPerPage
        : entries.length * 2;

    while (result.length < pageSize && attempts < maxAttempts) {
      final batch = <NostalgiaEntry>[];
      var pos = _cursor;
      while (batch.length < _concurrency &&
          batch.length < maxAttempts - attempts) {
        batch.add(entries[pos]);
        pos++;
        if (pos >= entries.length) {
          pos = 0;
        }
      }
      if (batch.isEmpty) {
        break;
      }
      _cursor = pos;
      attempts += batch.length;

      final videos = await Future.wait(batch.map(_fetch));
      for (final video in videos) {
        if (video != null && result.length < pageSize) {
          result.add(video);
        }
      }
    }

    await GStorage.setting.put(SettingBoxKey.nostalgiaCursor, _cursor);
    return Success(result);
  }

  @override
  void handleListResponse(List dataList) {}

  @override
  Future<void> onRefresh() {
    page = 0;
    isEnd = false;
    return queryData();
  }

  Future<void> reloadFromStorage() {
    _cursor = NostalgiaListService.currentCursor();
    return onRefresh();
  }

  Future<RcmdVideoItemModel?> _fetch(NostalgiaEntry entry) async {
    try {
      final res = await Request().get(
        Api.videoIntro,
        queryParameters: entry.aid != null
            ? {'aid': entry.aid}
            : {'bvid': entry.bvid},
      );
      final data = res.data;
      if (data is! Map || data['code'] != 0) {
        return null;
      }
      final info = data['data'];
      if (info is! Map) {
        return null;
      }
      return RcmdVideoItemModel.fromJson(<String, dynamic>{
        'id': info['aid'],
        'bvid': info['bvid'],
        'cid': info['cid'],
        'goto': 'av',
        'pic': info['pic'],
        'title': info['title'],
        'duration': info['duration'],
        'pubdate': info['pubdate'],
        'owner': info['owner'],
        'stat': info['stat'],
      });
    } catch (_) {
      return null;
    }
  }
}
