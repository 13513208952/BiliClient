package com.example.piliplus;

import android.Manifest;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ryanheise.audioservice.AudioService;
import com.ryanheise.audioservice.AudioServiceActivity;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends AudioServiceActivity {
    private static final String DOWNLOAD_CHANNEL = "com.bilibilitakemeback/download";
    private final ExecutorService muxExecutor = Executors.newSingleThreadExecutor();
    private MethodChannel downloadChannel;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        AndroidHelper.attachActivity(this);
        downloadChannel = new MethodChannel(
                flutterEngine.getDartExecutor().getBinaryMessenger(),
                DOWNLOAD_CHANNEL);
        downloadChannel.setMethodCallHandler((call, result) -> {
            switch (call.method) {
                case "startForeground": {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(
                                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                11011);
                    }
                    String title = call.argument("title");
                    DownloadForegroundService.start(
                            getApplicationContext(),
                            title == null ? "" : title);
                    result.success(null);
                    break;
                }
                case "stopForeground":
                    DownloadForegroundService.stop(getApplicationContext());
                    result.success(null);
                    break;
                case "mux": {
                    String videoPath = call.argument("videoPath");
                    String audioPath = call.argument("audioPath");
                    String outputPath = call.argument("outputPath");
                    if (videoPath == null || audioPath == null || outputPath == null) {
                        result.error("invalid_arguments", "合流参数不完整", null);
                        break;
                    }
                    muxExecutor.execute(() -> {
                        try {
                            mux(videoPath, audioPath, outputPath, result);
                        } catch (Throwable error) {
                            new File(outputPath).delete();
                            String message = error.getMessage();
                            runOnUiThread(
                                    () -> result.error(
                                            "mux_failed",
                                            message == null || message.isEmpty()
                                                    ? "音视频合流失败"
                                                    : message,
                                            null));
                        }
                    });
                    break;
                }
                default:
                    result.notImplemented();
                    break;
            }
        });
    }

    @Override
    public void cleanUpFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        if (downloadChannel != null) {
            downloadChannel.setMethodCallHandler(null);
            downloadChannel = null;
        }
        AndroidHelper.detachActivity(this);
        super.cleanUpFlutterEngine(flutterEngine);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (AndroidHelper.isFoldable) {
            Runnable callback = AndroidHelper.ToDart.onConfigurationChanged;
            if (callback != null) {
                callback.run();
            }
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        Runnable callback = AndroidHelper.ToDart.onUserLeaveHint;
        if (callback != null) {
            callback.run();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode, @Nullable Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        AndroidHelper.isPipMode = isInPictureInPictureMode;
    }

    void closeApp() {
        if (!isFinishing() && !isDestroyed()) {
            AndroidHelper.ToDart.onUserLeaveHint = null;
            AndroidHelper.ToDart.onConfigurationChanged = null;
            stopService(new Intent(this, AudioService.class));
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        AndroidHelper.ToDart.onUserLeaveHint = null;
        AndroidHelper.ToDart.onConfigurationChanged = null;
        AndroidHelper.detachActivity(this);
        stopService(new Intent(this, AudioService.class));
        super.onDestroy();
    }
    private void mux(
            String videoPath,
            String audioPath,
            String outputPath,
            MethodChannel.Result result) throws IOException {
        File output = new File(outputPath);
        if (output.exists() && !output.delete()) {
            throw new IOException("无法覆盖旧的合流文件");
        }

        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        try {
            videoExtractor.setDataSource(videoPath);
            audioExtractor.setDataSource(audioPath);
            int videoTrack = findTrack(videoExtractor, "video/");
            int audioTrack = findTrack(audioExtractor, "audio/");
            if (videoTrack < 0 || audioTrack < 0) {
                throw new IOException("下载文件缺少音频或视频轨道");
            }

            MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrack);
            MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrack);
            muxer = new MediaMuxer(
                    outputPath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outputVideoTrack = muxer.addTrack(videoFormat);
            int outputAudioTrack = muxer.addTrack(audioFormat);
            muxer.start();
            muxerStarted = true;

            int bufferSize = Math.max(
                    maxInputSize(videoFormat),
                    maxInputSize(audioFormat));
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            writeTrack(videoExtractor, videoTrack, muxer, outputVideoTrack, buffer);
            writeTrack(audioExtractor, audioTrack, muxer, outputAudioTrack, buffer);
        } finally {
            videoExtractor.release();
            audioExtractor.release();
            if (muxer != null) {
                try {
                    if (muxerStarted) muxer.stop();
                } finally {
                    muxer.release();
                }
            }
        }
        runOnUiThread(() -> result.success(null));
    }

    private static int findTrack(MediaExtractor extractor, String mimePrefix) {
        for (int index = 0; index < extractor.getTrackCount(); index++) {
            String mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(mimePrefix)) return index;
        }
        return -1;
    }

    private static int maxInputSize(MediaFormat format) {
        return Math.max(
                1024 * 1024,
                format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)
                        ? format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        : 0);
    }

    private static void writeTrack(
            MediaExtractor extractor,
            int inputTrack,
            MediaMuxer muxer,
            int outputTrack,
            ByteBuffer buffer) {
        extractor.selectTrack(inputTrack);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            buffer.clear();
            int size = extractor.readSampleData(buffer, 0);
            if (size < 0) break;
            info.offset = 0;
            info.size = size;
            info.presentationTimeUs = Math.max(0, extractor.getSampleTime());
            info.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    ? MediaCodec.BUFFER_FLAG_KEY_FRAME
                    : 0;
            muxer.writeSampleData(outputTrack, buffer, info);
            extractor.advance();
        }
    }

}
