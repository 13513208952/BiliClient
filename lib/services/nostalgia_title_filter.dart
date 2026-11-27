import 'package:PiliPlus/utils/storage.dart';
import 'package:synchronized/synchronized.dart';

abstract final class NostalgiaTitleFilter {
  static const _key = 'nostalgiaTitleFilter';
  static final _writes = Lock();
  static String get text => GStorage.setting.get(_key, defaultValue: '');
  static RegExp? _compiled = compile(text);

  static RegExp? get pattern => _compiled;

  static RegExp? compile(String source) =>
      source.isEmpty ? null : RegExp(source, caseSensitive: false);

  static Future<void> save(String source) =>
      _writes.synchronized(() => _save(source));

  static Future<void> prependTitle(String title) => _writes.synchronized(() async {
    if (title.trim().isEmpty) throw const FormatException('视频标题为空');
    final literal = RegExp.escape(title)
        .replaceAll('\r', r'\r')
        .replaceAll('\n', r'\n');
    final previous = text;
    await _save(previous.isEmpty ? literal : '$literal|$previous');
  });

  static Future<void> _save(String source) async {
    final compiled = compile(source);
    await GStorage.setting.put(_key, source);
    _compiled = compiled;
  }

  static String fromTxt(String text) => text
      .replaceFirst('\ufeff', '')
      .split(RegExp(r'\r\n?|\n'))
      .where((line) => line.trim().isNotEmpty)
      .join('|');
}
