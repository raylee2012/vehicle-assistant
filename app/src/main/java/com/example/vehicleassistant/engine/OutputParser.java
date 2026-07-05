package com.example.vehicleassistant.engine;

import com.example.vehicleassistant.vehicle.models.ActionCommand;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析模型输出。优先尝试 JSON 数组解析，失败则判定为纯文本（闲聊兜底）。
 */
public class OutputParser {

    public static class ParseResult {
        public final boolean isCommands;    // true=车控指令, false=纯文本
        public final List<ActionCommand> commands;
        public final String text;

        public ParseResult(List<ActionCommand> commands) {
            this.isCommands = true;
            this.commands = commands;
            this.text = null;
        }

        public ParseResult(String text) {
            this.isCommands = false;
            this.commands = null;
            this.text = text;
        }
    }

    public ParseResult parse(String modelOutput) {
        if (modelOutput == null || modelOutput.trim().isEmpty()) {
            return new ParseResult("(模型未输出内容)");
        }

        String trimmed = modelOutput.trim();

        // 尝试提取 JSON 数组
        List<ActionCommand> commands = tryParseJson(trimmed);
        if (commands != null && !commands.isEmpty()) {
            return new ParseResult(commands);
        }

        // 不是 JSON → 纯文本闲聊
        return new ParseResult(stripThinkingBlocks(trimmed));
    }

    private List<ActionCommand> tryParseJson(String text) {
        // 策略1: 直接解析
        String json = extractJsonArray(text);
        if (json == null) return null;

        try {
            return parseCommandArray(json);
        } catch (Exception e) {
            // 策略2: 修复常见格式错误后重试
            json = repairCommonErrors(json);
            try {
                return parseCommandArray(json);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start == -1 || end == -1 || start >= end) return null;
        return text.substring(start, end + 1);
    }

    private String repairCommonErrors(String json) {
        return json
            .replaceAll(",\\s*]", "]")          // 去除尾逗号
            .replaceAll(",\\s*,", ",")          // 双重逗号
            .replaceAll("'", "\"");             // 单引号转双引号 (保守策略)
    }

    private String stripThinkingBlocks(String text) {
        // 去除  response... 标签
        return text.replaceAll("(?s) response.*?</think>", "").trim();
    }

    private List<ActionCommand> parseCommandArray(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        List<ActionCommand> commands = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ActionCommand cmd = new ActionCommand();
            cmd.action = obj.getString("action");
            if (obj.has("params")) {
                JSONObject p = obj.getJSONObject("params");
                java.util.HashMap<String, Object> params = new java.util.HashMap<>();
                java.util.Iterator<String> keys = p.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    params.put(k, p.get(k));
                }
                cmd.params = params;
            }
            commands.add(cmd);
        }
        return commands;
    }
}
