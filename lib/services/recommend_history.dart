import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:PiliPlus/models/model_rec_video_item.dart';
import 'package:PiliPlus/services/recommend_history_db.dart';
import 'package:PiliPlus/utils/accounts.dart';
import 'package:PiliPlus/utils/id_utils.dart';
import 'package:PiliPlus/utils/storage.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

abstract final class RecommendHistory {
  static const _enabled = 'recommendHistoryEnabled';
  static const _folder = 'recommendHistoryFolder';
  static const _channel = MethodChannel('com.bilibilitakemeback/recommend-archive');
  static Future<RecommendHistoryDb>? _database;
  static Future<void>? _rotation;
  static (int, int, String?)? _lastRotation;
  static Object? archiveError;

  static bool get enabled => GStorage.setting.get(_enabled) == true;
  static String? get folder => GStorage.setting.get(_folder) as String?;
  static int get uid => Accounts.main.isLogin ? Accounts.main.mid : 0;
  static int get period => RecommendHistoryDb.periodAt(DateTime.now());
  static Future<void> setEnabled(bool value) => GStorage.setting.put(_enabled, value);

  static Future<RecommendHistoryDb> get database => _database ??= _open();

  static Future<RecommendHistoryDb> _open() async {
    try {
      final directory = await getApplicationSupportDirectory();
      return RecommendHistoryDb('${directory.path}/recommend-history.sqlite');
    } catch (_) {
      _database = null;
      rethrow;
    }
  }

  static int? aidOf(dynamic item) {
    if (item is! BaseRcmdVideoItemModel || item.goto != 'av') return null;
    final aid = item.aid;
    if (aid != null && aid > 0 && aid < IdUtils.MAX_AID) return aid;
    final bv = item.bvid;
    if (bv == null || !IdUtils.bvRegexExact.hasMatch(bv)) return null;
    final normalized = 'BV${bv.substring(2)}';
    if (normalized.codeUnits.skip(3).any((c) => !IdUtils.invData.containsKey(c))) return null;
    final decoded = IdUtils.bv2av(normalized);
    return decoded > 0 && IdUtils.av2bv(decoded) == normalized ? decoded : null;
  }

  static Future<void> chooseFolder() async {
    final chosen = Platform.isAndroid
        ? await _channel.invokeMethod<String>('choose')
        : await FilePicker.getDirectoryPath();
    if (chosen == null) return;
    await GStorage.setting.put(_folder, chosen);
    await rotate(uid, period, retry: true);
  }

  static Future<void> rotate(int account, int cycle, {bool retry = false}) async {
    final destination = folder;
    final key = (account, cycle, destination);
    while (_rotation != null) {
      await _rotation;
    }
    if (!retry && _lastRotation == key) return;
    final work = _rotate(account, cycle, destination);
    _rotation = work;
    try {
      await work;
      _lastRotation = key;
    } finally {
      if (identical(_rotation, work)) _rotation = null;
    }
  }

  static Future<void> _rotate(int account, int cycle, String? destination) async {
    try {
      final db = await database;
      for (final old in db.expired(account, cycle)) {
        if (destination == null) throw StateError('请指定归档目录，上期数据已保留');
        await _export(db, account, old, destination);
        db.deleteArchived(account, old);
      }
      archiveError = null;
    } catch (error) {
      archiveError = error;
    }
  }

  static Future<void> exportCurrent() async {
    final destination = folder;
    if (destination == null) throw StateError('请先指定归档目录');
    final account = uid;
    final cycle = period;
    await _export(await database, account, cycle, destination, manual: true);
  }

  static Future<int> diskBytes() async {
    final directory = await getApplicationSupportDirectory();
    final base = '${directory.path}/recommend-history.sqlite';
    var bytes = 0;
    for (final suffix in ['', '-wal', '-shm']) {
      final file = File('$base$suffix');
      if (await file.exists()) bytes += await file.length();
    }
    return bytes;
  }

  static Future<void> _export(RecommendHistoryDb db, int account, int cycle,
      String destination, {bool manual = false}) async {
    final label = RecommendHistoryDb.periodLabel(cycle);
    final suffix = manual ? '_manual_${DateTime.now().millisecondsSinceEpoch}' : '';
    final name = 'rcmd-blocked_uid${account}_$label$suffix.csv.gz';
    final temp = await (await getTemporaryDirectory()).createTemp('recommend-archive-');
    try {
      final file = File('${temp.path}/$name');
      final sink = file.openWrite();
      // Build a consistent snapshot without yielding between SQLite blocks.
      final encoder = utf8.encoder.fuse(gzip.encoder).startChunkedConversion(sink);
      try {
        encoder.add('av,bv,blocked_at_utc\n');
        final chunk = StringBuffer();
        for (final (aid, ms) in db.events(account, cycle)) {
          final stamp = DateTime.fromMillisecondsSinceEpoch(ms, isUtc: true).toIso8601String();
          chunk.writeln('av$aid,${IdUtils.av2bv(aid)},$stamp');
          if (chunk.length >= 32768) {
            encoder.add(chunk.toString());
            chunk.clear();
          }
        }
        if (chunk.isNotEmpty) encoder.add(chunk.toString());
      } finally {
        encoder.close();
        await sink.done;
      }
      if (Platform.isAndroid) {
        await _channel.invokeMethod<void>('write', {
          'directory': destination, 'name': name, 'source': file.path,
        });
      } else {
        final target = await file.copy('$destination/.$name.part');
        await target.rename('$destination/$name');
      }
    } finally {
      await temp.delete(recursive: true);
    }
  }
}
