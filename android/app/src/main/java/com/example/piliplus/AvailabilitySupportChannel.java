package com.example.piliplus;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

final class AvailabilitySupportChannel {
    private static final String CHANNEL = "com.bilibilitakemeback/availability-support";
    private static final int IMPORT_REQUEST = 0x4151;
    private static final int EXPORT_REQUEST = 0x4152;
    private static final Executor IO = Executors.newSingleThreadExecutor();

    private final Activity activity;
    private final Context context;
    private final MethodChannel channel;
    private MethodChannel.Result pickerResult;
    private int pickerRequest;

    AvailabilitySupportChannel(Activity activity, BinaryMessenger messenger) {
        this.activity = activity;
        context = activity.getApplicationContext();
        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this::handle);
    }

    private void handle(MethodCall call, MethodChannel.Result result) {
        switch (call.method) {
            case "status" -> run(result, () -> AvailabilityStore.get(context).status());
            case "setEnabled" -> {
                if (!(call.arguments instanceof Boolean enabled)) {
                    result.error("invalid_arguments", "缺少后台验证开关值", null);
                    return;
                }
                run(result, () -> {
                    AvailabilityWorker.setEnabled(context, AvailabilityStore.get(context), enabled);
                    return null;
                });
            }
            case "importCandidates" -> openPicker(IMPORT_REQUEST, result);
            case "exportResults" -> openPicker(EXPORT_REQUEST, result);
            default -> result.notImplemented();
        }
    }

    private void openPicker(int request, MethodChannel.Result result) {
        if (pickerResult != null) {
            result.error("picker_busy", "文件选择窗口已打开", null);
            return;
        }
        Intent intent = new Intent(request == IMPORT_REQUEST
                ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE);
        if (request == IMPORT_REQUEST) {
            intent.setType("text/plain");
        } else {
            intent.setType("application/x-ndjson");
            intent.putExtra(Intent.EXTRA_TITLE, "bili-availability-results.jsonl");
        }
        pickerRequest = request;
        pickerResult = result;
        try {
            activity.startActivityForResult(intent, request);
        } catch (RuntimeException unavailable) {
            pickerResult = null;
            result.error("picker_unavailable", "无法打开系统文件选择器", null);
        }
    }

    boolean onActivityResult(int request, int resultCode, @Nullable Intent data) {
        if (pickerResult == null || request != pickerRequest) {
            return false;
        }
        MethodChannel.Result result = pickerResult;
        pickerResult = null;
        Uri uri = data == null ? null : data.getData();
        if (resultCode != Activity.RESULT_OK || uri == null) {
            result.success(null);
        } else if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            result.error("invalid_document", "无法访问所选文件", null);
        } else {
            run(result, () -> {
                AvailabilityStore store = AvailabilityStore.get(context);
                if (request == IMPORT_REQUEST) {
                    return store.importCandidates(uri);
                }
                try {
                    return store.exportResults(uri);
                } catch (IOException | RuntimeException failure) {
                    try {
                        DocumentsContract.deleteDocument(context.getContentResolver(), uri);
                    } catch (IOException | RuntimeException ignored) {
                        // Some providers do not support deleting a partial newly created document.
                    }
                    throw failure;
                }
            });
        }
        return true;
    }

    void close() {
        channel.setMethodCallHandler(null);
        if (pickerResult != null) {
            pickerResult.success(null);
            pickerResult = null;
        }
    }

    private static void run(MethodChannel.Result result, Operation operation) {
        IO.execute(() -> {
            try {
                result.success(operation.execute());
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                result.error("availability_support_failed", failure.getMessage(), null);
            }
        });
    }

    @FunctionalInterface
    private interface Operation {
        Object execute() throws Exception;
    }
}
