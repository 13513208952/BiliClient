import 'dart:async';
import 'dart:collection';

import 'package:PiliPlus/grpc/bilibili/main/community/reply/v1.pb.dart'
    show ReplyInfo;
import 'package:PiliPlus/http/member.dart';
import 'package:PiliPlus/utils/storage.dart';
import 'package:PiliPlus/utils/storage_key.dart';
import 'package:dio/dio.dart';

abstract final class ReplyAuthorFilter {
  static const firstRegistrationYear = 2020;
  static const _maxCachedUsers = 1024;
  static const _maxConcurrentLookups = 4;
  static const _maxLookupsPerMinute = 12;
  static const _lookupTimeout = Duration(seconds: 2);
  static const _failureCooldown = Duration(minutes: 1);
  static const _unknownYearTtl = Duration(minutes: 15);
  static const _chinaOffset = Duration(hours: 8);

  static RegExp? _namePattern = _readNamePattern();
  static Set<int> _levels = _readSelection(SettingBoxKey.replyAuthorLevels);
  static Set<int> _registrationYears = _readSelection(
    SettingBoxKey.replyAuthorRegistrationYears,
  );

  static String get namePattern => _namePattern?.pattern ?? '';
  static Iterable<int> get levels => _levels;
  static Iterable<int> get registrationYears => _registrationYears;
  static bool get hasNameOrLevelFilter =>
      _namePattern != null || _levels.isNotEmpty;
  static bool get hasRegistrationYearFilter => _registrationYears.isNotEmpty;

  static final _clock = Stopwatch()..start();
  static final _lookupStarts = Queue<int>();
  static final _inFlight = <int, Future<void>>{};
  static final _years = <int, ({int? year, int expiresAt})>{};
  static int _nextLookupAt = 0;

  static RegExp? compileNamePattern(String value) =>
      value.isEmpty ? null : RegExp(value, caseSensitive: false);

  static RegExp? _readNamePattern() {
    final value = GStorage.setting.get(SettingBoxKey.replyAuthorNamePattern);
    if (value is! String) return null;
    try {
      return compileNamePattern(value);
    } on FormatException {
      return null;
    }
  }

  static Set<int> _readSelection(String key) {
    final value = GStorage.setting.get(key);
    return value is List ? value.whereType<int>().toSet() : <int>{};
  }

  static Future<void> saveNamePattern(RegExp? value) async {
    await GStorage.setting.put(
      SettingBoxKey.replyAuthorNamePattern,
      value?.pattern ?? '',
    );
    _namePattern = value;
  }

  static Future<void> saveLevels(Set<int> value) async {
    await GStorage.setting.put(SettingBoxKey.replyAuthorLevels, value.toList());
    _levels = value;
  }

  static Future<void> saveRegistrationYears(Set<int> value) async {
    await GStorage.setting.put(
      SettingBoxKey.replyAuthorRegistrationYears,
      value.toList(),
    );
    _registrationYears = value;
  }

  static bool matchesNameOrLevel(ReplyInfo reply) {
    if (!hasNameOrLevelFilter) return false;
    final member = reply.member;
    final basic = reply.memberV2.basic;
    if (_namePattern case final pattern?) {
      final name = member.name.isNotEmpty ? member.name : basic.name;
      if (name.isNotEmpty && pattern.hasMatch(name)) return true;
    }
    if (_levels.isNotEmpty) {
      final level = member.hasLevel() ? member.level : basic.level;
      final number = level.toInt();
      if (number >= 1 && number <= 6 && _levels.contains(number)) return true;
    }
    return false;
  }

  static bool matchesRegistrationYear(ReplyInfo reply) {
    if (!hasRegistrationYearFilter) return false;
    final year = _years[_authorId(reply)]?.year;
    return year != null &&
        year >= firstRegistrationYear &&
        _registrationYears.contains(year);
  }

  static bool matchesCachedAuthor(ReplyInfo reply) =>
      matchesNameOrLevel(reply) || matchesRegistrationYear(reply);

  static Future<bool> hidesPublishedReply(ReplyInfo reply) async {
    if (matchesNameOrLevel(reply)) return true;
    if (!hasRegistrationYearFilter) return false;
    final lookup = prepareRegistrationYears([reply]);
    if (lookup != null) await lookup;
    return matchesRegistrationYear(reply);
  }

  static int _authorId(ReplyInfo reply) {
    final mid = reply.mid.toInt();
    if (mid > 0) return mid;
    final memberMid = reply.member.mid.toInt();
    return memberMid > 0 ? memberMid : reply.memberV2.basic.mid.toInt();
  }

  // There is no registration date in ReplyInfo/Member/MemberV2.Basic.
  // Query only when the year filter is enabled, sharing a small global budget
  // between pages and keeping both successful and unavailable results cached.
  static Future<void>? prepareRegistrationYears(Iterable<ReplyInfo> roots) {
    if (!hasRegistrationYearFilter) return null;

    final pending = <Future<void>>{};
    final now = _clock.elapsedMilliseconds;
    while (_lookupStarts.isNotEmpty &&
        now - _lookupStarts.first >= Duration.millisecondsPerMinute) {
      _lookupStarts.removeFirst();
    }

    void visit(Iterable<ReplyInfo> replies) {
      for (final reply in replies) {
        final mid = _authorId(reply);
        if (mid > 0) {
          final cached = _years.remove(mid);
          if (cached != null && (cached.year != null || cached.expiresAt > now)) {
            _years[mid] = cached;
          } else if (_inFlight[mid] case final lookup?) {
            pending.add(lookup);
          } else if (_inFlight.length < _maxConcurrentLookups &&
              now >= _nextLookupAt &&
              _lookupStarts.length < _maxLookupsPerMinute) {
            _lookupStarts.add(now);
            pending.add(_inFlight[mid] = _lookupYear(mid));
          }
        }
        if (reply.replies.isNotEmpty) visit(reply.replies);
      }
    }

    visit(roots);
    return pending.isEmpty ? null : Future.wait<void>(pending).then<void>((_) {});
  }

  static Future<void> _lookupYear(int mid) async {
    final token = CancelToken();
    final timeout = Timer(_lookupTimeout, token.cancel);
    int? year;
    try {
      final result = await MemberHttp.space(
        mid: mid,
        cancelToken: token,
        publicLookup: true,
      );
      if (!result.isSuccess) {
        _pauseLookups();
        return;
      }
      // Older versions of the public app profile return zero here instead of
      // a registration time. Do not use UID ranges or account activity dates.
      final seconds = result.dataOrNull?.card?.regtime;
      final now = DateTime.now();
      if (seconds != null &&
          seconds > 0 &&
          seconds <= now.millisecondsSinceEpoch ~/ 1000) {
        year = DateTime.fromMillisecondsSinceEpoch(
          seconds * 1000,
          isUtc: true,
        ).add(_chinaOffset).year;
      }
    } catch (_) {
      _pauseLookups();
      // Missing, private, or malformed profile data must not hide a reply.
    } finally {
      timeout.cancel();
      _inFlight.remove(mid);
      if (_years.length == _maxCachedUsers) _years.remove(_years.keys.first);
      _years[mid] = (
        year: year,
        expiresAt: _clock.elapsedMilliseconds + _unknownYearTtl.inMilliseconds,
      );
    }
  }

  static void _pauseLookups() {
    _nextLookupAt = _clock.elapsedMilliseconds + _failureCooldown.inMilliseconds;
  }
}
