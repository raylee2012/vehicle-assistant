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
