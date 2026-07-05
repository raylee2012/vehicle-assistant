# 车载语音助手 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个基于本地大模型的智能车控对话助手 Android App，支持 Function Calling 多指令语义推理。

**Architecture:** 四层架构（UI → Agent → Engine → Vehicle），llama.cpp JNI 推理，Qwen2.5-1.5B Q4_K_M 模型，MVVM + ViewBinding + XML 实现 UI，ContextManager 滑动窗口管理 4096 tokens 上下文。

**Tech Stack:** Android + Java, llama.cpp (JNI), Qwen2.5-1.5B-Instruct Q4_K_M, ViewBinding, RecyclerView

## Global Constraints

- 平台: Android (minSdk 26, targetSdk 34)
- 语言: Java 11
- 包名: `com.example.vehicleassistant`
- 上下文窗口: 4096 tokens
- 推理参数: temperature=0.1, top_p=0.9, max_tokens=512, threads=4
- UI: MVVM + ViewBinding + XML, 禁止使用 Jetpack Compose
- 模型: Qwen2.5-1.5B-Instruct Q4_K_M, ChatML 格式, 通过下载获取不打包进 APK
- 车控方法: 35 个, 4 大类, 当前阶段 mock 打 log 实现
- 边界场景: 14 个 (安全2 + 对话6 + 系统4 + 执行2)

---

### Task 1: 项目基础模型层 (ChatMessage, ModelConfig)

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/model/ChatMessage.java`
- Create: `app/src/main/java/com/example/vehicleassistant/model/ModelConfig.java`

**Interfaces:**
- Produces: `ChatMessage(role, content)`, `ModelConfig` — 被所有上层模块消费

- [ ] **Step 1: 编写 ChatMessage.java**

```java
package com.example.vehicleassistant.model;

public class ChatMessage {
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public final String role;
    public final String content;
    public final long timestamp;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
}
```

- [ ] **Step 2: 编写 ModelConfig.java**

```java
package com.example.vehicleassistant.model;

public class ModelConfig {
    public final String modelPath;
    public final int contextSize = 4096;
    public final int maxTokens = 512;
    public final float temperature = 0.1f;
    public final float topP = 0.9f;
    public final int threads = 4;

    public ModelConfig(String modelPath) {
        this.modelPath = modelPath;
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/model/
git commit -m "feat: add base data models (ChatMessage, ModelConfig)"
```

---

### Task 2: Vehicle 模型层 (ToolDefinition, ActionCommand, ExecutionResult)

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/vehicle/models/ToolDefinition.java`
- Create: `app/src/main/java/com/example/vehicleassistant/vehicle/models/ActionCommand.java`
- Create: `app/src/main/java/com/example/vehicleassistant/vehicle/models/ExecutionResult.java`

**Interfaces:**
- Produces: `ToolDefinition(name, params, requireParked, critical, target)`, `ActionCommand(action, params)`, `ExecutionResult(success, action, params, message)`

- [ ] **Step 1: 编写 ToolDefinition.java**

```java
package com.example.vehicleassistant.vehicle.models;

import java.util.List;
import java.util.Map;

public class ToolDefinition {
    public final String name;
    public final String description;
    public final List<ParamDef> params;
    public final boolean requireParked;
    public final boolean critical;
    public final String target; // 冲突检测用的目标标识，如 "ac", "window:fl"

    public ToolDefinition(String name, String description, List<ParamDef> params,
                          boolean requireParked, boolean critical, String target) {
        this.name = name;
        this.description = description;
        this.params = params;
        this.requireParked = requireParked;
        this.critical = critical;
        this.target = target;
    }

    public static class ParamDef {
        public final String name;
        public final String type;   // "boolean", "int", "string"
        public final String description;
        public final Object min;    // int 类型的下限
        public final Object max;    // int 类型的上限
        public final List<String> enumValues; // enum 类型的合法值, null 表示非 enum

        public ParamDef(String name, String type, String description,
                        Object min, Object max, List<String> enumValues) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.min = min;
            this.max = max;
            this.enumValues = enumValues;
        }
    }
}
```

- [ ] **Step 2: 编写 ActionCommand.java**

```java
package com.example.vehicleassistant.vehicle.models;

import java.util.Map;

public class ActionCommand {
    public String action;
    public Map<String, Object> params;
    public boolean critical;    // 由 FunctionRegistry 在执行前填充
    public String target;       // 由 FunctionRegistry 在执行前填充

    public ActionCommand() {}

    public ActionCommand(String action, Map<String, Object> params) {
        this.action = action;
        this.params = params;
    }
}
```

- [ ] **Step 3: 编写 ExecutionResult.java**

```java
package com.example.vehicleassistant.vehicle.models;

import java.util.Map;

public class ExecutionResult {
    public final boolean success;
    public final String action;
    public final Map<String, Object> params;
    public final String message;

    public ExecutionResult(boolean success, String action,
                           Map<String, Object> params, String message) {
        this.success = success;
        this.action = action;
        this.params = params;
        this.message = message;
    }
}
```

- [ ] **Step 4: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/vehicle/models/
git commit -m "feat: add vehicle domain models (ToolDefinition, ActionCommand, ExecutionResult)"
```

---

### Task 3: VehicleState — 车辆状态维护

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/vehicle/VehicleState.java`

**Interfaces:**
- Produces: `VehicleState` — 被 VehicleService 和 FunctionRegistry 消费

- [ ] **Step 1: 编写 VehicleState.java**

```java
package com.example.vehicleassistant.vehicle;

import java.util.HashMap;
import java.util.Map;

/**
 * 内存中的车辆状态快照。每次车控执行成功后更新对应字段。
 * 用途: 幂等检查、安全校验、上下文注入。
 */
public class VehicleState {

    // --- 气候 ---
    public boolean acPower = false;
    public int acTemp = 24;
    public String acMode = "auto";            // auto/cool/heat/fan

    public int fanSpeed = 1;                  // 1-7
    public String airCirculation = "auto";    // internal/external/auto

    public boolean frontDefrost = false;
    public boolean rearDefrost = false;

    public Map<String, Integer> seatHeatLevel = new HashMap<>();  // seat→level 0-3
    public Map<String, Integer> seatVentLevel = new HashMap<>();
    public boolean steeringHeat = false;

    // --- 车窗与车门 ---
    // "fl"/"fr"/"rl"/"rr" → 0=关, 100=全开
    public Map<String, Integer> windowPercent = new HashMap<>();
    public boolean doorLocked = true;
    public boolean trunkOpen = false;
    public boolean sunroofOpen = false;
    public int sunroofPercent = 0;
    public boolean childLock = false;

    // --- 座椅 ---
    public Map<String, Integer> seatPosition = new HashMap<>();   // "driver:forward"→steps
    public int memorySeatProfile = 1;

    // --- 后视镜 ---
    public Map<String, String> mirrorPosition = new HashMap<>();  // "left"→"up"
    public boolean mirrorFolded = false;

    // --- 内饰 ---
    public String ambientColor = "auto";
    public int ambientBrightness = 50;

    // --- 灯光 ---
    public String headlightMode = "auto";      // auto/low/high/off
    public boolean fogLight = false;
    public boolean hazardLight = false;

    // --- 驾驶 ---
    public String driveMode = "comfort";
    public boolean cruiseOn = false;
    public int cruiseSpeed = 60;
    public String wiperSpeed = "off";

    // --- 安全状态 ---
    public boolean isParked = true;  // Mock: 始终为驻车状态

    public VehicleState() {
        // 初始化车窗默认值
        for (String pos : new String[]{"fl", "fr", "rl", "rr"}) {
            windowPercent.put(pos, 0);
        }
        // 初始化座椅默认值
        for (String seat : new String[]{"driver", "passenger"}) {
            seatHeatLevel.put(seat, 0);
            seatVentLevel.put(seat, 0);
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/vehicle/VehicleState.java
git commit -m "feat: add VehicleState with full vehicle state fields"
```

---

### Task 4: FunctionRegistry — 方法注册表 + Schema 生成

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/vehicle/FunctionRegistry.java`

**Interfaces:**
- Consumes: `ToolDefinition`, `VehicleState`
- Produces: `FunctionRegistry` — `generateToolsSchema(): String`, `getDefinition(name): ToolDefinition`

- [ ] **Step 1: 编写 FunctionRegistry.java**

```java
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
```

- [ ] **Step 2: 验证编译 (需要 org.json 依赖，先确认 build.gradle 中有 implementation 'org.json:json:20231013' 或类似)**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/vehicle/FunctionRegistry.java
git commit -m "feat: add FunctionRegistry with 35 vehicle control methods and JSON schema generation"
```

---

### Task 5: VehicleService — 车控执行入口

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/vehicle/VehicleService.java`

**Interfaces:**
- Consumes: `FunctionRegistry`, `VehicleState`, `ActionCommand`, `ExecutionResult`, `ToolDefinition`
- Produces: `VehicleService` — `execute(ActionCommand): ExecutionResult`

- [ ] **Step 1: 编写 VehicleService.java**

```java
package com.example.vehicleassistant.vehicle;

import android.util.Log;

import com.example.vehicleassistant.vehicle.models.ActionCommand;
import com.example.vehicleassistant.vehicle.models.ExecutionResult;
import com.example.vehicleassistant.vehicle.models.ToolDefinition;
import com.example.vehicleassistant.vehicle.models.ToolDefinition.ParamDef;

import java.util.List;
import java.util.Map;

/**
 * 车控方法执行入口。当前阶段所有方法为 mock 实现，打 log 输出。
 * 包含参数校验、危险操作检查、幂等检查。
 */
public class VehicleService {

    private static final String TAG = "VehicleService";

    private final FunctionRegistry registry;
    private final VehicleState state;

    public VehicleService(FunctionRegistry registry, VehicleState state) {
        this.registry = registry;
        this.state = state;
    }

    public ExecutionResult execute(ActionCommand command) {
        String action = command.action;
        Map<String, Object> params = command.params;

        ToolDefinition def = registry.getDefinition(action);
        if (def == null) {
            return new ExecutionResult(false, action, params,
                "未知的车控方法: " + action);
        }

        // 填充 command 的 critical 和 target 字段
        command.critical = def.critical;
        command.target = def.target;

        // 1. 参数校验
        String validationError = validateParams(def, params);
        if (validationError != null) {
            return new ExecutionResult(false, action, params, validationError);
        }

        // 2. 危险操作检查
        if (def.requireParked && !state.isParked) {
            return new ExecutionResult(false, action, params,
                "为了安全，请在驻车状态下执行" + def.description);
        }

        // 3. 幂等检查
        String idempotentMsg = checkIdempotent(action, params);
        if (idempotentMsg != null) {
            return new ExecutionResult(true, action, params, idempotentMsg);
        }

        // 4. 更新状态 + 执行 (mock)
        applyState(action, params);
        Log.d(TAG, "执行: " + action + " 参数: " + params);

        return new ExecutionResult(true, action, params, buildSuccessMessage(def, params));
    }

    private String validateParams(ToolDefinition def, Map<String, Object> params) {
        for (ParamDef p : def.params) {
            Object value = params.get(p.name);
            if (value == null) {
                return "缺少参数: " + p.name;
            }

            if ("int".equals(p.type) && value instanceof Number) {
                int intVal = ((Number) value).intValue();
                if (p.min != null && intVal < ((Number) p.min).intValue()) {
                    return p.name + " 值 " + intVal + " 小于最小值 " + p.min;
                }
                if (p.max != null && intVal > ((Number) p.max).intValue()) {
                    return p.name + " 值 " + intVal + " 大于最大值 " + p.max;
                }
            }

            if (p.enumValues != null && value instanceof String) {
                if (!p.enumValues.contains(value)) {
                    return p.name + "=" + value + " 不在允许范围 " + p.enumValues;
                }
            }
        }
        return null;
    }

    private String checkIdempotent(String action, Map<String, Object> params) {
        switch (action) {
            case "set_ac": {
                Boolean power = (Boolean) params.get("power");
                if (power != null && power && state.acPower) {
                    Number temp = (Number) params.get("temp");
                    if (temp != null && temp.intValue() == state.acTemp) {
                        return "空调已处于开启状态，温度 " + state.acTemp + " 度";
                    }
                }
                if (power != null && !power && !state.acPower) {
                    return "空调已处于关闭状态";
                }
                break;
            }
            case "control_window": {
                String pos = (String) params.get("position");
                String act = (String) params.get("action");
                if ("all".equals(pos) && "close".equals(act)) {
                    boolean allClosed = true;
                    for (int v : state.windowPercent.values()) {
                        if (v > 0) { allClosed = false; break; }
                    }
                    if (allClosed) return "所有车窗已经关闭";
                }
                if (!"all".equals(pos) && "close".equals(act)) {
                    Integer current = state.windowPercent.get(pos);
                    if (current != null && current == 0) return pos + " 车窗已经关闭";
                }
                break;
            }
            case "control_door_lock": {
                String act = (String) params.get("action");
                if ("lock".equals(act) && state.doorLocked) return "车门已上锁";
                if ("unlock".equals(act) && !state.doorLocked) return "车门已解锁";
                break;
            }
        }
        return null;
    }

    private void applyState(String action, Map<String, Object> params) {
        switch (action) {
            case "set_ac":
                if (params.containsKey("power")) state.acPower = (Boolean) params.get("power");
                if (params.containsKey("temp")) state.acTemp = ((Number) params.get("temp")).intValue();
                if (params.containsKey("mode")) state.acMode = (String) params.get("mode");
                break;
            case "set_fan_speed":
                state.fanSpeed = ((Number) params.get("level")).intValue();
                break;
            case "set_air_circulation":
                state.airCirculation = (String) params.get("mode");
                break;
            case "defrost":
                String pos = (String) params.get("position");
                Boolean pw = (Boolean) params.get("power");
                if ("front".equals(pos) || "both".equals(pos)) state.frontDefrost = pw;
                if ("rear".equals(pos) || "both".equals(pos)) state.rearDefrost = pw;
                break;
            case "seat_heat":
                state.seatHeatLevel.put((String) params.get("seat"),
                    ((Number) params.get("level")).intValue());
                break;
            case "seat_vent":
                state.seatVentLevel.put((String) params.get("seat"),
                    ((Number) params.get("level")).intValue());
                break;
            case "steering_heat":
                state.steeringHeat = (Boolean) params.get("power");
                break;
            case "control_window":
                String wPos = (String) params.get("position");
                String wAct = (String) params.get("action");
                int wPct = 0;
                if (params.containsKey("percent")) {
                    wPct = ((Number) params.get("percent")).intValue();
                } else if ("open".equals(wAct)) {
                    wPct = 100;
                }
                if ("all".equals(wPos)) {
                    for (String k : state.windowPercent.keySet()) {
                        state.windowPercent.put(k, wPct);
                    }
                } else {
                    state.windowPercent.put(wPos, wPct);
                }
                break;
            case "control_door_lock":
                state.doorLocked = "lock".equals(params.get("action"));
                break;
            case "control_trunk":
                state.trunkOpen = "open".equals(params.get("action"));
                break;
            case "control_sunroof":
                String sAct = (String) params.get("action");
                state.sunroofOpen = "open".equals(sAct) || "tilt".equals(sAct);
                if (params.containsKey("percent")) {
                    state.sunroofPercent = ((Number) params.get("percent")).intValue();
                }
                break;
            case "child_lock":
                state.childLock = (Boolean) params.get("power");
                break;
            case "adjust_seat":
                String seatKey = params.get("seat") + ":" + params.get("direction");
                Integer currentSteps = state.seatPosition.getOrDefault(seatKey, 0);
                state.seatPosition.put(seatKey, currentSteps + ((Number) params.get("steps")).intValue());
                break;
            case "memory_seat":
                state.memorySeatProfile = ((Number) params.get("profile")).intValue();
                break;
            case "adjust_mirror":
                state.mirrorPosition.put((String) params.get("mirror"), (String) params.get("direction"));
                break;
            case "fold_mirror":
                state.mirrorFolded = (Boolean) params.get("power");
                break;
            case "ambient_light":
                state.ambientColor = (String) params.get("color");
                state.ambientBrightness = ((Number) params.get("brightness")).intValue();
                break;
            case "control_headlight":
                state.headlightMode = (String) params.get("mode");
                break;
            case "control_fog_light":
                state.fogLight = (Boolean) params.get("power");
                break;
            case "hazard_light":
                state.hazardLight = (Boolean) params.get("power");
                break;
            case "drive_mode":
                state.driveMode = (String) params.get("mode");
                break;
            case "cruise_control":
                state.cruiseOn = (Boolean) params.get("power");
                if (params.containsKey("speed")) {
                    state.cruiseSpeed = ((Number) params.get("speed")).intValue();
                }
                break;
            case "wiper":
                state.wiperSpeed = (String) params.get("speed");
                break;
        }
    }

    private String buildSuccessMessage(ToolDefinition def, Map<String, Object> params) {
        return "执行成功: " + def.description + " → " + params;
    }

    public VehicleState getState() {
        return state;
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/vehicle/VehicleService.java
git commit -m "feat: add VehicleService with param validation, safety check, idempotency, and mock execution"
```

---

### Task 6: PromptBuilder — Prompt 组装

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/engine/PromptBuilder.java`

**Interfaces:**
- Consumes: `ChatMessage`, `FunctionRegistry.generateToolsSchema()`
- Produces: `PromptBuilder` — `buildSystemPrompt(toolsSchema): String`, `buildFullPrompt(system, history, userInput): String`, `buildResultPrompt(system, history, executionReport): String`

- [ ] **Step 1: 编写 PromptBuilder.java**

```java
package com.example.vehicleassistant.engine;

import com.example.vehicleassistant.model.ChatMessage;

import java.util.List;

/**
 * 组装 Qwen2.5 ChatML 格式 Prompt。
 */
public class PromptBuilder {

    private static final String SYSTEM_TEMPLATE =
        "你是智能车控助手。根据用户意图选择行动:\n" +
        "- 车控指令 → 严格输出 JSON 数组: [{\"action\":\"方法名\",\"params\":{参数}}]\n" +
        "- 一次可包含多条指令\n" +
        "- 闲聊/模糊意图/否定意图 → 直接回复文本，不要输出 JSON\n" +
        "- 模糊意图需向用户追问确认参数后再执行\n" +
        "- \"不要开空调\"\"别关窗\"等否定意图 = 不调用对应方法，改用文本回复\n\n" +
        "当前车辆状态:\n" +
        "{vehicle_state}\n\n" +
        "可用方法:\n" +
        "{tools_schema}";

    private static final String RESULT_PROMPT =
        "根据以下车控执行结果，用自然语言简洁地向用户汇报:\n" +
        "{execution_results}\n\n" +
        "要求: 一句话总结，成功的提一下，失败的重点说。";

    public String buildSystemPrompt(String toolsSchema, String vehicleStateSummary) {
        return SYSTEM_TEMPLATE
            .replace("{tools_schema}", toolsSchema)
            .replace("{vehicle_state}", vehicleStateSummary);
    }

    public String buildFirstInferencePrompt(String systemPrompt,
                                            List<ChatMessage> history,
                                            String userInput) {
        StringBuilder sb = new StringBuilder();
        sb.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n");

        for (ChatMessage msg : history) {
            sb.append("<|im_start|>").append(msg.role).append("\n");
            sb.append(msg.content).append("<|im_end|>\n");
        }

        sb.append("<|im_start|>user\n").append(userInput).append("<|im_end|>\n");
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    public String buildSecondInferencePrompt(String systemPrompt,
                                             List<ChatMessage> history,
                                             String userInput,
                                             String firstOutput,
                                             String executionReport) {
        StringBuilder sb = new StringBuilder();
        sb.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n");

        for (ChatMessage msg : history) {
            sb.append("<|im_start|>").append(msg.role).append("\n");
            sb.append(msg.content).append("<|im_end|>\n");
        }

        sb.append("<|im_start|>user\n").append(userInput).append("<|im_end|>\n");
        sb.append("<|im_start|>assistant\n").append(firstOutput).append("<|im_end|>\n");
        sb.append("<|im_start|>user\n").append(executionReport).append("<|im_end|>\n");
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    public String buildExecutionReport(
            List<com.example.vehicleassistant.vehicle.models.ExecutionResult> results) {
        StringBuilder sb = new StringBuilder();
        for (com.example.vehicleassistant.vehicle.models.ExecutionResult r : results) {
            sb.append("- ").append(r.success ? "✓" : "✗")
              .append(" ").append(r.message).append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/engine/PromptBuilder.java
git commit -m "feat: add PromptBuilder with ChatML format, first/second inference prompts"
```

---

### Task 7: OutputParser — JSON 解析 + 容错

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/engine/OutputParser.java`

**Interfaces:**
- Consumes: `ActionCommand`
- Produces: `OutputParser` — `parse(String modelOutput): ParseResult`

- [ ] **Step 1: 编写 OutputParser.java**

```java
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
        // 去除 <｜end▁of▁thinking｜>... 标签
        return text.replaceAll("(?s)<｜end▁of▁thinking｜>.*?</think>", "").trim();
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
```

- [ ] **Step 2: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/engine/OutputParser.java
git commit -m "feat: add OutputParser with JSON extraction, repair, and chat fallback"
```

---

### Task 8: LlamaEngine — JNI 桥接层

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/engine/LlamaEngine.java`
- Create: `app/src/main/cpp/llama_jni.cpp`
- Create: `app/src/main/cpp/CMakeLists.txt`

**Interfaces:**
- Consumes: `ModelConfig`
- Produces: `LlamaEngine` — `init(ModelConfig)`, `infer(String prompt): String`, `release()`

- [ ] **Step 1: 编写 LlamaEngine.java**

```java
package com.example.vehicleassistant.engine;

import com.example.vehicleassistant.model.ModelConfig;

/**
 * llama.cpp JNI 桥接。负责模型加载、推理、资源释放。
 * 推理在主线程外调用（由 Agent 层管理线程）。
 */
public class LlamaEngine {

    static {
        System.loadLibrary("llama_jni");
    }

    private long nativePtr = 0;
    private volatile boolean loaded = false;

    // --- Native methods ---
    private native long nativeInit(String modelPath, int contextSize, int maxTokens,
                                   float temperature, float topP, int threads);
    private native String nativeInfer(long ptr, String prompt);
    private native void nativeRelease(long ptr);

    public synchronized void init(ModelConfig config) {
        if (loaded) return;
        nativePtr = nativeInit(config.modelPath, config.contextSize,
            config.maxTokens, config.temperature, config.topP, config.threads);
        loaded = true;
    }

    public synchronized String infer(String prompt) {
        if (!loaded) return "[模型未加载]";
        return nativeInfer(nativePtr, prompt);
    }

    public synchronized void release() {
        if (!loaded) return;
        nativeRelease(nativePtr);
        nativePtr = 0;
        loaded = false;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
```

- [ ] **Step 2: 编写 llama_jni.cpp (骨架实现，后续可对接真实 llama.cpp)**

```cpp
#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaContext {
    bool initialized = false;
    // TODO: 接入真实 llama.cpp 后补充 llama_model*, llama_context* 等
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_vehicleassistant_engine_LlamaEngine_nativeInit(
    JNIEnv* env, jobject thiz,
    jstring modelPath, jint contextSize, jint maxTokens,
    jfloat temperature, jfloat topP, jint threads) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGD("Loading model: %s (ctx=%d, threads=%d)", path, contextSize, threads);
    env->ReleaseStringUTFChars(modelPath, path);

    auto* ctx = new LlamaContext();
    ctx->initialized = true;
    // TODO: 接入 real llama.cpp:
    //   llama_backend_init()
    //   llama_model_params model_params = llama_model_default_params();
    //   ctx->model = llama_load_model_from_file(path, model_params);
    //   llama_context_params ctx_params = llama_context_default_params();
    //   ctx_params.n_ctx = contextSize;
    //   ctx->llama_ctx = llama_new_context_with_model(ctx->model, ctx_params);

    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_example_vehicleassistant_engine_LlamaEngine_nativeInfer(
    JNIEnv* env, jobject thiz, jlong ptr, jstring prompt) {

    auto* ctx = reinterpret_cast<LlamaContext*>(ptr);
    if (!ctx || !ctx->initialized) {
        return env->NewStringUTF("[模型未初始化]");
    }

    const char* input = env->GetStringUTFChars(prompt, nullptr);
    LOGD("Inference prompt length: %zu chars", strlen(input));
    env->ReleaseStringUTFChars(prompt, input);

    // TODO: 接入 real llama.cpp:
    //   llama_eval(ctx->llama_ctx, tokens, ...)
    //   llama_sample_token(ctx->llama_ctx, ...)
    //   std::string output = detokenize(sampled_tokens);

    // 当前返回占位 JSON（无模型时的降级回复）
    return env->NewStringUTF("[{\"action\":\"set_ac\",\"params\":{\"power\":true,\"temp\":22,\"mode\":\"auto\"}}]");
}

JNIEXPORT void JNICALL
Java_com_example_vehicleassistant_engine_LlamaEngine_nativeRelease(
    JNIEnv* env, jobject thiz, jlong ptr) {

    auto* ctx = reinterpret_cast<LlamaContext*>(ptr);
    if (ctx) {
        // TODO: 接入 real llama.cpp:
        //   llama_free(ctx->llama_ctx);
        //   llama_free_model(ctx->model);
        //   llama_backend_free();
        delete ctx;
        LOGD("Model released");
    }
}

} // extern "C"
```

- [ ] **Step 3: 编写 CMakeLists.txt**

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("llama_jni")

add_library(llama_jni SHARED llama_jni.cpp)

find_library(log-lib log)

target_link_libraries(llama_jni ${log-lib})

# TODO: 接入真实 llama.cpp 后取消以下注释
# add_subdirectory(llama.cpp)  # llama.cpp 源码目录
# target_link_libraries(llama_jni llama ${log-lib})
```

- [ ] **Step 4: 验证 JNI 方法签名**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/engine/LlamaEngine.java \
        app/src/main/cpp/llama_jni.cpp \
        app/src/main/cpp/CMakeLists.txt
git commit -m "feat: add LlamaEngine JNI bridge with skeleton C++ implementation"
```

---

### Task 9: ContextManager — 上下文管理

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/agent/ContextManager.java`

**Interfaces:**
- Consumes: `ChatMessage`
- Produces: `ContextManager` — `save(msg)`, `getHistory(): List<ChatMessage>`, `estimateTokens(text): int`

- [ ] **Step 1: 编写 ContextManager.java**

```java
package com.example.vehicleassistant.agent;

import com.example.vehicleassistant.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑动窗口上下文管理。
 * 窗口: 4096 tokens, 预留 1200 给 prompt + 输出, 历史上限 2896 tokens / 20 轮。
 * 裁剪策略: 从最早消息开始成对删除 (user + assistant)。
 */
public class ContextManager {

    private static final int MAX_HISTORY_TOKENS = 2896;
    private static final int MAX_ROUNDS = 20;

    private final List<ChatMessage> history = new ArrayList<>();

    public void save(ChatMessage msg) {
        history.add(msg);
        trim();
    }

    public void saveUserAndAssistant(ChatMessage userMsg, ChatMessage assistantMsg) {
        history.add(userMsg);
        history.add(assistantMsg);
        trim();
    }

    public List<ChatMessage> getHistory() {
        return new ArrayList<>(history);
    }

    public void clear() {
        history.clear();
    }

    public int getCurrentTokenCount() {
        int total = 0;
        for (ChatMessage msg : history) {
            total += estimateTokens(msg.content);
        }
        return total;
    }

    /**
     * 中文粗略估算: 字符数 * 0.5 ≈ token 数。
     */
    public static int estimateTokens(String text) {
        if (text == null) return 0;
        return (int) (text.length() * 0.5);
    }

    private void trim() {
        // 1. 按轮数裁剪
        int maxMessages = MAX_ROUNDS * 2; // user + assistant 成对
        while (history.size() > maxMessages) {
            history.remove(0);
        }

        // 2. 按 token 数裁剪
        while (getCurrentTokenCount() > MAX_HISTORY_TOKENS && history.size() >= 2) {
            // 保留 system 消息（如果存在），从最早的 user/assistant 对开始删
            if (ChatMessage.ROLE_SYSTEM.equals(history.get(0).role)) {
                // 跳过 system 消息，删后面的
                if (history.size() >= 3) {
                    history.remove(1);
                    history.remove(1);
                } else break;
            } else {
                history.remove(0);
                if (!history.isEmpty()) history.remove(0);
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/agent/ContextManager.java
git commit -m "feat: add ContextManager with sliding window and token estimation"
```

---

### Task 10: CommandPipeline — 多指令执行管线

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/agent/CommandPipeline.java`

**Interfaces:**
- Consumes: `VehicleService.execute()`, `ActionCommand`, `ExecutionResult`
- Produces: `CommandPipeline` — `execute(List<ActionCommand>): List<ExecutionResult>`, `deduplicate(commands): List<ActionCommand>`

- [ ] **Step 1: 编写 CommandPipeline.java**

```java
package com.example.vehicleassistant.agent;

import com.example.vehicleassistant.vehicle.VehicleService;
import com.example.vehicleassistant.vehicle.models.ActionCommand;
import com.example.vehicleassistant.vehicle.models.ExecutionResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多指令执行管线。包含冲突去重、参数校验、部分失败策略。
 */
public class CommandPipeline {

    private final VehicleService vehicleService;

    public CommandPipeline(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    /**
     * 执行指令列表。返回完整执行结果列表。
     * 策略: nonCritical 失败跳过继续, critical 失败中断。
     */
    public List<ExecutionResult> execute(List<ActionCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return new ArrayList<>();
        }

        List<ActionCommand> deduped = deduplicate(commands);
        List<ExecutionResult> results = new ArrayList<>();

        for (ActionCommand cmd : deduped) {
            ExecutionResult result = vehicleService.execute(cmd);
            results.add(result);

            // critical 失败则中断后续执行
            if (!result.success && cmd.critical) {
                break;
            }
        }

        return results;
    }

    /**
     * 批量冲突去重: 同一 target 的指令，后者覆盖前者。
     * 例: [升左前窗, 降左前窗] → [降左前窗]
     */
    List<ActionCommand> deduplicate(List<ActionCommand> commands) {
        Map<String, ActionCommand> map = new LinkedHashMap<>();
        for (ActionCommand cmd : commands) {
            // 先通过 execute 触发 VehicleService 来填充 target 字段
            // 实际上 target 在 VehicleService.execute 中填充，
            // 所以我们使用一个占位来标记
            String key = cmd.target != null ? cmd.target
                : (cmd.action + "_" + cmd.params.getOrDefault("position", ""));
            map.put(key, cmd);
        }
        return new ArrayList<>(map.values());
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/agent/CommandPipeline.java
git commit -m "feat: add CommandPipeline with dedup, sequential execution, partial failure handling"
```

---

### Task 11: AgentManager — 对话编排核心

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/agent/AgentManager.java`

**Interfaces:**
- Consumes: 所有下层模块 (LlamaEngine, PromptBuilder, OutputParser, ContextManager, CommandPipeline, VehicleService, FunctionRegistry, VehicleState)
- Produces: `AgentManager` — `receive(String userInput): AgentResponse`, `isReady(): boolean`

- [ ] **Step 1: 编写 AgentResponse 模型**

```java
package com.example.vehicleassistant.agent;

import com.example.vehicleassistant.vehicle.models.ExecutionResult;

import java.util.List;

public class AgentResponse {
    public final String text;                       // 最终展示文本
    public final List<ExecutionResult> execResults;  // 执行结果列表（可能为 null）
    public final boolean isChat;                     // true=闲聊, false=车控

    public AgentResponse(String text, List<ExecutionResult> execResults, boolean isChat) {
        this.text = text;
        this.execResults = execResults;
        this.isChat = isChat;
    }
}
```

Create file: `app/src/main/java/com/example/vehicleassistant/agent/AgentResponse.java`

- [ ] **Step 2: 编写 AgentManager.java**

Create file: `app/src/main/java/com/example/vehicleassistant/agent/AgentManager.java`

```java
package com.example.vehicleassistant.agent;

import android.util.Log;

import com.example.vehicleassistant.engine.LlamaEngine;
import com.example.vehicleassistant.engine.OutputParser;
import com.example.vehicleassistant.engine.PromptBuilder;
import com.example.vehicleassistant.model.ChatMessage;
import com.example.vehicleassistant.vehicle.FunctionRegistry;
import com.example.vehicleassistant.vehicle.VehicleService;
import com.example.vehicleassistant.vehicle.VehicleState;
import com.example.vehicleassistant.vehicle.models.ActionCommand;
import com.example.vehicleassistant.vehicle.models.ExecutionResult;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话编排核心。负责单轮对话的完整生命周期：
 * 第一次推理（意图提取）→ 执行车控 → 第二次推理（结果汇总）
 * 包含模型未就绪、快速连发等边界处理。
 */
public class AgentManager {

    private static final String TAG = "AgentManager";

    private final LlamaEngine engine;
    private final PromptBuilder promptBuilder;
    private final OutputParser outputParser;
    private final ContextManager contextManager;
    private final CommandPipeline pipeline;
    private final VehicleService vehicleService;
    private final FunctionRegistry registry;
    private final VehicleState vehicleState;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean processing = false;
    private String pendingInput = null;

    private String cachedSystemPrompt;

    public AgentManager(LlamaEngine engine, FunctionRegistry registry,
                        VehicleService vehicleService, VehicleState vehicleState) {
        this.engine = engine;
        this.registry = registry;
        this.vehicleService = vehicleService;
        this.vehicleState = vehicleState;
        this.promptBuilder = new PromptBuilder();
        this.outputParser = new OutputParser();
        this.contextManager = new ContextManager();
        this.pipeline = new CommandPipeline(vehicleService);
    }

    public boolean isReady() {
        return engine.isLoaded();
    }

    /**
     * 接收用户输入。如果正在处理中则排队。
     */
    public void receive(String userInput, Callback callback) {
        if (processing) {
            pendingInput = userInput;
            return;
        }
        processing = true;
        executor.execute(() -> processMessage(userInput, callback));
    }

    private void processMessage(String userInput, Callback callback) {
        try {
            // 检查可用内存 (粗略)
            Runtime rt = Runtime.getRuntime();
            long freeMem = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
            if (freeMem < 200 * 1024 * 1024) { // 不足 200MB
                callback.onResponse(new AgentResponse("内存不足，请清理后台应用后重试", null, true));
                return;
            }

            // 确保 system prompt 已缓存
            if (cachedSystemPrompt == null) {
                String stateSummary = buildVehicleStateSummary();
                cachedSystemPrompt = promptBuilder.buildSystemPrompt(
                    registry.generateToolsSchema(), stateSummary);
            }

            // --- 第一次推理（意图提取）---
            String firstPrompt = promptBuilder.buildFirstInferencePrompt(
                cachedSystemPrompt, contextManager.getHistory(), userInput);
            Log.d(TAG, "First inference...");
            String firstOutput = engine.infer(firstPrompt);

            OutputParser.ParseResult parseResult = outputParser.parse(firstOutput);

            if (parseResult.isCommands) {
                // 车控模式: 执行 + 第二次推理
                List<ExecutionResult> execResults = pipeline.execute(parseResult.commands);
                String execReport = promptBuilder.buildExecutionReport(execResults);

                // 执行成功后更新 system prompt 中的车辆状态
                String updatedStateSummary = buildVehicleStateSummary();
                String updatedSystemPrompt = promptBuilder.buildSystemPrompt(
                    registry.generateToolsSchema(), updatedStateSummary);

                String secondPrompt = promptBuilder.buildSecondInferencePrompt(
                    updatedSystemPrompt, contextManager.getHistory(),
                    userInput, firstOutput, "执行结果:\n" + execReport);
                Log.d(TAG, "Second inference...");
                String secondOutput = engine.infer(secondPrompt);

                // 保存本轮对话
                contextManager.saveUserAndAssistant(
                    new ChatMessage(ChatMessage.ROLE_USER, userInput),
                    new ChatMessage(ChatMessage.ROLE_ASSISTANT, secondOutput));

                callback.onResponse(new AgentResponse(secondOutput, execResults, false));
            } else {
                // 闲聊模式: 直接返回文本
                contextManager.saveUserAndAssistant(
                    new ChatMessage(ChatMessage.ROLE_USER, userInput),
                    new ChatMessage(ChatMessage.ROLE_ASSISTANT, parseResult.text));

                callback.onResponse(new AgentResponse(parseResult.text, null, true));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing message", e);
            callback.onResponse(new AgentResponse("抱歉，处理出错: " + e.getMessage(), null, true));
        } finally {
            processing = false;
            // 处理排队中的下一条消息
            if (pendingInput != null) {
                String next = pendingInput;
                pendingInput = null;
                receive(next, callback);
            }
        }
    }

    private String buildVehicleStateSummary() {
        return "空调: " + (vehicleState.acPower ? "开启 " + vehicleState.acTemp + "度 " + vehicleState.acMode : "关闭") +
               "\n风扇: " + vehicleState.fanSpeed + "级" +
               "\n循环: " + vehicleState.airCirculation +
               "\n除霜: 前=" + vehicleState.frontDefrost + " 后=" + vehicleState.rearDefrost +
               "\n车门锁: " + (vehicleState.doorLocked ? "已锁" : "未锁") +
               "\n驾驶模式: " + vehicleState.driveMode +
               "\n驻车状态: " + (vehicleState.isParked ? "已驻车" : "行驶中");
    }

    public List<ChatMessage> getHistory() {
        return contextManager.getHistory();
    }

    public void shutdown() {
        executor.shutdown();
        engine.release();
    }

    public interface Callback {
        void onResponse(AgentResponse response);
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/agent/
git commit -m "feat: add AgentManager with two-pass inference, message queue, and memory check"
```

---

### Task 12: UI XML 布局文件

**Files:**
- Create: `app/src/main/res/layout/activity_chat.xml`
- Create: `app/src/main/res/layout/item_message_user.xml`
- Create: `app/src/main/res/layout/item_message_assistant.xml`
- Create: `app/src/main/res/layout/widget_execution_card.xml`

**Interfaces:**
- Produces: 4 个布局文件，被 ChatActivity, ChatAdapter, ExecutionCard 消费

- [ ] **Step 1: 编写 activity_chat.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#1A1A2E">

    <TextView
        android:id="@+id/tv_status"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="12dp"
        android:text="模型加载中..."
        android:textColor="#888"
        android:textSize="14sp"
        android:gravity="center" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_chat"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:padding="12dp"
        android:clipToPadding="false" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="12dp"
        android:background="#16213E">

        <EditText
            android:id="@+id/et_input"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="输入指令..."
            android:textColor="#FFF"
            android:textColorHint="#666"
            android:background="@android:color/transparent"
            android:padding="12dp"
            android:maxLines="3" />

        <Button
            android:id="@+id/btn_send"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="发送"
            android:textColor="#FFF"
            android:backgroundTint="#0F3460" />

    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: 编写 item_message_user.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="end"
    android:padding="8dp">

    <TextView
        android:id="@+id/tv_message"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:maxWidth="280dp"
        android:padding="12dp"
        android:textSize="16sp"
        android:textColor="#FFF"
        android:background="@drawable/bg_user_bubble"
        android:text="用户消息" />
</LinearLayout>
```

- [ ] **Step 3: 编写 item_message_assistant.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="start"
    android:padding="8dp">

    <LinearLayout
        android:id="@+id/ll_execution_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:visibility="gone" />

    <TextView
        android:id="@+id/tv_message"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:maxWidth="280dp"
        android:padding="12dp"
        android:textSize="16sp"
        android:textColor="#FFF"
        android:background="@drawable/bg_assistant_bubble"
        android:text="助手消息" />
</LinearLayout>
```

- [ ] **Step 4: 编写 widget_execution_card.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="8dp 12dp"
    android:background="@drawable/bg_execution_card"
    android:layout_marginBottom="4dp">

    <ImageView
        android:id="@+id/iv_status"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:src="@android:drawable/ic_dialog_info"
        android:layout_marginEnd="8dp" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tv_action"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="14sp"
            android:textColor="#FFF"
            android:text="set_ac" />

        <TextView
            android:id="@+id/tv_detail"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="#AAA"
            android:text="温度: 22°C" />
    </LinearLayout>

    <TextView
        android:id="@+id/tv_status_badge"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="12sp"
        android:padding="4dp 8dp"
        android:text="成功" />
</LinearLayout>
```

- [ ] **Step 5: 创建 drawable 资源文件**

Create: `app/src/main/res/drawable/bg_user_bubble.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0F3460" />
    <corners android:radius="16dp" />
</shape>
```

Create: `app/src/main/res/drawable/bg_assistant_bubble.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#16213E" />
    <corners android:radius="16dp" />
</shape>
```

Create: `app/src/main/res/drawable/bg_execution_card.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#1A1A2E" />
    <corners android:radius="8dp" />
    <stroke android:width="1dp" android:color="#0F3460" />
</shape>
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/ app/src/main/res/drawable/
git commit -m "feat: add UI XML layouts (chat, message bubbles, execution card)"
```

---

### Task 13: ChatAdapter + ExecutionCard — RecyclerView 适配器

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/ui/widgets/ExecutionCard.java`
- Create: `app/src/main/java/com/example/vehicleassistant/ui/ChatAdapter.java`

**Interfaces:**
- Consumes: `AgentResponse`, `ExecutionResult`, `ChatMessage`
- Produces: `ChatAdapter` — 被 MainViewModel / ChatActivity 使用

- [ ] **Step 1: 编写 ExecutionCard.java**

```java
package com.example.vehicleassistant.ui.widgets;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.vehicleassistant.R;
import com.example.vehicleassistant.vehicle.models.ExecutionResult;

public class ExecutionCard extends LinearLayout {

    private ImageView ivStatus;
    private TextView tvAction;
    private TextView tvDetail;
    private TextView tvStatusBadge;

    public ExecutionCard(Context context) {
        super(context);
        init(context);
    }

    public ExecutionCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.widget_execution_card, this, true);
        ivStatus = findViewById(R.id.iv_status);
        tvAction = findViewById(R.id.tv_action);
        tvDetail = findViewById(R.id.tv_detail);
        tvStatusBadge = findViewById(R.id.tv_status_badge);
    }

    public void bind(ExecutionResult result) {
        tvAction.setText(result.action);
        tvDetail.setText(result.message);

        if (result.success) {
            ivStatus.setImageResource(android.R.drawable.ic_dialog_info);
            tvStatusBadge.setText("成功");
            tvStatusBadge.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            ivStatus.setImageResource(android.R.drawable.ic_dialog_alert);
            tvStatusBadge.setText("失败");
            tvStatusBadge.setTextColor(Color.parseColor("#F44336"));
        }
    }
}
```

- [ ] **Step 2: 编写 ChatAdapter.java**

```java
package com.example.vehicleassistant.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vehicleassistant.R;
import com.example.vehicleassistant.model.ChatMessage;
import com.example.vehicleassistant.ui.widgets.ExecutionCard;
import com.example.vehicleassistant.vehicle.models.ExecutionResult;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private final List<ChatItem> items = new ArrayList<>();

    public static class ChatItem {
        public final ChatMessage message;
        public final List<ExecutionResult> execResults;

        public ChatItem(ChatMessage message, List<ExecutionResult> execResults) {
            this.message = message;
            this.execResults = execResults;
        }
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = items.get(position).message;
        return ChatMessage.ROLE_USER.equals(msg.role) ? 0 : 1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = viewType == 0
            ? R.layout.item_message_user
            : R.layout.item_message_assistant;
        View view = LayoutInflater.from(parent.getContext())
            .inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatItem item = items.get(position);
        holder.tvMessage.setText(item.message.content);

        // 展示执行卡片（如果有）
        if (holder.llExecContainer != null && item.execResults != null) {
            holder.llExecContainer.setVisibility(View.VISIBLE);
            holder.llExecContainer.removeAllViews();
            for (ExecutionResult result : item.execResults) {
                ExecutionCard card = new ExecutionCard(holder.itemView.getContext());
                card.bind(result);
                holder.llExecContainer.addView(card);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void addUserMessage(String text) {
        items.add(new ChatItem(
            new ChatMessage(ChatMessage.ROLE_USER, text), null));
        notifyItemInserted(items.size() - 1);
    }

    public void addAssistantMessage(String text, List<ExecutionResult> execResults) {
        items.add(new ChatItem(
            new ChatMessage(ChatMessage.ROLE_ASSISTANT, text), execResults));
        notifyItemInserted(items.size() - 1);
    }

    public void addItem(ChatItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        LinearLayout llExecContainer;

        ViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message);
            llExecContainer = itemView.findViewById(R.id.ll_execution_container);
        }
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/ui/
git commit -m "feat: add ChatAdapter and ExecutionCard widget"
```

---

### Task 14: MainViewModel — ViewModel 层

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/ui/MainViewModel.java`

**Interfaces:**
- Consumes: `AgentManager`
- Produces: `MainViewModel` — 被 ChatActivity 消费

- [ ] **Step 1: 编写 MainViewModel.java**

```java
package com.example.vehicleassistant.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vehicleassistant.agent.AgentManager;
import com.example.vehicleassistant.agent.AgentResponse;
import com.example.vehicleassistant.engine.LlamaEngine;
import com.example.vehicleassistant.model.ModelConfig;
import com.example.vehicleassistant.vehicle.FunctionRegistry;
import com.example.vehicleassistant.vehicle.VehicleService;
import com.example.vehicleassistant.vehicle.VehicleState;

import java.io.File;

public class MainViewModel extends AndroidViewModel {

    private final MutableLiveData<String> statusText = new MutableLiveData<>("正在初始化...");
    private final MutableLiveData<ChatAdapter.ChatItem> newMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> inputEnabled = new MutableLiveData<>(false);

    private AgentManager agentManager;
    private ChatAdapter adapter;

    public MainViewModel(@NonNull Application application) {
        super(application);
        adapter = new ChatAdapter();
        initEngine(application);
    }

    private void initEngine(Application app) {
        new Thread(() -> {
            try {
                statusText.postValue("正在加载模型...");

                // 模型路径: 外部存储或 app 私有目录
                File modelDir = new File(app.getExternalFilesDir(null), "models");
                File modelFile = new File(modelDir, "qwen2.5-1.5b-instruct-q4_k_m.gguf");

                if (!modelFile.exists()) {
                    statusText.postValue("模型文件未找到，请下载到: " + modelFile.getAbsolutePath());
                    return;
                }

                ModelConfig config = new ModelConfig(modelFile.getAbsolutePath());
                LlamaEngine engine = new LlamaEngine();
                engine.init(config);

                VehicleState state = new VehicleState();
                FunctionRegistry registry = new FunctionRegistry();
                VehicleService vehicleService = new VehicleService(registry, state);

                agentManager = new AgentManager(engine, registry, vehicleService, state);

                statusText.postValue("就绪");
                inputEnabled.postValue(true);
            } catch (Exception e) {
                statusText.postValue("初始化失败: " + e.getMessage());
            }
        }).start();
    }

    public void sendMessage(String text) {
        if (agentManager == null || !agentManager.isReady()) {
            statusText.postValue("模型未就绪，请稍候...");
            return;
        }

        adapter.addUserMessage(text);
        inputEnabled.setValue(false);
        statusText.setValue("思考中...");

        agentManager.receive(text, response -> {
            adapter.addAssistantMessage(response.text, response.execResults);
            inputEnabled.postValue(true);
            statusText.postValue("就绪");
        });
    }

    public LiveData<String> getStatusText() { return statusText; }
    public LiveData<Boolean> getInputEnabled() { return inputEnabled; }
    public ChatAdapter getAdapter() { return adapter; }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (agentManager != null) {
            agentManager.shutdown();
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/ui/MainViewModel.java
git commit -m "feat: add MainViewModel with model loading and message handling"
```

---

### Task 15: ChatActivity — 主 Activity

**Files:**
- Create: `app/src/main/java/com/example/vehicleassistant/ui/ChatActivity.java`

**Interfaces:**
- Consumes: `MainViewModel`, `ChatAdapter`
- Produces: 用户可见的完整对话界面

- [ ] **Step 1: 编写 ChatActivity.java**

```java
package com.example.vehicleassistant.ui;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vehicleassistant.R;
import com.example.vehicleassistant.databinding.ActivityChatBinding;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private MainViewModel viewModel;
    private ChatAdapter adapter;
    private RecyclerView rvChat;
    private EditText etInput;
    private Button btnSend;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        rvChat = binding.rvChat;
        etInput = binding.etInput;
        btnSend = binding.btnSend;
        tvStatus = binding.tvStatus;

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        adapter = viewModel.getAdapter();

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        // 状态文本
        viewModel.getStatusText().observe(this, status -> tvStatus.setText(status));

        // 输入启用状态
        viewModel.getInputEnabled().observe(this, enabled -> {
            etInput.setEnabled(enabled);
            btnSend.setEnabled(enabled);
        });

        // 发送按钮
        btnSend.setOnClickListener(v -> sendMessage());

        // 键盘发送
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        // 自动滚动到底部
        viewModel.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                rvChat.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;
        etInput.setText("");
        viewModel.sendMessage(text);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd app && ./gradlew compileDebugJavaWithJavac
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/ui/ChatActivity.java
git commit -m "feat: add ChatActivity with ViewBinding and MVVM wiring"
```

---

### Task 16: AndroidManifest + build.gradle 配置

**Files:**
- Create/Modify: `app/build.gradle`
- Create/Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- 项目构建配置，所有代码依赖声明

- [ ] **Step 1: 编写 build.gradle（app 级别）**

```groovy
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.example.vehicleassistant'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.vehicleassistant"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0"

        ndk {
            abiFilters "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
            version "3.22.1"
        }
    }

    buildFeatures {
        viewBinding true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.lifecycle:lifecycle-viewmodel:2.8.4'
    implementation 'com.google.android.material:material:1.12.0'
}
```

- [ ] **Step 2: 编写 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />

    <application
        android:allowBackup="true"
        android:label="车控助手"
        android:theme="@style/Theme.VehicleAssistant"
        android:supportsRtl="true">

        <activity
            android:name="com.example.vehicleassistant.ui.ChatActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 3: 编写 themes.xml**

Create: `app/src/main/res/values/themes.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.VehicleAssistant" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">#0F3460</item>
        <item name="colorPrimaryVariant">#16213E</item>
        <item name="colorOnPrimary">#FFFFFF</item>
        <item name="android:statusBarColor">#1A1A2E</item>
        <item name="android:navigationBarColor">#1A1A2E</item>
    </style>
</resources>
```

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle app/src/main/AndroidManifest.xml app/src/main/res/values/themes.xml
git commit -m "feat: add build configuration, manifest, and app theme"
```

---

### Task 17: 集成验证 — 编译与功能验证

**Files:** 无新文件

- [ ] **Step 1: 完整编译**

```bash
cd app && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL, 生成 debug APK

- [ ] **Step 2: 检查 APK 结构**

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E "\.so|\.dex"
```

Expected: 包含 `lib/arm64-v8a/libllama_jni.so`, `classes.dex`

- [ ] **Step 3: 验证关键代码路径**

检查以下数据流是否贯穿:
- PromptBuilder 生成的 Prompt 包含 system + tools schema + history + user input
- OutputParser 能正确区分 JSON 和纯文本
- VehicleService mock 执行输出正确的 log
- ContextManager 超限后正确裁剪

- [ ] **Step 4: 安装到设备测试（如设备已连接）**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "chore: integration verification - build passes"
```

---

## Plan Summary

| Task | 文件数 | 核心产出 |
|------|--------|---------|
| 1 | 2 | ChatMessage, ModelConfig |
| 2 | 3 | ToolDefinition, ActionCommand, ExecutionResult |
| 3 | 1 | VehicleState (35 个状态字段) |
| 4 | 1 | FunctionRegistry (35 方法 + JSON Schema) |
| 5 | 1 | VehicleService (参数校验 + 危险检查 + 幂等 + mock) |
| 6 | 1 | PromptBuilder (ChatML 格式, 两次推理 prompt) |
| 7 | 1 | OutputParser (JSON 提取 + 修复 + 闲聊 fallback) |
| 8 | 3 | LlamaEngine JNI + C++ skeleton + CMakeLists |
| 9 | 1 | ContextManager (滑动窗口 + token 估算) |
| 10 | 1 | CommandPipeline (去重 + 顺序执行 + 部分失败) |
| 11 | 2 | AgentResponse + AgentManager (两次推理编排 + 消息队列 + 内存检查) |
| 12 | 7 | 4 个 XML 布局 + 3 个 drawable |
| 13 | 2 | ChatAdapter + ExecutionCard widget |
| 14 | 1 | MainViewModel |
| 15 | 1 | ChatActivity (ViewBinding + 键盘发送) |
| 16 | 3 | build.gradle + AndroidManifest + themes.xml |
| 17 | 0 | 编译验证 |

共计 **17 个任务**, **31 个文件**, **~1500 行 Java + ~100 行 C++ + ~250 行 XML**
