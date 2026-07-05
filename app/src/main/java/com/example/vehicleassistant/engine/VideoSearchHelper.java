package com.example.vehicleassistant.engine;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class VideoSearchHelper {

    private static final String[] DOUYIN_PACKAGES = {
        "com.ss.android.ugc.aweme",      // 正式版
        "com.ss.android.ugc.aweme.lite"  // 极速版
    };
    // 正式版和极速版 scheme 不同
    private static final String[] DOUYIN_SCHEMES = {
        "snssdk1128",  // 正式版
        "snssdk2329"   // 极速版
    };
    private static final String DOUYIN_WEB_SEARCH = "https://www.douyin.com/search/%s";

    private static final String[] SEARCH_VERBS = {"搜索", "找个", "搜", "找", "查"};

    public static String extractKeyword(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty() || !trimmed.contains("视频")) return null;

        boolean hasVerb = false;
        for (String v : SEARCH_VERBS) {
            if (trimmed.contains(v)) { hasVerb = true; break; }
        }
        if (!hasVerb) return null;

        String keyword = trimmed;
        for (String v : SEARCH_VERBS) keyword = keyword.replace(v, " ");
        keyword = keyword.replace("视频", " ");
        keyword = keyword.replace("帮我", " ")
                .replace("给我", " ")
                .replace("一个", " ")
                .replace("一下", " ")
                .replace("一段", " ")
                .replace("的", " ")
                .replace("个", " ")
                .trim()
                .replaceAll("\\s+", " ");

        return keyword.isEmpty() ? trimmed : keyword;
    }

    public static void openSearch(Context context, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return;

        String encoded = encode(keyword);
        Intent appIntent = new Intent(Intent.ACTION_VIEW);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 逐个尝试 (package, scheme) 组合
        for (int i = 0; i < DOUYIN_PACKAGES.length; i++) {
            String scheme = DOUYIN_SCHEMES[i];
            String uri = scheme + "://search?keyword=" + encoded + "&type=1";
            appIntent.setData(Uri.parse(uri));
            appIntent.setPackage(DOUYIN_PACKAGES[i]);
            try {
                context.startActivity(appIntent);
                Toast.makeText(context, "正在跳转抖音", Toast.LENGTH_SHORT).show();
                return;
            } catch (Exception ignored) {}
        }

        Intent webIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(DOUYIN_WEB_SEARCH, encoded)));
        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(webIntent);
            Toast.makeText(context, "未安装抖音，已打开网页版", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "跳转失败", Toast.LENGTH_SHORT).show();
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }
}
