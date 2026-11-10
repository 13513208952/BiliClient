import 'dart:async' show unawaited;

import 'package:PiliPlus/common/widgets/flutter/list_tile.dart';
import 'package:PiliPlus/pages/home/controller.dart';
import 'package:PiliPlus/pages/nostalgia/controller.dart';
import 'package:PiliPlus/services/nostalgia_list_service.dart';
import 'package:PiliPlus/utils/storage.dart';
import 'package:PiliPlus/utils/storage_key.dart';
import 'package:PiliPlus/utils/storage_pref.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart' hide ListTile;
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

class NostalgiaSettingPage extends StatefulWidget {
  const NostalgiaSettingPage({super.key, this.showAppBar = true});

  final bool showAppBar;

  @override
  State<NostalgiaSettingPage> createState() => _NostalgiaSettingPageState();
}

class _NostalgiaSettingPageState extends State<NostalgiaSettingPage> {
  bool _enabled = Pref.nostalgiaEnabled;
  bool _busy = true;
  int _count = 0;
  int _cursor = 0;

  @override
  void initState() {
    super.initState();
    _reloadStatus();
  }

  @override
  Widget build(BuildContext context) {
    final padding = MediaQuery.viewPaddingOf(context);
    return Scaffold(
      appBar: widget.showAppBar ? AppBar(title: const Text('怀旧推荐设置')) : null,
      body: ListView(
        padding: EdgeInsets.only(bottom: padding.bottom + 100),
        children: [
          ListTile(
            leading: const Icon(Icons.history_toggle_off_outlined),
            title: const Text('启用怀旧推荐'),
            trailing: Switch.adaptive(
              value: _enabled,
              onChanged: _busy ? null : _setEnabled,
            ),
            onTap: _busy ? null : () => _setEnabled(!_enabled),
          ),
          ListTile(
            leading: const Icon(Icons.list_alt_outlined),
            title: const Text('AV 列表'),
            subtitle: Text(
              _busy
                  ? '正在读取'
                  : _count == 0
                  ? '列表为空'
                  : '$_count 项，下次从第 ${_cursor + 1} 项开始',
            ),
          ),
          const Divider(height: 1),
          ListTile(
            enabled: !_busy,
            leading: const Icon(Icons.playlist_add_outlined),
            title: const Text('导入 AV 列表'),
            subtitle: const Text('追加新条目并自动去重'),
            onTap: _import,
          ),
          ListTile(
            enabled: !_busy,
            leading: const Icon(Icons.find_replace_outlined),
            title: const Text('覆盖 AV 列表'),
            subtitle: const Text('用所选 TXT 文件替换当前列表'),
            onTap: _overwrite,
          ),
          ListTile(
            enabled: !_busy,
            leading: const Icon(Icons.shuffle_outlined),
            title: const Text('重置为随机进度'),
            onTap: _resetRandomCursor,
          ),
          ListTile(
            enabled: !_busy,
            leading: Icon(
              Icons.delete_outline,
              color: Theme.of(context).colorScheme.error,
            ),
            title: Text(
              '清空 AV 列表',
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
            onTap: _confirmClear,
          ),
        ],
      ),
    );
  }

  Future<void> _reloadStatus() async {
    final entries = await NostalgiaListService.entries();
    if (!mounted) return;
    setState(() {
      _count = entries.length;
      _cursor = _normalizedCursor(
        NostalgiaListService.currentCursor(),
        entries.length,
      );
      _busy = false;
    });
  }

  Future<void> _setEnabled(bool value) async {
    setState(() => _busy = true);
    if (Get.isRegistered<HomeController>()) {
      await Get.find<HomeController>().setNostalgiaEnabled(value);
    } else {
      await GStorage.setting.put(SettingBoxKey.nostalgiaEnabled, value);
    }
    if (!mounted) return;
    setState(() {
      _enabled = value;
      _busy = false;
    });
  }

  Future<void> _import() async {
    final source = await _pickTextFile();
    if (source == null) return;
    await _runListChange(
      () => NostalgiaListService.importText(source),
      success: (count) => '导入完成，当前共 $count 项',
    );
  }

  Future<void> _overwrite() async {
    final source = await _pickTextFile();
    if (source == null) return;
    await _runListChange(
      () => NostalgiaListService.overwriteText(source),
      success: (count) => '覆盖完成，当前共 $count 项',
    );
  }

  Future<String?> _pickTextFile() async {
    final result = await FilePicker.pickFile(
      type: .custom,
      allowedExtensions: const ['txt'],
    );
    return result?.xFile.readAsString();
  }

  Future<void> _resetRandomCursor() async {
    setState(() => _busy = true);
    final cursor = await NostalgiaListService.resetRandomCursor();
    _refreshActiveFeed();
    if (!mounted) return;
    setState(() {
      _cursor = cursor;
      _busy = false;
    });
    SmartDialog.showToast(_count == 0 ? 'AV 列表为空' : '已随机重置到第 ${cursor + 1} 项');
  }

  Future<void> _confirmClear() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('清空 AV 列表'),
        content: const Text('确定清空当前 AV 列表吗？'),
        actions: [
          TextButton(
            onPressed: () => Get.back(result: false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Get.back(result: true),
            child: const Text('清空'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() => _busy = true);
    await NostalgiaListService.clear();
    _refreshActiveFeed();
    if (!mounted) return;
    setState(() {
      _count = 0;
      _cursor = 0;
      _busy = false;
    });
    SmartDialog.showToast('AV 列表已清空');
  }

  Future<void> _runListChange(
    Future<int> Function() change, {
    required String Function(int count) success,
  }) async {
    setState(() => _busy = true);
    try {
      final count = await change();
      final cursor = _normalizedCursor(
        NostalgiaListService.currentCursor(),
        count,
      );
      _refreshActiveFeed();
      if (!mounted) return;
      setState(() {
        _count = count;
        _cursor = cursor;
      });
      SmartDialog.showToast(success(count));
    } catch (error) {
      SmartDialog.showToast('操作失败：$error');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _refreshActiveFeed() {
    if (Get.isRegistered<NostalgiaController>()) {
      unawaited(Get.find<NostalgiaController>().reloadFromStorage());
    }
  }

  static int _normalizedCursor(int cursor, int count) =>
      count == 0 || cursor < 0 || cursor >= count ? 0 : cursor;
}
