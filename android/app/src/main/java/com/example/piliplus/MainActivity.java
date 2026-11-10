package com.example.piliplus;

import android.Manifest;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ryanheise.audioservice.AudioService;
import com.ryanheise.audioservice.AudioServiceActivity;
import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.ReturnCode;

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
                    muxExecutor.execute(
                            () -> mux(videoPath, audioPath, outputPath, result));
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
            MethodChannel.Result result) {
        File output = new File(outputPath);
        if (output.exists() && !output.delete()) {
            runOnUiThread(
                    () -> result.error("mux_failed", "无法覆盖旧的合流文件", null));
            return;
        }
        String command = "-y -i " + quote(videoPath)
                + " -i " + quote(audioPath)
                + " -map 0:v:0 -map 1:a:0 -c copy -movflags +faststart "
                + quote(outputPath);
        final var session = FFmpegKit.execute(command);
        if (ReturnCode.isSuccess(session.getReturnCode())) {
            runOnUiThread(() -> result.success(null));
        } else {
            String rawLog = session.getAllLogsAsString();
            final String log = rawLog != null && rawLog.length() > 4000
                    ? rawLog.substring(rawLog.length() - 4000)
                    : rawLog;
            runOnUiThread(
                    () -> result.error(
                            "mux_failed",
                            log == null || log.isEmpty() ? "音视频合流失败" : log,
                            null));
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

}
