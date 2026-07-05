package com.example.vehicleassistant.model;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 模型文件下载管理器，支持双源镜像和断点续传。
 */
public class ModelDownloadManager {

    private static final String TAG = "ModelDownloader";

    public interface DownloadCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onComplete(File file);
        void onError(String message);
    }

    public static final String DEFAULT_MODEL_URL =
        "https://modelscope.cn/models/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/qwen2.5-1.5b-instruct-q4_0.gguf";

    private static final String MIRROR_URL =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_0.gguf";

    private static final String[] URLS = {DEFAULT_MODEL_URL, MIRROR_URL};

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled = false;

    public void download(File destFile, DownloadCallback callback) {
        cancelled = false;
        new Thread(() -> {
            // 检查是否有未完成的下载可续传
            long existingSize = 0;
            if (destFile.exists()) {
                existingSize = destFile.length();
                if (existingSize > 0) {
                    Log.d(TAG, "发现已下载 " + existingSize + " 字节，将续传");
                }
            }

            for (int i = 0; i < URLS.length; i++) {
                if (cancelled) return;
                String url = URLS[i];
                Log.d(TAG, "尝试下载: " + url + (existingSize > 0 ? " (续传)" : ""));

                try {
                    if (downloadFromUrl(url, destFile, existingSize, callback)) {
                        return; // 成功
                    }
                    // 失败则尝试下一个源
                    Log.w(TAG, "源 " + url + " 失败，尝试下一个");
                } catch (Exception e) {
                    Log.e(TAG, "源 " + url + " 异常: " + e.getMessage());
                }
            }

            if (!cancelled) {
                destFile.delete();
                mainHandler.post(() -> callback.onError("所有下载源均失败，请检查网络后重试"));
            }
        }).start();
    }

    private boolean downloadFromUrl(String url, File destFile, long existingSize,
                                    DownloadCallback callback) {
        try {
            URL u = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestMethod("GET");

            if (existingSize > 0) {
                conn.setRequestProperty("Range", "bytes=" + existingSize + "-");
            }

            int responseCode = conn.getResponseCode();
            boolean isResume = (responseCode == 206);

            if (responseCode != 200 && responseCode != 206) {
                conn.disconnect();
                // 如果续传被拒绝，从头开始
                if (existingSize > 0 && responseCode == 200) {
                    existingSize = 0;
                    destFile.delete();
                    return downloadFromUrl(url, destFile, 0, callback);
                }
                return false;
            }

            long totalSize;
            if (isResume) {
                String range = conn.getHeaderField("Content-Range");
                // "bytes X-Y/Z"
                totalSize = Long.parseLong(range.substring(range.lastIndexOf('/') + 1));
                Log.d(TAG, "续传: 已下载=" + existingSize + " 总大小=" + totalSize);
            } else {
                totalSize = conn.getContentLength();
            }

            File parent = destFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            try (InputStream in = conn.getInputStream();
                 RandomAccessFile out = new RandomAccessFile(destFile, "rw")) {

                if (existingSize > 0) {
                    out.seek(existingSize);
                }

                byte[] buf = new byte[8192];
                long total = existingSize;
                int read;
                int lastPercent = (totalSize > 0) ? (int) (total * 100 / totalSize) : -1;

                while ((read = in.read(buf)) != -1) {
                    if (cancelled) {
                        // 保留已下载部分以便续传
                        mainHandler.post(() -> callback.onError("下载已取消"));
                        return false;
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

                // 完整性检查
                if (totalSize > 0 && total < totalSize) {
                    Log.w(TAG, "下载不完整: " + total + "/" + totalSize);
                    return false;
                }
            }

            conn.disconnect();

            if (!cancelled) {
                mainHandler.post(() -> callback.onComplete(destFile));
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "下载异常: " + e.getMessage());
            return false;
        }
    }

    /** @deprecated 使用 download(File, DownloadCallback) 自动多源下载 */
    @Deprecated
    public void download(String url, File destFile, DownloadCallback callback) {
        cancelled = false;
        destFile.delete(); // 旧签名不支持续传
        new Thread(() -> {
            for (int i = 0; i < URLS.length; i++) {
                String src = URLS[i];
                try {
                    if (downloadFromUrl(src, destFile, 0, callback)) return;
                } catch (Exception e) {
                    Log.e(TAG, "源异常: " + e.getMessage());
                }
            }
            if (!cancelled) {
                destFile.delete();
                mainHandler.post(() -> callback.onError("所有下载源均失败"));
            }
        }).start();
    }

    public void cancel() {
        cancelled = true;
    }
}
