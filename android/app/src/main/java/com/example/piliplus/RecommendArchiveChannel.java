package com.example.piliplus;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;

final class RecommendArchiveChannel {
    private static final int PICK = 11043;
    private final Activity activity;
    private final MethodChannel channel;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private MethodChannel.Result selection;

    RecommendArchiveChannel(Activity activity, BinaryMessenger messenger) {
        this.activity = activity;
        channel = new MethodChannel(messenger, "com.bilibilitakemeback/recommend-archive");
        channel.setMethodCallHandler((call, result) -> {
            if (call.method.equals("choose")) {
                if (selection != null) {
                    result.error("busy", "目录选择尚未结束", null);
                    return;
                }
                selection = result;
                try {
                    activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), PICK);
                } catch (RuntimeException error) {
                    selection = null;
                    result.error("directory", error.toString(), null);
                }
            } else if (call.method.equals("write")) {
                String directory = call.argument("directory");
                String name = call.argument("name");
                String source = call.argument("source");
                io.execute(() -> {
                    try {
                        write(Uri.parse(directory), name, source);
                        activity.runOnUiThread(() -> result.success(null));
                    } catch (IOException | RuntimeException error) {
                        activity.runOnUiThread(() -> result.error("archive", error.toString(), null));
                    }
                });
            } else {
                result.notImplemented();
            }
        });
    }

    boolean onActivityResult(int request, int status, Intent data) {
        if (request != PICK || selection == null) return false;
        MethodChannel.Result result = selection;
        selection = null;
        Uri uri = data == null ? null : data.getData();
        if (status != Activity.RESULT_OK || uri == null) {
            result.success(null);
            return true;
        }
        try {
            activity.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            result.success(uri.toString());
        } catch (SecurityException error) {
            result.error("directory", error.toString(), null);
        }
        return true;
    }

    private void write(Uri tree, String name, String source) throws IOException {
        ContentResolver resolver = activity.getContentResolver();
        String parentId = DocumentsContract.getTreeDocumentId(tree);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        Uri document = null;
        try (Cursor cursor = resolver.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor == null) throw new IOException("无法读取归档目录");
            while (cursor.moveToNext()) {
                if (name.equals(cursor.getString(1))) {
                    document = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                    break;
                }
            }
        }
        if (document == null) document = DocumentsContract.createDocument(resolver, parent, "application/gzip", name);
        if (document == null) throw new IOException("无法建立归档文件");
        try (FileInputStream input = new FileInputStream(source);
                OutputStream output = resolver.openOutputStream(document, "wt")) {
            if (output == null) throw new IOException("无法打开归档文件");
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = input.read(buffer)) != -1; ) output.write(buffer, 0, n);
        }
    }

    void close() {
        if (selection != null) selection.success(null);
        selection = null;
        channel.setMethodCallHandler(null);
        io.shutdown();
    }
}
