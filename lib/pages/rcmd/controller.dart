import 'dart:async';

import 'package:PiliPlus/http/loading_state.dart';
import 'package:PiliPlus/http/video.dart';
import 'package:PiliPlus/pages/common/common_list_controller.dart';
import 'package:PiliPlus/services/recommend_history.dart';
import 'package:PiliPlus/services/recommend_history_db.dart';
import 'package:PiliPlus/utils/storage_pref.dart';

class RcmdController extends CommonListController {
  late bool enableSaveLastData = Pref.enableSaveLastData;
  final bool appRcmd = Pref.appRcmd;

  int? lastRefreshAt;
  late bool savedRcmdTip = Pref.savedRcmdTip;
  bool get supportsRecommendHistory => true;
  int? displayedAccount;
  RecommendHistoryDb? _history;
  int? _cycle;
  int _skippedPages = 0;
  bool _localBatchExhausted = false;

  @override
  bool get isEnd => false;

  @override
  void onInit() {
    super.onInit();
    page = 0;
    queryData();
  }

  @override
  Future<LoadingState> customGetData() async {
    _localBatchExhausted = false;
    final account = RecommendHistory.uid;
    if (!supportsRecommendHistory || !RecommendHistory.enabled) {
      _history = null;
      return _fetchPage();
    }
    try {
      final db = await RecommendHistory.database;
      final cycle = RecommendHistory.period;
      unawaited(RecommendHistory.rotate(account, cycle));
      for (var attempts = 0; attempts < 4; attempts++) {
        final result = await _fetchPage();
        if (RecommendHistory.uid != account) return const Error('推荐账号已切换，请刷新');
        if (result is! Success<List> || result.response.isEmpty) return result;
        if (!RecommendHistory.enabled) return result;
        final items = result.response;
        final aids = items.map(RecommendHistory.aidOf).toList();
        final blocked = db.shownAmong(account, cycle, aids.whereType<int>());
        db.recordBlocked(account, cycle, [
          for (final aid in aids) if (aid != null && blocked.contains(aid)) aid,
        ], DateTime.now().millisecondsSinceEpoch);
        final unique = <int>{};
        final kept = <dynamic>[];
        for (var i = 0; i < items.length; i++) {
          final aid = aids[i];
          if (aid == null || (!blocked.contains(aid) && unique.add(aid))) {
            kept.add(items[i]);
          }
        }
        if (kept.isNotEmpty) {
          _history = db;
          _cycle = cycle;
          return Success(kept);
        }
        _skippedPages++;
      }
      _localBatchExhausted = true;
      return const Success(<dynamic>[]);
    } catch (error) {
      return Error('无法读取首页去重记录：$error');
    }
  }

  Future<LoadingState<List>> _fetchPage() {
    final index = page + _skippedPages;
    return appRcmd
        ? VideoHttp.rcmdVideoListApp(freshIdx: index)
        : VideoHttp.rcmdVideoList(freshIdx: index, ps: 20);
  }

  Future<void> recordExposure(int account, int aid) async {
    if (!supportsRecommendHistory || !RecommendHistory.enabled) return;
    final db = await RecommendHistory.database;
    if (account != RecommendHistory.uid || !RecommendHistory.enabled) return;
    final cycle = RecommendHistory.period;
    db.recordShown(account, cycle, [aid]);
    unawaited(RecommendHistory.rotate(account, cycle));
  }

  @override
  bool handleError(String? errMsg) {
    return enableSaveLastData;
  }

  @override
  void handleListResponse(List dataList) {
    if (supportsRecommendHistory && page == 0) {
      final account = RecommendHistory.uid;
      if (displayedAccount != account) {
        displayedAccount = account;
        lastRefreshAt = null;
        return;
      }
    }
    if (enableSaveLastData && page == 0) {
      if (loadingState.value case Success(:final response)) {
        if (response != null && response.isNotEmpty) {
          Iterable<dynamic> old = response.length > 200 ? response.take(50) : response;
          if (supportsRecommendHistory && RecommendHistory.enabled && _history != null) {
            final seen = _history!.shownAmong(RecommendHistory.uid, _cycle!,
                old.map(RecommendHistory.aidOf).whereType<int>());
            final incoming = dataList.map(RecommendHistory.aidOf).whereType<int>().toSet();
            old = old.where((item) {
              final aid = RecommendHistory.aidOf(item);
              return aid == null || (!seen.contains(aid) && incoming.add(aid));
            });
          }
          lastRefreshAt = null;
          final boundary = dataList.length;
          dataList.addAll(old);
          if (savedRcmdTip && dataList.length > boundary) lastRefreshAt = boundary;
        }
      }
    }
  }

  @override
  Future<void> onReload() {
    if (_localBatchExhausted) return queryData();
    return super.onReload();
  }

  @override
  Future<void> onRefresh() {
    page = 0;
    _skippedPages = 0;
    isEnd = false;
    return queryData();
  }
}
