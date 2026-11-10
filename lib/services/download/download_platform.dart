import 'dart:io';

import 'package:flutter/services.dart';

abstract final class DownloadPlatform {
  static const _channel = MethodChannel('com.bilibilitakemeback/download');

  static Future<void> startForeground(String title) async {
    if (Platform.isAndroid) {
      await _channel.invokeMethod<void>('startForeground', {'title': title});
    }
  }

  static Future<void> stopForeground() async {
    if (Platform.isAndroid) {
      await _channel.invokeMethod<void>('stopForeground');
    }
  }

  static Future<void> mux({
    required String videoPath,
    required String audioPath,
    required String outputPath,
  }) async {
    if (Platform.isAndroid) {
      await _channel.invokeMethod<void>('mux', {
        'videoPath': videoPath,
        'audioPath': audioPath,
        'outputPath': outputPath,
      });
      return;
    }
    final result = await Process.run('ffmpeg', [
      '-y',
      '-i',
      videoPath,
      '-i',
      audioPath,
      '-c',
      'copy',
      outputPath,
    ]);
    if (result.exitCode != 0) {
      throw ProcessException(
        'ffmpeg',
        const [],
        result.stderr?.toString() ?? '合流失败',
        result.exitCode,
      );
    }
  }
}
