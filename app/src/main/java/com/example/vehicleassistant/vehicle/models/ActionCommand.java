package com.example.vehicleassistant.vehicle.models;

import java.util.Collections;
import java.util.Map;

public class ActionCommand {
    public String action;
    public Map<String, Object> params = Collections.emptyMap();
    public boolean critical;
    public String target;

    public ActionCommand() {}

    public ActionCommand(String action, Map<String, Object> params) {
        this.action = action;
        this.params = params != null ? params : Collections.emptyMap();
    }
}
