import 'dart:io';

import 'package:PiliPlus/pages/setting/pages/availability_hang_page.dart';
import 'package:PiliPlus/services/availability_support_service.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

class AvailabilitySupportTile extends StatelessWidget {
  const AvailabilitySupportTile({super.key});

  @override
  Widget build(BuildContext context) => kIsWeb || !Platform.isAndroid
      ? const SizedBox.shrink()
      : const _AndroidAvailabilitySupportTile();
}

class _AndroidAvailabilitySupportTile extends StatefulWidget {
  const _AndroidAvailabilitySupportTile();

  @override
  State<_AndroidAvailabilitySupportTile> createState() =>
      _AndroidAvailabilitySupportTileState();
}

class _AndroidAvailabilitySupportTileState
    extends State<_AndroidAvailabilitySupportTile> {
  late final AppLifecycleListener _lifecycle;
  AvailabilitySupportStatus? _status;
  bool _loading = true;
  bool _busy = false;
  int _hideGeneration = 0;

  @override
  void initState() {
    super.initState();
    _lifecycle = AppLifecycleListener(
      onResume: _refresh,
      onHide: () => _hideGeneration++,
    );
    _loadStatus();
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    super.dispose();
  }

  void _refresh() {
    if (!_busy) _loadStatus();
  }

  Future<void> _loadStatus() async {
    AvailabilitySupportStatus? status;
    try {
      status = await AvailabilitySupportService.status();
    } catch (_) {
      // Keep edits disabled when the persistent setting cannot be read.
    }
    if (!mounted) return;
    setState(() {
      _status = status;
      _loading = false;
    });
  }

  Future<void> _perform(
    Future<String?> Function() action,
    String failureMessage,
  ) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final notice = await action();
      if (mounted && notice != null) SmartDialog.showToast(notice);
    } catch (error) {
      final details = error is PlatformException ? error.message : null;
      if (mounted) {
        SmartDialog.showToast(
          details == null ? failureMessage : '$failureMessage：$details',
        );
      }
    } finally {
      if (mounted) {
        await _loadStatus();
        if (mounted) setState(() => _busy = false);
      }
    }
  }

  Future<void> _openHangPage() => _perform(() async {
    final generation = _hideGeneration;
    await AvailabilitySupportService.setHangMode(true);
    try {
      if (mounted && generation == _hideGeneration) {
        await Navigator.of(context).push<void>(
          MaterialPageRoute<void>(builder: (_) => const AvailabilityHangPage()),
        );
      }
    } finally {
      await AvailabilitySupportService.setHangMode(false);
    }
    return null;
  }, '无法开启挂机验证');

  Future<void> _chooseStrategy(AvailabilityStrategy current) async {
    final selected = await showDialog<AvailabilityStrategy>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('采集策略模式'),
        children: [
          for (final strategy in AvailabilityStrategy.values)
            ListTile(
              selected: strategy == current,
              title: Text(strategy.label),
              subtitle: Text(switch (strategy) {
                .compatible => '共用 Wi-Fi 连接池，不设资源水位门槛',
                .normal => '默认 · Java 堆达到 90% 暂停，降至 78% 恢复',
                .aggressive => '不限制资源水位，可能因内存耗尽退出',
              }),
              onTap: () => Navigator.pop(context, strategy),
            ),
        ],
      ),
    );
    if (mounted && selected != null && selected != current) {
      await _perform(() async {
        await AvailabilitySupportService.setStrategy(selected);
        return null;
      }, '无法更改采集策略');
    }
  }

  Future<void> _confirmClearPending() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('清空待验证列表？'),
        content: const Text('将删除手机上的所有待验证任务，已保存的验证结果不会删除。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text('确认清空', style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await _perform(() async {
      final count = await AvailabilitySupportService.clearPending();
      return '已清空 $count 条待验证任务';
    }, '清空失败');
  }

  @override
  Widget build(BuildContext context) {
    final status = _status;
    return Column(
      children: [
        SwitchListTile.adaptive(
          secondary: const Icon(Icons.fact_check_outlined),
          title: const Text('协助验证视频可用性'),
          subtitle: const Text('仅在 Wi-Fi 下由系统静默运行；无需保持应用开启'),
          value: status?.enabled ?? false,
          onChanged: status == null || _busy
              ? null
              : (enabled) => _perform(() async {
                  await AvailabilitySupportService.setEnabled(enabled);
                  return null;
                }, '无法更改后台验证开关'),
        ),
        ExpansionTile(
          key: const PageStorageKey<String>('availability-support-files'),
          leading: const Icon(Icons.folder_open_outlined),
          title: const Text('验证队列与结果'),
          subtitle: Text(
            status != null
                ? '${status.pending} 条待验证 · ${status.results} 条结果'
                      '${status.enabled && status.coolingDown ? ' · 风控等待中' : ''}'
                      '${status.resourcePaused ? ' · 资源保护暂停中' : ''}'
                : _loading
                ? '正在读取'
                : '无法读取验证队列',
          ),
          onExpansionChanged: (expanded) {
            if (expanded) _refresh();
          },
          children: [
            ListTile(
              leading: const Icon(Icons.tune),
              title: const Text('采集策略模式'),
              subtitle: Text(
                (status?.strategy ?? AvailabilityStrategy.normal).label,
              ),
              enabled: !_busy && status != null,
              onTap: _busy || status == null
                  ? null
                  : () => _chooseStrategy(status.strategy),
            ),
            ListTile(
              leading: const Icon(Icons.nights_stay_outlined),
              title: const Text('进入挂机验证'),
              subtitle: Text(
                status?.enabled == true
                    ? 'Wi-Fi 下四路验证，黑屏常亮；用系统返回键退出'
                    : '请先开启协助验证视频可用性',
              ),
              enabled: !_busy && status?.enabled == true,
              onTap: _busy || status?.enabled != true ? null : _openHangPage,
            ),
            ListTile(
              enabled: !_busy,
              leading: const Icon(Icons.playlist_add_outlined),
              title: const Text('导入待验证 AV 列表'),
              subtitle: const Text('TXT 每行一个 AV 号，追加并去重；不修改怀旧列表'),
              onTap: () => _perform(() async {
                final count = await AvailabilitySupportService.importCandidates();
                return count == null ? null : '新增 $count 条待验证 AV';
              }, '导入失败'),
            ),
            ListTile(
              enabled: !_busy && (status?.pending ?? 0) > 0,
              leading: const Icon(Icons.delete_outline),
              title: const Text('清空待验证列表'),
              onTap: _confirmClearPending,
            ),
            ListTile(
              enabled: !_busy && (status?.results ?? 0) > 0,
              leading: const Icon(Icons.file_download_outlined),
              title: const Text('导出验证结果'),
              subtitle: const Text('保存已记录的结果为 JSONL，不包含待验证条目'),
              onTap: () => _perform(() async {
                final count = await AvailabilitySupportService.exportResults();
                return count == null ? null : '已导出 $count 条验证结果';
              }, '导出失败'),
            ),
          ],
        ),
      ],
    );
  }
}
