package com.example.vehicleassistant.engine;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 基于关键词匹配的模拟意图提取器。
 * 当真实模型未就绪时，从用户输入中提取车控指令。
 * 覆盖 10 个核心车控方法的关键词。
 */
public class MockCommandExtractor {

    /**
     * 从中文用户输入中提取车控指令，返回 JSON 数组字符串。
     */
    public String extract(String userInput) {
        JSONArray commands = new JSONArray();

        // --- 空调 ---
        if (containsAny(userInput, "空调", "冷气", "暖气", "制冷", "制热", "AC")) {
            JSONObject ac = new JSONObject();
            try {
                ac.put("action", "set_ac");
                JSONObject params = new JSONObject();
                params.put("power", !containsAny(userInput, "关空调", "关掉空调", "关闭空调", "空调关"));
                if (containsAny(userInput, "热", "暖", "制热")) {
                    params.put("mode", "heat");
                } else if (containsAny(userInput, "冷", "制冷", "凉")) {
                    params.put("mode", "cool");
                } else {
                    params.put("mode", "auto");
                }
                params.put("temp", extractTemp(userInput));
                ac.put("params", params);
                commands.put(ac);
            } catch (Exception ignored) {}
        }

        // --- 风扇 ---
        if (containsAny(userInput, "风扇", "风量", "风速", "吹风")) {
            JSONObject fan = new JSONObject();
            try {
                fan.put("action", "set_fan_speed");
                JSONObject params = new JSONObject();
                params.put("level", extractLevel(userInput, 7));
                fan.put("params", params);
                commands.put(fan);
            } catch (Exception ignored) {}
        }

        // --- 车窗 ---
        if (containsAny(userInput, "车窗", "窗户", "窗")) {
            JSONObject win = new JSONObject();
            try {
                win.put("action", "control_window");
                JSONObject params = new JSONObject();
                params.put("position", extractWindowPosition(userInput));
                params.put("action", containsAny(userInput, "关", "升") ? "close" : "open");
                int pct = extractPercent(userInput);
                params.put("percent", pct >= 0 ? pct : (containsAny(userInput, "关", "升") ? 0 : 100));
                win.put("params", params);
                commands.put(win);
            } catch (Exception ignored) {}
        }

        // --- 车门锁 ---
        if (containsAny(userInput, "车门", "门锁", "锁车", "锁门", "解锁", "开锁")) {
            JSONObject lock = new JSONObject();
            try {
                lock.put("action", "control_door_lock");
                JSONObject params = new JSONObject();
                params.put("action", containsAny(userInput, "开锁", "解锁", "开门") ? "unlock" : "lock");
                lock.put("params", params);
                commands.put(lock);
            } catch (Exception ignored) {}
        }

        // --- 天窗 ---
        if (containsAny(userInput, "天窗")) {
            JSONObject sun = new JSONObject();
            try {
                sun.put("action", "control_sunroof");
                JSONObject params = new JSONObject();
                params.put("action", containsAny(userInput, "关") ? "close" : "open");
                sun.put("params", params);
                commands.put(sun);
            } catch (Exception ignored) {}
        }

        // --- 大灯 ---
        if (containsAny(userInput, "大灯", "远光", "近光", "头灯", "车灯")) {
            JSONObject hl = new JSONObject();
            try {
                hl.put("action", "control_headlight");
                JSONObject params = new JSONObject();
                if (containsAny(userInput, "远光")) params.put("mode", "high");
                else if (containsAny(userInput, "近光")) params.put("mode", "low");
                else if (containsAny(userInput, "关")) params.put("mode", "off");
                else params.put("mode", "auto");
                hl.put("params", params);
                commands.put(hl);
            } catch (Exception ignored) {}
        }

        // --- 驾驶模式 ---
        if (containsAny(userInput, "驾驶模式", "模式")) {
            JSONObject dm = new JSONObject();
            try {
                dm.put("action", "drive_mode");
                JSONObject params = new JSONObject();
                if (containsAny(userInput, "运动")) params.put("mode", "sport");
                else if (containsAny(userInput, "节能", "省油", "经济")) params.put("mode", "eco");
                else if (containsAny(userInput, "雪地")) params.put("mode", "snow");
                else if (containsAny(userInput, "越野")) params.put("mode", "offroad");
                else params.put("mode", "comfort");
                dm.put("params", params);
                commands.put(dm);
            } catch (Exception ignored) {}
        }

        // --- 雨刮 ---
        if (containsAny(userInput, "雨刮", "雨刷", "刮水")) {
            JSONObject wp = new JSONObject();
            try {
                wp.put("action", "wiper");
                JSONObject params = new JSONObject();
                if (containsAny(userInput, "快", "高", "大")) params.put("speed", "high");
                else if (containsAny(userInput, "中")) params.put("speed", "medium");
                else if (containsAny(userInput, "慢", "低", "小")) params.put("speed", "low");
                else if (containsAny(userInput, "关", "停")) params.put("speed", "off");
                else params.put("speed", "auto");
                wp.put("params", params);
                commands.put(wp);
            } catch (Exception ignored) {}
        }

        // --- 除霜 ---
        if (containsAny(userInput, "除霜", "除雾")) {
            JSONObject df = new JSONObject();
            try {
                df.put("action", "defrost");
                JSONObject params = new JSONObject();
                if (containsAny(userInput, "前")) params.put("position", "front");
                else if (containsAny(userInput, "后")) params.put("position", "rear");
                else params.put("position", "both");
                params.put("power", !containsAny(userInput, "关"));
                df.put("params", params);
                commands.put(df);
            } catch (Exception ignored) {}
        }

        // --- 后视镜 ---
        if (containsAny(userInput, "后视镜", "倒车镜")) {
            if (containsAny(userInput, "折叠", "折")) {
                JSONObject fm = new JSONObject();
                try {
                    fm.put("action", "fold_mirror");
                    JSONObject params = new JSONObject();
                    params.put("power", !containsAny(userInput, "展开", "打开"));
                    fm.put("params", params);
                    commands.put(fm);
                } catch (Exception ignored) {}
            }
        }

        // 如果没匹配到任何指令，返回 null 走闲聊兜底
        if (commands.length() == 0) {
            return null; // 让 OutputParser 走闲聊兜底
        }

        return commands.toString();
    }

    // ---- 辅助方法 ----

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private int extractTemp(String text) {
        // 找 "XX度" 或 "XX°C" 模式
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)\\s*[度°]");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            int t = Integer.parseInt(m.group(1));
            return Math.max(16, Math.min(32, t));
        }
        return 24; // 默认
    }

    private int extractLevel(String text, int max) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)\\s*[档级]");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            int l = Integer.parseInt(m.group(1));
            return Math.max(1, Math.min(max, l));
        }
        return max; // 默认最大
    }

    private int extractPercent(String text) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)\\s*[%％]");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            int v = Integer.parseInt(m.group(1));
            return Math.max(0, Math.min(100, v));
        }
        return -1; // 未指定
    }

    private int extractSpeed(String text) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)\\s*(公里|km|迈|码)");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            int s = Integer.parseInt(m.group(1));
            return Math.max(30, Math.min(150, s));
        }
        return 60;
    }

    private String extractWindowPosition(String text) {
        if (containsAny(text, "全部", "所有", "全车")) return "all";
        if (containsAny(text, "左前", "驾驶", "主驾")) return "fl";
        if (containsAny(text, "右前", "副驾", "副驾驶")) return "fr";
        if (containsAny(text, "左后")) return "rl";
        if (containsAny(text, "右后")) return "rr";
        return "all"; // 默认全部
    }

    private String extractSeat(String text) {
        if (containsAny(text, "副驾", "副驾驶", "乘客")) return "passenger";
        return "driver";
    }

    private String extractColor(String text) {
        if (containsAny(text, "红")) return "red";
        if (containsAny(text, "蓝")) return "blue";
        if (containsAny(text, "绿")) return "green";
        if (containsAny(text, "白")) return "white";
        if (containsAny(text, "暖")) return "warm";
        return "auto";
    }
}
