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
        // --- 气候控制 ---
        register(new ToolDefinition("set_ac", "设置空调。temp为温度(16-32)，mode为模式，power为开关",
                Arrays.asList(
                    new ParamDef("power", "boolean", "是否开启空调", null, null, null),
                    new ParamDef("temp", "int", "目标温度(16-32)", 16, 32, null),
                    new ParamDef("mode", "string", "空调模式", null, null,
                        Arrays.asList("auto", "cool", "heat", "fan"))
                ), false, false, "ac"));

        register(new ToolDefinition("set_fan_speed", "设置风扇风速(1-7级)",
                Arrays.asList(
                    new ParamDef("level", "int", "风速等级(1-7)", 1, 7, null)
                ), false, false, "fan"));

        register(new ToolDefinition("set_air_circulation", "设置空气循环模式",
                Arrays.asList(
                    new ParamDef("mode", "string", "循环模式", null, null,
                        Arrays.asList("internal", "external", "auto"))
                ), false, false, "air_circ"));

        register(new ToolDefinition("defrost", "控制除霜功能",
                Arrays.asList(
                    new ParamDef("position", "string", "除霜位置", null, null,
                        Arrays.asList("front", "rear", "both")),
                    new ParamDef("power", "boolean", "是否开启", null, null, null)
                ), false, false, "defrost"));

        register(new ToolDefinition("seat_heat", "设置座椅加热。seat为座椅位置，level为档位(0-3,0=关闭)",
                Arrays.asList(
                    new ParamDef("seat", "string", "座椅位置", null, null,
                        Arrays.asList("driver", "passenger", "rear_left", "rear_right")),
                    new ParamDef("level", "int", "加热档位(0=关闭,1-3)", 0, 3, null)
                ), false, false, "seat_heat"));

        register(new ToolDefinition("seat_vent", "设置座椅通风。seat为座椅位置，level为档位(0-3,0=关闭)",
                Arrays.asList(
                    new ParamDef("seat", "string", "座椅位置", null, null,
                        Arrays.asList("driver", "passenger")),
                    new ParamDef("level", "int", "通风档位(0=关闭,1-3)", 0, 3, null)
                ), false, false, "seat_vent"));

        register(new ToolDefinition("steering_heat", "方向盘加热开关",
                Arrays.asList(
                    new ParamDef("power", "boolean", "是否开启", null, null, null)
                ), false, false, "steering_heat"));

        // --- 车窗与车门 ---
        register(new ToolDefinition("control_window", "控制车窗升降。position=位置(action为all时需包含all)，action=动作，percent=开度百分比(仅open/close时有效)",
                Arrays.asList(
                    new ParamDef("position", "string", "车窗位置", null, null,
                        Arrays.asList("fl", "fr", "rl", "rr", "all")),
                    new ParamDef("action", "string", "动作", null, null,
                        Arrays.asList("open", "close", "stop")),
                    new ParamDef("percent", "int", "开度百分比(0=全关,100=全开)", 0, 100, null)
                ), true, false, "window"));

        register(new ToolDefinition("control_door_lock", "车门上锁/解锁",
                Arrays.asList(
                    new ParamDef("action", "string", "锁动作", null, null,
                        Arrays.asList("lock", "unlock"))
                ), true, true, "door_lock"));

        register(new ToolDefinition("control_trunk", "后备箱开关",
                Arrays.asList(
                    new ParamDef("action", "string", "动作", null, null,
                        Arrays.asList("open", "close"))
                ), true, false, "trunk"));

        register(new ToolDefinition("control_sunroof", "天窗控制",
                Arrays.asList(
                    new ParamDef("action", "string", "动作", null, null,
                        Arrays.asList("open", "close", "tilt")),
                    new ParamDef("percent", "int", "开度百分比(0-100)", 0, 100, null)
                ), false, false, "sunroof"));

        register(new ToolDefinition("child_lock", "儿童锁开关",
                Arrays.asList(
                    new ParamDef("power", "boolean", "是否启用", null, null, null)
                ), true, false, "child_lock"));

        // --- 座椅与内饰 ---
        register(new ToolDefinition("adjust_seat", "调节座椅位置。steps为正数表示正向调，负数表示反向调",
                Arrays.asList(
                    new ParamDef("seat", "string", "座椅", null, null,
                        Arrays.asList("driver", "passenger")),
                    new ParamDef("direction", "string", "方向", null, null,
                        Arrays.asList("forward", "backward", "up", "down", "recline")),
                    new ParamDef("steps", "int", "步数", -20, 20, null)
                ), true, false, "seat_adjust"));

        register(new ToolDefinition("memory_seat", "调用座椅记忆位置",
                Arrays.asList(
                    new ParamDef("profile", "int", "记忆档位(1-3)", 1, 3, null)
                ), true, false, "seat_memory"));

        register(new ToolDefinition("adjust_mirror", "调节后视镜角度",
                Arrays.asList(
                    new ParamDef("mirror", "string", "后视镜", null, null,
                        Arrays.asList("left", "right")),
                    new ParamDef("direction", "string", "方向", null, null,
                        Arrays.asList("up", "down", "in", "out"))
                ), false, false, "mirror_adjust"));

        register(new ToolDefinition("fold_mirror", "折叠/展开后视镜",
                Arrays.asList(
                    new ParamDef("power", "boolean", "true=折叠,false=展开", null, null, null)
                ), false, false, "mirror_fold"));

        register(new ToolDefinition("ambient_light", "设置氛围灯颜色和亮度",
                Arrays.asList(
                    new ParamDef("color", "string", "颜色", null, null,
                        Arrays.asList("red", "blue", "green", "white", "warm", "auto")),
                    new ParamDef("brightness", "int", "亮度(0-100)", 0, 100, null)
                ), false, false, "ambient_light"));

        // --- 驾驶与灯光 ---
        register(new ToolDefinition("control_headlight", "大灯控制",
                Arrays.asList(
                    new ParamDef("mode", "string", "大灯模式", null, null,
                        Arrays.asList("auto", "low", "high", "off"))
                ), false, false, "headlight"));

        register(new ToolDefinition("control_fog_light", "雾灯开关",
                Arrays.asList(
                    new ParamDef("power", "boolean", "是否开启", null, null, null)
                ), false, false, "fog_light"));

        register(new ToolDefinition("hazard_light", "双闪灯开关",
                Arrays.asList(
                    new ParamDef("power", "boolean", "是否开启", null, null, null)
                ), false, false, "hazard"));

        register(new ToolDefinition("drive_mode", "切换驾驶模式",
                Arrays.asList(
                    new ParamDef("mode", "string", "驾驶模式", null, null,
                        Arrays.asList("eco", "comfort", "sport", "snow", "offroad"))
                ), true, false, "drive_mode"));

        register(new ToolDefinition("cruise_control", "定速巡航控制",
                Arrays.asList(
                    new ParamDef("power", "boolean", "是否开启", null, null, null),
                    new ParamDef("speed", "int", "巡航速度(30-150 km/h)", 30, 150, null)
                ), false, true, "cruise"));

        register(new ToolDefinition("wiper", "雨刮器控制",
                Arrays.asList(
                    new ParamDef("speed", "string", "雨刮速度", null, null,
                        Arrays.asList("off", "low", "medium", "high", "auto"))
                ), false, false, "wiper"));

        buildSchema();
    }

    private void register(ToolDefinition tool) {
        tools.put(tool.name, tool);
    }

    private void buildSchema() {
        JSONArray arr = new JSONArray();
        for (ToolDefinition tool : tools.values()) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", tool.name);
                obj.put("description", tool.description);

                JSONObject props = new JSONObject();
                JSONArray required = new JSONArray();
                for (ParamDef p : tool.params) {
                    JSONObject prop = new JSONObject();
                    prop.put("type", p.type);
                    prop.put("description", p.description);
                    if (p.min != null) prop.put("minimum", p.min);
                    if (p.max != null) prop.put("maximum", p.max);
                    if (p.enumValues != null) {
                        prop.put("enum", new JSONArray(p.enumValues));
                    }
                    props.put(p.name, prop);
                    required.put(p.name); // 所有参数都是必填
                }
                obj.put("parameters", new JSONObject() {{
                    put("type", "object");
                    put("properties", props);
                    put("required", required);
                }});
                obj.put("_requireParked", tool.requireParked);
                obj.put("_critical", tool.critical);
                obj.put("_target", tool.target);
            } catch (Exception e) {
                throw new RuntimeException("Failed to build schema for " + tool.name, e);
            }
            arr.put(obj);
        }
        cachedSchema = arr.toString(2);
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
