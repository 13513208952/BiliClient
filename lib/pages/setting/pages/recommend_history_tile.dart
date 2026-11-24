import 'package:PiliPlus/pages/rcmd/controller.dart';
import 'package:PiliPlus/services/recommend_history.dart';
import 'package:PiliPlus/services/recommend_history_db.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class RecommendHistoryTile extends StatefulWidget {
  const RecommendHistoryTile({super.key});

  @override
  State<RecommendHistoryTile> createState() => _RecommendHistoryTileState();
}

class _RecommendHistoryTileState extends State<RecommendHistoryTile> {
  bool _busy = false;
  String? _error;
  int _shown = 0, _blocked = 0, _bytes = 0;

  Future<void> _run(Future<void> Function() action) async {
    if (_busy) return;
    setState(() { _busy = true; _error = null; });
    try {
      await action();
      final stats = (await RecommendHistory.database).stats(RecommendHistory.uid, RecommendHistory.period);
      _shown = stats.shown;
      _blocked = stats.blocked;
      _bytes = await RecommendHistory.diskBytes();
    } catch (error) {
      _error = error.toString();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final folder = RecommendHistory.folder;
    return ExpansionTile(
      title: const Text('首页重复推送拦截'),
      subtitle: const Text('只影响首页常规推荐，按账号每半年归档重置'),
      onExpansionChanged: (expanded) {
        if (expanded) _run(() => RecommendHistory.rotate(RecommendHistory.uid, RecommendHistory.period));
      },
      children: [
        SwitchListTile(
          title: const Text('拦截本周期已展示的视频'),
          value: RecommendHistory.enabled,
          onChanged: _busy ? null : (value) => _run(() async {
            await RecommendHistory.setEnabled(value);
            if (Get.isRegistered<RcmdController>()) await Get.find<RcmdController>().onRefresh();
          }),
        ),
        ListTile(
          title: Text('账号 ${RecommendHistory.uid} · ${RecommendHistoryDb.periodLabel(RecommendHistory.period)}'),
          subtitle: Text('已展示 $_shown · 已拦截 $_blocked · 本机数据库 ${(_bytes / 1024).toStringAsFixed(1)} KiB'),
        ),
        ListTile(
          title: const Text('选择自动归档目录'),
          subtitle: Text(folder == null ? '未指定：到期数据会保留，直至归档成功' : Uri.decodeFull(folder)),
          onTap: _busy ? null : () => _run(RecommendHistory.chooseFolder),
        ),
        ListTile(
          title: const Text('导出当前拦截记录（CSV.gz）'),
          onTap: _busy || folder == null ? null : () => _run(() async {
            await RecommendHistory.exportCurrent();
            if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已导出到指定目录')));
          }),
        ),
        if ((_error ?? RecommendHistory.archiveError?.toString()) case final error?)
          ListTile(
            title: const Text('归档或数据库操作未完成'),
            subtitle: Text(error),
            onTap: _busy ? null : () => _run(() => RecommendHistory.rotate(RecommendHistory.uid, RecommendHistory.period, retry: true)),
          ),
      ],
    );
  }
}
