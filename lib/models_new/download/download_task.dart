enum DownloadTaskStatus {
  waiting,
  downloading,
  paused,
  saving,
  done,
  failed;

  bool get isActive =>
      this == DownloadTaskStatus.waiting ||
      this == DownloadTaskStatus.downloading ||
      this == DownloadTaskStatus.saving;
}

class DownloadTask {
  DownloadTask({
    required this.aid,
    required this.bvid,
    required this.cid,
    required this.title,
    this.cover,
    this.ownerName,
    this.epid,
    this.seasonId,
    this.sourceType,
    this.qn = 80,
    this.audioQn = 30280,
    this.videoDescription,
    this.audioDescription,
    this.totalBytes = 0,
    this.downloadedBytes = 0,
    this.status = DownloadTaskStatus.waiting,
    this.error,
    this.videoPath,
    this.audioPath,
    this.outputPath,
    this.savedUri,
    int? updatedAt,
  }) : updatedAt = updatedAt ?? DateTime.now().millisecondsSinceEpoch;

  int aid;
  String bvid;
  int cid;
  String title;
  String? cover;
  String? ownerName;
  int? epid;
  int? seasonId;
  String? sourceType;
  int qn;
  int audioQn;
  String? videoDescription;
  String? audioDescription;
  int totalBytes;
  int downloadedBytes;
  DownloadTaskStatus status;
  String? error;
  String? videoPath;
  String? audioPath;
  String? outputPath;
  String? savedUri;
  int updatedAt;

  String get key => '${bvid}_$cid';

  DownloadTask.fromJson(Map<String, dynamic> json)
    : aid = json['aid'] as int? ?? 0,
      bvid = json['bvid'] as String? ?? '',
      cid = json['cid'] as int? ?? 0,
      title = json['title'] as String? ?? '',
      cover = json['cover'] as String?,
      ownerName = json['ownerName'] as String?,
      epid = json['epid'] as int?,
      seasonId = json['seasonId'] as int?,
      sourceType = json['sourceType'] as String?,
      qn = json['qn'] as int? ?? 80,
      audioQn = json['audioQn'] as int? ?? 30280,
      videoDescription = json['videoDescription'] as String?,
      audioDescription = json['audioDescription'] as String?,
      totalBytes = json['totalBytes'] as int? ?? 0,
      downloadedBytes = json['downloadedBytes'] as int? ?? 0,
      status = DownloadTaskStatus.values.firstWhere(
        (e) => e.name == json['status'],
        orElse: () => DownloadTaskStatus.failed,
      ),
      error = json['error'] as String?,
      videoPath = (json['videoPath'] ?? json['filePath']) as String?,
      audioPath = json['audioPath'] as String?,
      outputPath = json['outputPath'] as String?,
      savedUri = json['savedUri'] as String?,
      updatedAt = json['updatedAt'] as int? ?? 0;

  Map<String, dynamic> toJson() => {
    'aid': aid,
    'bvid': bvid,
    'cid': cid,
    'title': title,
    'cover': cover,
    'ownerName': ownerName,
    'epid': epid,
    'seasonId': seasonId,
    'sourceType': sourceType,
    'qn': qn,
    'audioQn': audioQn,
    'videoDescription': videoDescription,
    'audioDescription': audioDescription,
    'totalBytes': totalBytes,
    'downloadedBytes': downloadedBytes,
    'status': status.name,
    'error': error,
    'videoPath': videoPath,
    'audioPath': audioPath,
    'outputPath': outputPath,
    'savedUri': savedUri,
    'updatedAt': updatedAt,
  };
}
