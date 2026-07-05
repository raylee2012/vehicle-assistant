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
