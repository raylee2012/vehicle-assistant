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
