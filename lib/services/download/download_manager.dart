import 'dart:async';
import 'dart:io';
import 'dart:math' as math;

import 'package:PiliPlus/http/init.dart';
import 'package:PiliPlus/utils/extension/string_ext.dart';
import 'package:dio/dio.dart';

/// 支持镜像切换、断点续传和分段并发的单媒体流下载器。
class DownloadManager {
  DownloadManager({
    required this.urls,
    required this.path,
    required this.onReceiveProgress,
    this.threadCount = 4,
  }) : task = Future<void>.value() {
    task = _run();
  }

  final List<String> urls;
  final String path;
  final void Function(int received, int total)? onReceiveProgress;
  final int threadCount;

  late Future<void> task;
  final List<CancelToken> _cancelTokens = [];
  bool _cancelled = false;
  int _lastProgressAt = 0;
  static final RegExp _contentRangePattern = RegExp(
    r'^bytes\s+(\d+)-(\d+)/(\d+)$',
    caseSensitive: false,
  );

  Future<void> _run() async {
    if (urls.isEmpty) {
      throw const FormatException('下载地址为空');
    }
    final total = await _probeLength();
    if (_cancelled) return;

    if (total == null || total < 4 * 1024 * 1024 || threadCount < 2) {
      await _downloadSingle(total);
      return;
    }
    await _downloadRanges(total);
  }

  Future<int?> _probeLength() async {
    Object? lastError;
    for (var round = 0; round < 3; round++) {
      for (final candidate in urls) {
        final token = CancelToken();
        _cancelTokens.add(token);
        try {
          final response = await Request.http11Dio.get<ResponseBody>(
            candidate.http2https,
            options: Options(
              headers: const {'range': 'bytes=0-0'},
              responseType: ResponseType.stream,
              validateStatus: (status) => status == 200 || status == 206,
              connectTimeout: const Duration(seconds: 30),
              receiveTimeout: const Duration(seconds: 60),
            ),
            cancelToken: token,
          );
          if (response.statusCode == 206) {
            final value = response.headers.value('content-range');
            final match = value == null
                ? null
                : _contentRangePattern.firstMatch(value.trim());
            final total = match == null
                ? null
                : int.tryParse(match.group(3)!);
            await response.data!.stream.drain<void>();
            final returnedEnd = match == null
                ? null
                : int.tryParse(match.group(2)!);
            if (match != null &&
                match.group(1) == '0' &&
                returnedEnd != null &&
                returnedEnd >= 0 &&
                total != null &&
                total > 0) {
              return total;
            }
            lastError = HttpException(
              '服务器返回的媒体长度范围不匹配: $value',
            );
          } else {
            token.cancel('range unsupported');
            return null;
          }
        } catch (e) {
          if (_cancelled) return null;
          lastError = e;
        } finally {
          _cancelTokens.remove(token);
        }
      }
    }
    throw lastError ?? const HttpException('无法读取媒体文件长度');
  }

  Future<void> _downloadSingle(int? knownTotal) async {
    final file = File(path);
    await file.parent.create(recursive: true);
    Object? lastError;

    for (var round = 0; round < 3; round++) {
      for (final candidate in urls) {
        if (_cancelled) return;
        var received = await file.exists() ? await file.length() : 0;
        final token = CancelToken();
        _cancelTokens.add(token);
        IOSink? sink;
        try {
          final response = await Request.http11Dio.get<ResponseBody>(
            candidate.http2https,
            options: Options(
              headers: received > 0 ? {'range': 'bytes=$received-'} : null,
              responseType: ResponseType.stream,
              validateStatus: (status) =>
                  status == 200 || status == 206 || status == 416,
              connectTimeout: const Duration(seconds: 30),
              receiveTimeout: const Duration(seconds: 60),
            ),
            cancelToken: token,
          );
          if (response.statusCode == 416 &&
              knownTotal != null &&
              received == knownTotal) {
            _emitProgress(received, knownTotal, force: true);
            return;
          }
          if (response.statusCode == 416) {
            await _delete(file);
            received = 0;
            continue;
          }
          if (received > 0 && response.statusCode == 200) {
            await _delete(file);
            received = 0;
          }
          sink = file.openWrite(
            mode: received == 0 ? FileMode.writeOnly : FileMode.writeOnlyAppend,
          );
          final data = response.data!;
          final total = knownTotal ?? data.contentLength + received;
          _emitProgress(received, total, force: true);
          await for (final chunk in data.stream) {
            if (_cancelled) return;
            sink.add(chunk);
            received += chunk.length;
            _emitProgress(received, total);
          }
          await sink.flush();
          await sink.close();
          sink = null;
          if (knownTotal != null && received != knownTotal) {
            throw HttpException('文件长度不完整：$received/$knownTotal');
          }
          _emitProgress(received, total, force: true);
          return;
        } catch (e) {
          if (_cancelled) return;
          lastError = e;
        } finally {
          try {
            await sink?.close();
          } catch (_) {}
          _cancelTokens.remove(token);
        }
      }
    }
    throw lastError ?? const HttpException('媒体下载失败');
  }

  Future<void> _downloadRanges(int total) async {
    final count = math.min(
      threadCount,
      math.max(2, total ~/ (2 * 1024 * 1024)),
    );
    final partSize = (total / count).ceil();
    final receivedByPart = List<int>.filled(count, 0);

    for (var index = 0; index < count; index++) {
      final start = index * partSize;
      final end = math.min(total - 1, start + partSize - 1);
      final file = File(_partPath(index));
      final expected = end - start + 1;
      if (await file.exists()) {
        final length = await file.length();
        if (length > expected) {
          await _delete(file);
        } else {
          receivedByPart[index] = length;
        }
      }
    }
    _emitProgress(receivedByPart.fold(0, (a, b) => a + b), total, force: true);

    await Future.wait([
      for (var index = 0; index < count; index++)
        _downloadPart(
          index: index,
          rangeStart: index * partSize,
          rangeEnd: math.min(total - 1, (index + 1) * partSize - 1),
          total: total,
          receivedByPart: receivedByPart,
        ),
    ], eagerError: true);
    if (_cancelled) return;

    final output = File(path);
    await output.parent.create(recursive: true);
    if (await output.exists()) await output.delete();
    final sink = output.openWrite(mode: FileMode.writeOnly);
    try {
      for (var index = 0; index < count; index++) {
        await for (final chunk in File(_partPath(index)).openRead()) {
          sink.add(chunk);
        }
      }
      await sink.flush();
    } finally {
      await sink.close();
    }
    if (await output.length() != total) {
      await _delete(output);
      throw const HttpException('分段合并后的文件长度不正确');
    }
    for (var index = 0; index < count; index++) {
      await _delete(File(_partPath(index)));
    }
    _emitProgress(total, total, force: true);
  }

  Future<void> _downloadPart({
    required int index,
    required int rangeStart,
    required int rangeEnd,
    required int total,
    required List<int> receivedByPart,
  }) async {
    final file = File(_partPath(index));
    await file.parent.create(recursive: true);
    final expected = rangeEnd - rangeStart + 1;
    if (receivedByPart[index] == expected) return;

    Object? lastError;
    for (var round = 0; round < 3; round++) {
      for (final candidate in urls) {
        if (_cancelled) return;
        var received = await file.exists() ? await file.length() : 0;
        if (received == expected) return;
        final token = CancelToken();
        _cancelTokens.add(token);
        IOSink? sink;
        try {
          final response = await Request.http11Dio.get<ResponseBody>(
            candidate.http2https,
            options: Options(
              headers: {'range': 'bytes=${rangeStart + received}-$rangeEnd'},
              responseType: ResponseType.stream,
              validateStatus: (status) => status == 206 || status == 416,
              connectTimeout: const Duration(seconds: 30),
              receiveTimeout: const Duration(seconds: 60),
            ),
            cancelToken: token,
          );
          if (response.statusCode == 416 && received == expected) return;
          if (response.statusCode != 206) {
            throw const HttpException('服务器不支持分段下载');
          }

          final data = response.data!;
          final requestedStart = rangeStart + received;
          final contentRange = response.headers.value('content-range');
          final match = contentRange == null
              ? null
              : _contentRangePattern.firstMatch(contentRange.trim());
          final returnedStart = match == null
              ? null
              : int.tryParse(match.group(1)!);
          final returnedEnd = match == null
              ? null
              : int.tryParse(match.group(2)!);
          final returnedTotal = match == null
              ? null
              : int.tryParse(match.group(3)!);
          if (returnedStart != requestedStart ||
              returnedEnd == null ||
              returnedEnd < requestedStart ||
              returnedEnd > rangeEnd ||
              returnedTotal != total) {
            await data.stream.drain<void>();
            throw HttpException(
              '服务器返回的分段范围不匹配: $contentRange',
            );
          }
          final remaining = returnedEnd - requestedStart + 1;
          if (data.contentLength >= 0 && data.contentLength != remaining) {
            await data.stream.drain<void>();
            throw HttpException(
              '服务器返回的分段长度不匹配: ${data.contentLength}/$remaining',
            );
          }

          sink = file.openWrite(
            mode: received == 0 ? FileMode.writeOnly : FileMode.writeOnlyAppend,
          );
          await for (final chunk in data.stream) {
            if (_cancelled) return;
            sink.add(chunk);
            received += chunk.length;
            receivedByPart[index] = received;
            _emitProgress(receivedByPart.fold(0, (a, b) => a + b), total);
          }
          await sink.flush();
          await sink.close();
          sink = null;
          if (received != expected) {
            throw HttpException('分段 $index 长度不完整：$received/$expected');
          }
          return;
        } catch (e) {
          if (_cancelled) return;
          lastError = e;
        } finally {
          try {
            await sink?.close();
          } catch (_) {}
          _cancelTokens.remove(token);
        }
      }
    }
    throw lastError ?? HttpException('分段 $index 下载失败');
  }

  String _partPath(int index) => '$path.part$index';

  void _emitProgress(int received, int total, {bool force = false}) {
    final now = DateTime.now().millisecondsSinceEpoch;
    if (force || now - _lastProgressAt >= 250) {
      _lastProgressAt = now;
      onReceiveProgress?.call(received, total);
    }
  }

  Future<void> cancel({required bool isDelete}) async {
    _cancelled = true;
    for (final token in List<CancelToken>.of(_cancelTokens)) {
      if (!token.isCancelled) token.cancel('cancelled');
    }
    try {
      await task;
    } catch (_) {}
    if (isDelete) {
      await _delete(File(path));
      for (var index = 0; index < threadCount; index++) {
        await _delete(File(_partPath(index)));
      }
    }
  }

  static Future<void> _delete(File file) async {
    if (await file.exists()) {
      try {
        await file.delete();
      } catch (_) {}
    }
  }
}
