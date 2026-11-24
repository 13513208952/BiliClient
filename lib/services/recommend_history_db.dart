import 'dart:io';
import 'dart:typed_data';

import 'package:sqlite3/sqlite3.dart';

/// Local state for the regular homepage feed only. All keys are canonical AIDs.
final class RecommendHistoryDb {
  RecommendHistoryDb(String path) : _db = sqlite3.open(path) {
    _db.execute('PRAGMA journal_mode = WAL');
    _db.execute('''
      CREATE TABLE IF NOT EXISTS cycles (
        uid INTEGER NOT NULL, period INTEGER NOT NULL,
        PRIMARY KEY (uid, period)
      ) WITHOUT ROWID;
      CREATE TABLE IF NOT EXISTS shown (
        uid INTEGER NOT NULL, period INTEGER NOT NULL, bucket INTEGER NOT NULL,
        count INTEGER NOT NULL, payload BLOB NOT NULL,
        PRIMARY KEY (uid, period, bucket)
      ) WITHOUT ROWID;
      CREATE TABLE IF NOT EXISTS events (
        uid INTEGER NOT NULL, period INTEGER NOT NULL, seq INTEGER NOT NULL,
        aid INTEGER NOT NULL, ms INTEGER NOT NULL,
        PRIMARY KEY (uid, period, seq)
      ) WITHOUT ROWID;
      CREATE TABLE IF NOT EXISTS blocks (
        uid INTEGER NOT NULL, period INTEGER NOT NULL, seq INTEGER NOT NULL,
        count INTEGER NOT NULL, payload BLOB NOT NULL,
        PRIMARY KEY (uid, period, seq)
      ) WITHOUT ROWID;
    ''');
  }

  final Database _db;
  static const _bucketBits = 12;
  static const _bucketMask = (1 << _bucketBits) - 1;
  static const _blockSize = 1024;

  static int periodAt(DateTime local) => local.year * 2 + (local.month > 6 ? 1 : 0);

  static String periodLabel(int period) => '${period ~/ 2}-H${period % 2 + 1}';

  void close() => _db.close();

  List<int> expired(int uid, int period) => [
    for (final row in _db.select(
      'SELECT period FROM cycles WHERE uid=? AND period<? ORDER BY period',
      [uid, period],
    )) row['period'] as int,
  ];

  void _transaction(void Function() action) {
    _db.execute('BEGIN IMMEDIATE');
    try {
      action();
      _db.execute('COMMIT');
    } catch (_) {
      _db.execute('ROLLBACK');
      rethrow;
    }
  }

  void _touch(int uid, int period) => _db.execute(
    'INSERT OR IGNORE INTO cycles VALUES (?,?)', [uid, period],
  );

  Set<int> _readBucket(int uid, int period, int bucket) {
    final rows = _db.select(
      'SELECT payload FROM shown WHERE uid=? AND period=? AND bucket=?',
      [uid, period, bucket],
    );
    if (rows.isEmpty) return {};
    var value = 0;
    return {for (final delta in _unpack(rows.single['payload'] as Uint8List)) value += delta};
  }

  Set<int> shownAmong(int uid, int period, Iterable<int> aids) {
    final buckets = <int, Set<int>>{};
    return {
      for (final aid in aids)
        if (buckets.putIfAbsent(aid >> _bucketBits,
            () => _readBucket(uid, period, aid >> _bucketBits)).contains(aid & _bucketMask)) aid,
    };
  }

  void recordShown(int uid, int period, Iterable<int> aids) {
    final grouped = <int, Set<int>>{};
    for (final aid in aids) {
      (grouped[aid >> _bucketBits] ??= {}).add(aid & _bucketMask);
    }
    if (grouped.isEmpty) return;
    _transaction(() {
      _touch(uid, period);
      for (final entry in grouped.entries) {
        final offsets = _readBucket(uid, period, entry.key);
        final before = offsets.length;
        offsets.addAll(entry.value);
        if (offsets.length == before) continue;
        var last = 0;
        final deltas = <int>[];
        for (final offset in offsets.toList()..sort()) {
          deltas.add(offset - last);
          last = offset;
        }
        _db.execute('INSERT OR REPLACE INTO shown VALUES (?,?,?,?,?)',
          [uid, period, entry.key, offsets.length, _pack(deltas)]);
      }
    });
  }

  void recordBlocked(int uid, int period, List<int> aids, int ms) {
    if (aids.isEmpty) return;
    _transaction(() {
      _touch(uid, period);
      var seq = _db.select('''
        SELECT MAX(COALESCE((SELECT seq+count-1 FROM blocks
          WHERE uid=? AND period=? ORDER BY seq DESC LIMIT 1),0),
          COALESCE((SELECT MAX(seq) FROM events WHERE uid=? AND period=?),0)) AS last
      ''', [uid, period, uid, period]).single['last'] as int;
      final insert = _db.prepare('INSERT INTO events VALUES (?,?,?,?,?)');
      try {
        for (final aid in aids) {
          insert.execute([uid, period, ++seq, aid, ms]);
        }
      } finally {
        insert.close();
      }
      while (true) {
        final rows = _db.select('SELECT seq,aid,ms FROM events WHERE uid=? AND period=? ORDER BY seq LIMIT $_blockSize', [uid, period]);
        if (rows.length < _blockSize) break;
        final first = rows.first['seq'] as int;
        final payload = <int>[];
        var lastMs = 0;
        for (final row in rows) {
          final time = row['ms'] as int;
          final delta = time - lastMs;
          payload..add(row['aid'] as int)..add(delta < 0 ? -2 * delta - 1 : 2 * delta);
          lastMs = time;
        }
        _db.execute('INSERT INTO blocks VALUES (?,?,?,?,?)', [uid, period, first, rows.length, _pack(payload)]);
        _db.execute('DELETE FROM events WHERE uid=? AND period=? AND seq<=?', [uid, period, rows.last['seq']]);
      }
    });
  }

  Iterable<(int aid, int ms)> events(int uid, int period) sync* {
    var after = 0;
    while (true) {
      final rows = _db.select('SELECT seq,payload FROM blocks WHERE uid=? AND period=? AND seq>? ORDER BY seq LIMIT 1', [uid, period, after]);
      if (rows.isEmpty) break;
      final row = rows.single;
      after = row['seq'] as int;
      final values = _unpack(row['payload'] as Uint8List).iterator;
      var ms = 0;
      while (values.moveNext()) {
        final aid = values.current;
        if (!values.moveNext()) throw const FormatException('Incomplete event');
        final delta = values.current;
        ms += delta.isOdd ? -((delta + 1) ~/ 2) : delta ~/ 2;
        yield (aid, ms);
      }
    }
    for (final row in _db.select('SELECT aid,ms FROM events WHERE uid=? AND period=? ORDER BY seq', [uid, period])) {
      yield (row['aid'] as int, row['ms'] as int);
    }
  }

  ({int shown, int blocked}) stats(int uid, int period) {
    final row = _db.select('''
      SELECT COALESCE((SELECT SUM(count) FROM shown WHERE uid=? AND period=?),0) AS shown,
        COALESCE((SELECT SUM(count) FROM blocks WHERE uid=? AND period=?),0) +
        (SELECT COUNT(*) FROM events WHERE uid=? AND period=?) AS blocked
    ''', [uid, period, uid, period, uid, period]).single;
    return (shown: row['shown'] as int, blocked: row['blocked'] as int);
  }

  /// Call only after a completed archive has been written to the user's folder.
  void deleteArchived(int uid, int period) => _transaction(() {
    for (final table in ['shown', 'blocks', 'events', 'cycles']) {
      _db.execute('DELETE FROM $table WHERE uid=? AND period=?', [uid, period]);
    }
  });

  static Uint8List _pack(List<int> values) {
    final bytes = BytesBuilder(copy: false);
    for (var value in values) {
      while (value >= 128) {
        bytes.addByte((value & 127) | 128);
        value >>= 7;
      }
      bytes.addByte(value);
    }
    final raw = bytes.takeBytes();
    if (raw.length >= 32) {
      final compressed = zlib.encode(raw);
      if (compressed.length < raw.length) return Uint8List.fromList([1, ...compressed]);
    }
    return Uint8List.fromList([0, ...raw]);
  }

  static Iterable<int> _unpack(Uint8List payload) sync* {
    final body = Uint8List.sublistView(payload, 1);
    final bytes = payload.first == 0 ? body : zlib.decode(body);
    var value = 0;
    var shift = 0;
    for (final byte in bytes) {
      value |= (byte & 127) << shift;
      if (byte < 128) {
        yield value;
        value = 0;
        shift = 0;
      } else {
        shift += 7;
      }
    }
    if (shift != 0) throw const FormatException('Incomplete varint');
  }
}
