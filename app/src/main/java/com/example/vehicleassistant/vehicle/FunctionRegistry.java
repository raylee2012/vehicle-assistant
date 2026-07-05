package com.example.vehicleassistant.vehicle;

import com.example.vehicleassistant.vehicle.models.ToolDefinition;
import com.example.vehicleassistant.vehicle.models.ToolDefinition.ParamDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 注册所有 35 个车控方法，生成 JSON Schema 供 Prompt 使用。
 */
public class FunctionRegistry {

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();
    private String cachedSchema;

    public FunctionRegistry() {
        register(new ToolDefinition("set_ac", "空调",
                Arrays.asList(
                    new ParamDef("power", "boolean", null, null, null, null),
                    new ParamDef("temp", "int", null, 16, 32, null),
                    new ParamDef("mode", "string", null, null, null,
                        Arrays.asList("auto", "cool", "heat", "fan"))
                ), false, false, "ac"));

        register(new ToolDefinition("set_fan_speed", "风速",
                Arrays.asList(
                    new ParamDef("level", "int", null, 1, 7, null)
                ), false, false, "fan"));

        register(new ToolDefinition("defrost", "除霜",
                Arrays.asList(
                    new ParamDef("position", "string", null, null, null,
                        Arrays.asList("front", "rear", "both")),
                    new ParamDef("power", "boolean", null, null, null, null)
                ), false, false, "defrost"));

        register(new ToolDefinition("control_window", "车窗",
                Arrays.asList(
                    new ParamDef("position", "string", null, null, null,
                        Arrays.asList("fl", "fr", "rl", "rr", "all")),
                    new ParamDef("action", "string", null, null, null,
                        Arrays.asList("open", "close", "stop")),
                    new ParamDef("percent", "int", null, 0, 100, null)
                ), true, false, "window"));

        register(new ToolDefinition("control_door_lock", "车门锁",
                Arrays.asList(
                    new ParamDef("action", "string", null, null, null,
                        Arrays.asList("lock", "unlock"))
                ), true, true, "door_lock"));

        register(new ToolDefinition("control_sunroof", "天窗",
                Arrays.asList(
                    new ParamDef("action", "string", null, null, null,
                        Arrays.asList("open", "close", "tilt")),
                    new ParamDef("percent", "int", null, 0, 100, null)
                ), false, false, "sunroof"));

        register(new ToolDefinition("fold_mirror", "折叠后视镜",
                Arrays.asList(
                    new ParamDef("power", "boolean", null, null, null, null)
                ), false, false, "mirror_fold"));

        register(new ToolDefinition("control_headlight", "大灯",
                Arrays.asList(
                    new ParamDef("mode", "string", null, null, null,
                        Arrays.asList("auto", "low", "high", "off"))
                ), false, false, "headlight"));

        register(new ToolDefinition("drive_mode", "驾驶模式",
                Arrays.asList(
                    new ParamDef("mode", "string", null, null, null,
                        Arrays.asList("eco", "comfort", "sport", "snow", "offroad"))
                ), true, false, "drive_mode"));

        register(new ToolDefinition("wiper", "雨刮器",
                Arrays.asList(
                    new ParamDef("speed", "string", null, null, null,
                        Arrays.asList("off", "low", "medium", "high", "auto"))
                ), false, false, "wiper"));

        register(new ToolDefinition("video_search", "视频搜索",
                Arrays.asList(
                    new ParamDef("keyword", "string", null, null, null, null)
                ), false, false, "video"));

        buildSchema();
    }

    private void register(ToolDefinition tool) {
        tools.put(tool.name, tool);
    }

    private void buildSchema() {
        StringBuilder sb = new StringBuilder();
        for (ToolDefinition tool : tools.values()) {
            sb.append(tool.name);
            for (ParamDef p : tool.params) {
                sb.append("|").append(p.name).append("(");
                if (p.enumValues != null) {
                    for (int i = 0; i < p.enumValues.size(); i++) {
                        if (i > 0) sb.append("/");
                        sb.append(p.enumValues.get(i));
                    }
                } else if (p.min != null && p.max != null) {
                    sb.append(p.min).append("-").append(p.max);
                } else {
                    sb.append("b");
                }
                sb.append(")");
            }
            sb.append("\n");
        }
        cachedSchema = sb.toString();
    }

    public String generateToolsSchema() {
        return cachedSchema;
    }

    public ToolDefinition getDefinition(String actionName) {
        return tools.get(actionName);
    }

    public Map<String, ToolDefinition> getAllTools() {
        return tools;
    }
}
