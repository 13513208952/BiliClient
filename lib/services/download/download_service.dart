import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:PiliPlus/http/download.dart';
import 'package:PiliPlus/models_new/download/download_task.dart';
import 'package:PiliPlus/services/download/download_manager.dart';
import 'package:PiliPlus/services/download/download_platform.dart';
import 'package:PiliPlus/utils/path_utils.dart';
import 'package:PiliPlus/utils/platform_utils.dart';
import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart' show kDebugMode, debugPrint;
import 'package:flutter/services.dart' show PlatformException;
import 'package:get/get.dart';
import 'package:path/path.dart' as path;
import 'package:saver_gallery/saver_gallery.dart';

/// 下载任务落到系统视频目录；应用私有目录只保存队列元数据和未完成分段。
class DownloadService extends GetxService {
  static const _taskFile = 'download_tasks.json';
  static const _albumPath = 'BiliClient';

  final tasks = <DownloadTask>[].obs;
  late final Future<void> waitForInitialization = _load();

  DownloadTask? _current;
  DownloadManager? _videoManager;
  DownloadManager? _audioManager;
  final _pendingCids = <int>{};

  @override
  void onInit() {
    super.onInit();
    waitForInitialization;
  }

  String get _taskFilePath => path.join(appSupportDirPath, _taskFile);

  Future<void> _load() async {
    final file = File(_taskFilePath);
    if (!file.existsSync()) return;
    try {
      final list = jsonDecode(await file.readAsString()) as List;
      for (final item in list) {
        if (item is! Map) continue;
        final task = DownloadTask.fromJson(item.cast<String, dynamic>());
        if (task.status.isActive) task.status = DownloadTaskStatus.paused;
        tasks.add(task);
      }
      tasks.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    } catch (e) {
      if (kDebugMode) debugPrint('load download tasks error: $e');
    }
  }

  Future<void> _save() async {
    final file = File(_taskFilePath);
    try {
      if (!file.parent.existsSync()) {
        await file.parent.create(recursive: true);
      }
      await file.writeAsString(
        jsonEncode(tasks.map((e) => e.toJson()).toList()),
        flush: true,
      );
    } catch (e) {
      if (kDebugMode) debugPrint('save download tasks error: $e');
    }
  }

  Set<int> get pendingCids => tasks
      .where((e) => e.status != DownloadTaskStatus.failed)
      .map((e) => e.cid)
      .toSet();

  DownloadTask? findByCid(int cid) =>
      tasks.firstWhereOrNull((task) => task.cid == cid);

  Future<void> enqueue({
    required int aid,
    required String bvid,
    required int cid,
    required String title,
    String? cover,
    String? ownerName,
    int qn = 80,
    int audioQn = 30280,
    int? epid,
    int? seasonId,
    String? sourceType,
  }) async {
    await waitForInitialization;
    final existing = findByCid(cid);
    if (existing != null) {
      if (existing.status == DownloadTaskStatus.failed) await retry(existing);
      return;
    }
    if (!_pendingCids.add(cid)) return;
    try {
      final task = DownloadTask(
        aid: aid,
        bvid: bvid,
        cid: cid,
        title: title,
        cover: cover,
        ownerName: ownerName,
        epid: epid,
        seasonId: seasonId,
        sourceType: sourceType,
        qn: qn,
        audioQn: audioQn,
      );
      tasks.insert(0, task);
      tasks.refresh();
      await _save();
      _startNext();
    } finally {
      _pendingCids.remove(cid);
    }
  }

  void _startNext() {
    if (_current != null) return;
    final task = tasks.firstWhereOrNull(
      (e) => e.status == DownloadTaskStatus.waiting,
    );
    if (task == null) {
      unawaited(DownloadPlatform.stopForeground());
      return;
    }
    _current = task;
    task
      ..status = DownloadTaskStatus.downloading
      ..error = null
      ..updatedAt = DateTime.now().millisecondsSinceEpoch;
    tasks.refresh();
    unawaited(_runCurrent(task));
  }

  Future<void> _runCurrent(DownloadTask task) async {
    try {
      await DownloadPlatform.startForeground(task.title);
      final info = await DownloadHttp.resolve(
        avid: task.aid,
        bvid: task.bvid,
        cid: task.cid,
        qn: task.qn,
        audioQn: task.audioQn,
        epid: task.epid,
        seasonId: task.seasonId,
        sourceType: task.sourceType,
      );
      if (!identical(task, _current) ||
          task.status != DownloadTaskStatus.downloading) {
        return;
      }

      final baseName = '${task.bvid}_${task.cid}';
      task
        ..qn = info.qn
        ..audioQn = info.audioQn ?? task.audioQn
        ..videoDescription = info.videoDescription
        ..audioDescription = info.audioDescription
        ..videoPath ??= path.join(downloadPath, '$baseName.video')
        ..audioPath = info.needsMux
            ? (task.audioPath ?? path.join(downloadPath, '$baseName.audio'))
            : null
        ..outputPath = info.needsMux
            ? (task.outputPath ?? path.join(downloadPath, '$baseName.mp4'))
            : null;

      var videoReceived = 0;
      var videoTotal = 0;
      var audioReceived = 0;
      var audioTotal = 0;
      void updateProgress() {
        task
          ..downloadedBytes = videoReceived + audioReceived
          ..totalBytes = videoTotal + audioTotal;
        tasks.refresh();
      }

      _videoManager = DownloadManager(
        urls: info.videoUrls,
        path: task.videoPath!,
        onReceiveProgress: (received, total) {
          videoReceived = received;
          videoTotal = total;
          updateProgress();
        },
      );
      if (info.needsMux) {
        _audioManager = DownloadManager(
          urls: info.audioUrls!,
          path: task.audioPath!,
          onReceiveProgress: (received, total) {
            audioReceived = received;
            audioTotal = total;
            updateProgress();
          },
        );
      }
      await Future.wait([
        _videoManager!.task,
        if (_audioManager != null) _audioManager!.task,
      ], eagerError: true);
      if (!identical(task, _current) ||
          task.status != DownloadTaskStatus.downloading) {
        return;
      }
      await _finish(task, needsMux: info.needsMux);
    } catch (e) {
      await Future.wait([
        if (_videoManager != null) _videoManager!.cancel(isDelete: false),
        if (_audioManager != null) _audioManager!.cancel(isDelete: false),
      ]);
      if (identical(task, _current) &&
          task.status != DownloadTaskStatus.paused) {
        task
          ..status = DownloadTaskStatus.failed
          ..error = e.toString()
          ..redownloadOnRetry =
              e is PlatformException && e.code == 'mux_failed'
          ..updatedAt = DateTime.now().millisecondsSinceEpoch;
        tasks.refresh();
      }
    } finally {
      if (identical(task, _current)) {
        _current = null;
        _videoManager = null;
        _audioManager = null;
      }
      await _save();
      _startNext();
    }
  }

  Future<void> _finish(DownloadTask task, {required bool needsMux}) async {
    task.status = DownloadTaskStatus.saving;
    tasks.refresh();

    var source = File(task.videoPath!);
    if (!await source.exists()) throw const FileSystemException('视频文件不存在');
    if (needsMux) {
      final audio = File(task.audioPath!);
      if (!await audio.exists()) throw const FileSystemException('音频文件不存在');
      await DownloadPlatform.mux(
        videoPath: source.path,
        audioPath: audio.path,
        outputPath: task.outputPath!,
      );
      source = File(task.outputPath!);
      if (!await source.exists() || await source.length() == 0) {
        throw const FileSystemException('音视频合流失败');
      }
    }

    final fileName = _fileName(task);
    if (PlatformUtils.isMobile) {
      final result = await SaverGallery.saveFile(
        filePath: source.path,
        fileName: fileName,
        albumPath: _albumPath,
        skipIfExists: true,
      );
      if (!result.isSuccess) {
        throw result.errorMessage ?? '保存到系统视频目录失败';
      }
      task.savedUri = result.savedUri;
      await _deleteWorkFiles(task);
    } else {
      final target = File(path.join(downloadPath, fileName));
      if (target.path != source.path) {
        if (await target.exists()) await target.delete();
        await source.rename(target.path);
      }
      task.outputPath = target.path;
      await _deleteFile(task.videoPath, except: target.path);
      await _deleteFile(task.audioPath, except: target.path);
    }
    task
      ..status = DownloadTaskStatus.done
      ..error = null
      ..redownloadOnRetry = false
      ..downloadedBytes = task.totalBytes
      ..updatedAt = DateTime.now().millisecondsSinceEpoch;
    tasks.refresh();
  }

  static String _fileName(DownloadTask task) {
    final safeTitle = task.title
        .replaceAll(RegExp(r'[\\/:*?"<>|\r\n]'), '_')
        .trim();
    final name = safeTitle.isEmpty ? task.bvid : safeTitle;
    return '${name}_${task.cid}.mp4'.replaceFirst(RegExp(r'^\uFEFF'), '');
  }

  Future<void> pause(DownloadTask task) async {
    if (task.status == DownloadTaskStatus.saving ||
        task.status == DownloadTaskStatus.done) {
      return;
    }
    task
      ..status = DownloadTaskStatus.paused
      ..updatedAt = DateTime.now().millisecondsSinceEpoch;
    tasks.refresh();
    if (identical(task, _current)) {
      await Future.wait([
        if (_videoManager != null) _videoManager!.cancel(isDelete: false),
        if (_audioManager != null) _audioManager!.cancel(isDelete: false),
      ]);
    }
    await _save();
  }

  Future<void> resume(DownloadTask task) async {
    if (task.status == DownloadTaskStatus.done ||
        task.status == DownloadTaskStatus.saving) {
      return;
    }
    if (task.redownloadOnRetry) {
      await _deleteWorkFiles(task);
      task
        ..downloadedBytes = 0
        ..totalBytes = 0
        ..redownloadOnRetry = false;
    }
    task
      ..status = DownloadTaskStatus.waiting
      ..error = null;
    tasks.refresh();
    await _save();
    _startNext();
  }

  Future<void> retry(DownloadTask task) => resume(task);

  Future<void> cancel(DownloadTask task) async {
    task.status = DownloadTaskStatus.paused;
    if (identical(task, _current)) {
      await Future.wait([
        if (_videoManager != null) _videoManager!.cancel(isDelete: true),
        if (_audioManager != null) _audioManager!.cancel(isDelete: true),
      ]);
    }
    await _deleteWorkFiles(task);
    tasks.remove(task);
    tasks.refresh();
    await _save();
  }

  Future<void> removeRecord(DownloadTask task) async {
    if (task.status != DownloadTaskStatus.done) return;
    tasks.remove(task);
    tasks.refresh();
    await _save();
  }

  static Future<void> _deleteWorkFiles(DownloadTask task) async {
    await Future.wait([
      _deleteFile(task.videoPath),
      _deleteFile(task.audioPath),
      _deleteFile(task.outputPath),
    ]);
  }

  static Future<void> _deleteFile(String? filePath, {String? except}) async {
    if (filePath == null || filePath == except) return;
    final file = File(filePath);
    if (await file.exists()) {
      try {
        await file.delete();
      } catch (_) {}
    }
    for (var index = 0; index < 8; index++) {
      final part = File('$filePath.part$index');
      if (await part.exists()) {
        try {
          await part.delete();
        } catch (_) {}
      }
    }
  }
}
