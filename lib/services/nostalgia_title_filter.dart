import 'package:PiliPlus/utils/storage.dart';

abstract final class NostalgiaTitleFilter {
  static const _key = 'nostalgiaTitleFilter';
  static String get text => GStorage.setting.get(_key, defaultValue: '');
  static RegExp? _compiled = compile(text);

  static RegExp? get pattern => _compiled;

  static RegExp? compile(String source) =>
      source.isEmpty ? null : RegExp(source, caseSensitive: false);

  static Future<void> save(String source) async {
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
