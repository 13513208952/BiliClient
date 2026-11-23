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

  @override
  void initState() {
    super.initState();
    _lifecycle = AppLifecycleListener(onResume: _refresh);
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
    await AvailabilitySupportService.setHangMode(true);
    try {
      if (mounted) {
        await Navigator.of(context).push<void>(
          MaterialPageRoute<void>(builder: (_) => const AvailabilityHangPage()),
        );
      }
    } finally {
      await AvailabilitySupportService.setHangMode(false);
    }
    return null;
  }, '无法开启挂机验证');

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
                : _loading
                ? '正在读取'
                : '无法读取验证队列',
          ),
          onExpansionChanged: (expanded) {
            if (expanded) _refresh();
          },
          children: [
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
