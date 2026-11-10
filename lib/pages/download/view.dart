import 'package:PiliPlus/common/widgets/image/network_img_layer.dart';
import 'package:PiliPlus/models_new/download/download_task.dart';
import 'package:PiliPlus/services/download/download_service.dart';
import 'package:PiliPlus/utils/cache_manager.dart';
import 'package:PiliPlus/utils/platform_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class DownloadPage extends StatefulWidget {
  const DownloadPage({super.key});

  @override
  State<DownloadPage> createState() => _DownloadPageState();
}

class _DownloadPageState extends State<DownloadPage> {
  final _service = Get.find<DownloadService>();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('下载')),
      body: Obx(() {
        final tasks = _service.tasks;
        if (tasks.isEmpty) {
          return Center(
            child: Text(
              '暂无下载任务',
              style: TextStyle(color: theme.colorScheme.outline),
            ),
          );
        }
        return ListView.separated(
          padding: EdgeInsets.only(
            top: 8,
            bottom: MediaQuery.viewPaddingOf(context).bottom + 24,
          ),
          itemCount: tasks.length,
          separatorBuilder: (_, _) => const Divider(height: 1, indent: 16),
          itemBuilder: (context, index) => _buildItem(theme, tasks[index]),
        );
      }),
    );
  }

  Widget _buildItem(ThemeData theme, DownloadTask task) {
    final progress = task.totalBytes <= 0
        ? null
        : (task.downloadedBytes / task.totalBytes).clamp(0.0, 1.0);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ClipRRect(
            borderRadius: const BorderRadius.all(Radius.circular(6)),
            child: NetworkImgLayer(
              src: task.cover,
              width: 96,
              height: 60,
              type: .emote,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  task.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium,
                ),
                const SizedBox(height: 4),
                Text(
                  _statusText(task),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: task.status == DownloadTaskStatus.failed
                        ? theme.colorScheme.error
                        : theme.colorScheme.outline,
                  ),
                ),
                if (progress != null &&
                    task.status != DownloadTaskStatus.done) ...[
                  const SizedBox(height: 6),
                  LinearProgressIndicator(value: progress, minHeight: 3),
                ],
              ],
            ),
          ),
          const SizedBox(width: 4),
          _actions(task),
        ],
      ),
    );
  }

  Widget _actions(DownloadTask task) {
    return switch (task.status) {
      DownloadTaskStatus.waiting ||
      DownloadTaskStatus.downloading => IconButton(
        tooltip: '暂停',
        icon: const Icon(Icons.pause_circle_outline),
        onPressed: () => _service.pause(task),
      ),
      DownloadTaskStatus.saving => const Padding(
        padding: EdgeInsets.all(12),
        child: SizedBox.square(
          dimension: 20,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      ),
      DownloadTaskStatus.paused => IconButton(
        tooltip: '继续',
        icon: const Icon(Icons.play_circle_outline),
        onPressed: () => _service.resume(task),
      ),
      DownloadTaskStatus.failed => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            tooltip: '重试',
            icon: const Icon(Icons.refresh),
            onPressed: () => _service.retry(task),
          ),
          IconButton(
            tooltip: '删除',
            icon: const Icon(Icons.delete_outline),
            onPressed: () => _service.cancel(task),
          ),
        ],
      ),
      DownloadTaskStatus.done => IconButton(
        tooltip: '删除记录',
        icon: const Icon(Icons.delete_outline),
        onPressed: () => _service.removeRecord(task),
      ),
    };
  }

  String _statusText(DownloadTask task) {
    final quality = [
      task.videoDescription,
      task.audioDescription,
    ].whereType<String>().join(' · ');
    final prefix = quality.isEmpty ? '' : '$quality · ';
    return switch (task.status) {
      DownloadTaskStatus.waiting => '$prefix等待下载',
      DownloadTaskStatus.downloading =>
        task.totalBytes > 0
            ? '$prefix${CacheManager.formatSize(task.downloadedBytes)}'
                  ' / ${CacheManager.formatSize(task.totalBytes)}'
            : '$prefix下载中',
      DownloadTaskStatus.paused =>
        task.totalBytes > 0
            ? '$prefix已暂停 ${CacheManager.formatSize(task.downloadedBytes)}'
                  ' / ${CacheManager.formatSize(task.totalBytes)}'
            : '$prefix已暂停',
      DownloadTaskStatus.saving => '$prefix正在合流并保存到系统视频目录',
      DownloadTaskStatus.done =>
        PlatformUtils.isMobile
            ? '$prefix已保存到系统视频目录'
            : (task.outputPath ?? '$prefix已保存'),
      DownloadTaskStatus.failed => task.error ?? '下载失败',
    };
  }
}
