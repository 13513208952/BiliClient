import 'package:PiliPlus/models/model_video.dart';
import 'package:PiliPlus/utils/storage.dart';
import 'package:synchronized/synchronized.dart';

abstract final class NostalgiaUploaderFilter {
  static const _key = 'nostalgiaUploaderFilter';
  static final _writes = Lock();
  static Map<String, String> _entries = Map<String, String>.from(
    GStorage.setting.get(_key, defaultValue: const <String, String>{}) as Map,
  );

  static int get count => _entries.length;

  static bool blocks(BaseOwner owner) =>
      _entries.containsKey('uid:${owner.mid}') ||
      _entries.containsKey('name:${owner.name?.trim()}');

  static Future<void> add(int uid, String name) => _writes.synchronized(() async {
    final label = name.replaceAll(RegExp(r'[\t\r\n]+'), ' ').trim();
    await _save({..._entries, 'uid:$uid': label});
  });

  static Future<void> overwrite(String text) => _writes.synchronized(() async {
    final entries = <String, String>{};
    for (final (index, raw) in text
        .replaceFirst(RegExp(r'^\ufeff'), '')
        .split(RegExp(r'\r\n?|\n'))
        .indexed) {
      if (raw.trim().isEmpty) continue;
      final fields = raw.split('\t');
      if (fields.length == 1) {
        final name = raw.trim();
        entries['name:$name'] = name;
      } else if (fields.length == 2 &&
          RegExp(r'^[1-9][0-9]*$').hasMatch(fields.first) &&
          int.tryParse(fields.first) != null) {
        entries['uid:${fields.first}'] = fields.last.trim();
      } else {
        throw FormatException('第 ${index + 1} 行应为用户名或 UID<TAB>用户名');
      }
    }
    await _save(entries);
  });

  static String exportText() => _entries.entries.map((entry) {
    if (entry.key.startsWith('uid:')) {
      return '${entry.key.substring(4)}\t${entry.value}\n';
    }
    return '${entry.value}\n';
  }).join();

  static Future<void> _save(Map<String, String> entries) async {
    await GStorage.setting.put(_key, entries);
    _entries = entries;
  }
}
