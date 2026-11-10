import 'dart:io';
import 'dart:math';

import 'package:PiliPlus/utils/path_utils.dart';
import 'package:PiliPlus/utils/storage.dart';
import 'package:PiliPlus/utils/storage_key.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:path/path.dart' as path;

class NostalgiaEntry {
  const NostalgiaEntry._({this.aid, this.bvid});

  final int? aid;
  final String? bvid;

  String get key => aid == null ? bvid! : 'av$aid';
  String get serialized => aid == null ? bvid! : 'av$aid';
}

abstract final class NostalgiaListService {
  static const _assetPath = 'av_list.txt';
  static List<NostalgiaEntry>? _cache;

  static String get filePath => path.join(appSupportDirPath, _assetPath);

  static Future<List<NostalgiaEntry>> entries() async {
    if (_cache case final entries?) return entries;

    final file = File(filePath);
    final String source;
    if (await file.exists()) {
      source = await file.readAsString();
    } else {
      source = await rootBundle.loadString(_assetPath);
      await _write(_parse(source));
    }

    final entries = List<NostalgiaEntry>.unmodifiable(_parse(source));
    _cache = entries;
    return entries;
  }

  static Future<int> importText(String source) async {
    final imported = _requireEntries(source);
    final merged = <String, NostalgiaEntry>{
      for (final entry in await entries()) entry.key: entry,
      for (final entry in imported) entry.key: entry,
    }.values.toList(growable: false);
    await _write(merged);
    return merged.length;
  }

  static Future<int> overwriteText(String source) async {
    final entries = _requireEntries(source);
    await _write(entries);
    await GStorage.setting.put(SettingBoxKey.nostalgiaCursor, 0);
    return entries.length;
  }

  static Future<void> clear() async {
    await _write(const []);
    await GStorage.setting.put(SettingBoxKey.nostalgiaCursor, 0);
  }

  static Future<int> resetRandomCursor() async {
    final count = (await entries()).length;
    final cursor = count == 0 ? 0 : Random().nextInt(count);
    await GStorage.setting.put(SettingBoxKey.nostalgiaCursor, cursor);
    return cursor;
  }

  static int currentCursor() =>
      GStorage.setting.get(SettingBoxKey.nostalgiaCursor, defaultValue: 0);

  static List<NostalgiaEntry> _requireEntries(String source) {
    final entries = _parse(source);
    if (entries.isEmpty) {
      throw const FormatException('文件中没有有效的 AV/BV 号');
    }
    return entries;
  }

  static List<NostalgiaEntry> _parse(String source) {
    final entries = <NostalgiaEntry>[];
    final seen = <String>{};
    for (final raw in source.split(RegExp(r'[\s,;]+'))) {
      final value = raw.trim();
      if (value.length < 3) continue;

      final lower = value.toLowerCase();
      if (lower.startsWith('av')) {
        final aid = int.tryParse(value.substring(2));
        if (aid != null && aid > 0 && seen.add('av$aid')) {
          entries.add(NostalgiaEntry._(aid: aid));
        }
      } else if (lower.startsWith('bv')) {
        final bvid = 'BV${value.substring(2)}';
        if (seen.add(bvid)) {
          entries.add(NostalgiaEntry._(bvid: bvid));
        }
      }
    }
    return entries;
  }

  static Future<void> _write(List<NostalgiaEntry> entries) async {
    final file = File(filePath);
    await file.create(recursive: true);
    final content = entries.isEmpty
        ? ''
        : '${entries.map((entry) => entry.serialized).join('\n')}\n';
    await file.writeAsString(content, flush: true);
    _cache = List<NostalgiaEntry>.unmodifiable(entries);
  }
}
