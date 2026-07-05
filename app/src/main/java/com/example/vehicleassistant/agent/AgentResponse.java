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
