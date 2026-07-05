package com.example.vehicleassistant.model;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 模型文件下载管理器，支持进度回调。
 */
public class ModelDownloadManager {

    public interface DownloadCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onComplete(File file);
        void onError(String message);
    }

    public static final String DEFAULT_MODEL_URL =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled = false;

    public void download(String url, File destFile, DownloadCallback callback) {
        cancelled = false;
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestMethod("GET");

                int contentLength = conn.getContentLength();
                final long totalSize = contentLength > 0 ? contentLength : -1;

                // 确保目录存在
                File parent = destFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(destFile)) {

                    byte[] buf = new byte[8192];
                    long total = 0;
                    int read;
                    int lastPercent = -1;

                    while ((read = in.read(buf)) != -1) {
                        if (cancelled) {
                            destFile.delete();
                            mainHandler.post(() -> callback.onError("下载已取消"));
                            return;
                        }
                        out.write(buf, 0, read);
                        total += read;

                        if (totalSize > 0) {
                            int pct = (int) (total * 100 / totalSize);
                            if (pct != lastPercent) {
                                lastPercent = pct;
                                int finalPct = pct;
                                long finalTotal = total;
                                mainHandler.post(() ->
                                    callback.onProgress(finalPct, finalTotal, totalSize));
                            }
                        }
                    }
                }

                conn.disconnect();

                if (!cancelled) {
                    mainHandler.post(() -> callback.onComplete(destFile));
                }
            } catch (Exception e) {
                if (!cancelled) {
                    destFile.delete();
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }
        }).start();
    }

    public void cancel() {
        cancelled = true;
    }
}
