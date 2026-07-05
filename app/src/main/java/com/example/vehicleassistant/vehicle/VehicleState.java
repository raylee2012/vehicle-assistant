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
    public boolean isParked = true;  // 当前 mock 为 true，始终驻车。接入真实传感器后改为实际值

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
