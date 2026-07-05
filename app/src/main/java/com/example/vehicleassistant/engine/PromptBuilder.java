package com.example.vehicleassistant.engine;

import com.example.vehicleassistant.model.ChatMessage;

import java.util.List;

/**
 * 组装 Qwen2.5 ChatML 格式 Prompt。
 */
public class PromptBuilder {

    private static final String SYSTEM_TEMPLATE =
        "你是车控助手。规则:\n" +
        "- 车控指令→输出JSON数组:[{\"action\":\"方法名\",\"params\":{}}]\n" +
        "- 一条指令也要用[]包裹\n" +
        "- 闲聊/模糊/否定→直接回文本,不输出JSON\n" +
        "- 模糊意图需追问确认\n\n" +
        "车辆状态: {vehicle_state}\n\n" +
        "方法:\n" +
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
