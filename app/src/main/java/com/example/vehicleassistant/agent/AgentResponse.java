package com.example.vehicleassistant.agent;

import com.example.vehicleassistant.vehicle.models.ExecutionResult;

import java.util.List;

public class AgentResponse {
    public final String text;                       // 最终展示文本
    public final List<ExecutionResult> execResults;  // 执行结果列表（可能为 null）
    public final boolean isChat;                     // true=闲聊, false=车控
    public final String videoSearchKeyword;           // 非空 = 视频搜索

    public AgentResponse(String text, List<ExecutionResult> execResults, boolean isChat) {
        this(text, execResults, isChat, null);
    }

    public AgentResponse(String text, List<ExecutionResult> execResults, boolean isChat,
                         String videoSearchKeyword) {
        this.text = text;
        this.execResults = execResults;
        this.isChat = isChat;
        this.videoSearchKeyword = videoSearchKeyword;
    }

    public static AgentResponse videoSearch(String replyText, String keyword) {
        return new AgentResponse(replyText, null, true, keyword);
    }
}
