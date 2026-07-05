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
