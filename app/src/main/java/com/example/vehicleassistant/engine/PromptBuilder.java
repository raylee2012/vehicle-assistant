package com.example.vehicleassistant.engine;

import com.example.vehicleassistant.model.ChatMessage;

import java.util.List;

/**
 * 组装 Qwen2.5 ChatML 格式 Prompt。
 */
public class PromptBuilder {

    private static final String SYSTEM_TEMPLATE_SIMPLE =
        "车控助手。指令→JSON数组[{\"action\":\"n\",\"params\":{}}]，闲聊→文本。\n" +
        "{vehicle_state}\n" +
        "{tools_schema}";

    private static final String SYSTEM_TEMPLATE_DETAILED =
        "你是车载语音助手。根据用户输入决定输出格式:\n" +
        "- 车控指令 → 只输出JSON数组，禁止加解释文字\n" +
        "- 闲聊 → 简短文本回复\n\n" +
        "JSON格式: [{\"action\":\"方法名\",\"params\":{\"参数\":值}}]\n" +
        "多个指令: [{\"action\":\"set_ac\",...},{\"action\":\"control_window\",...}]\n\n" +
        "可用方法:\n{tools_schema}\n" +
        "当前车辆状态: {vehicle_state}\n\n" +
        "示例:\n" +
        "用户: 打开空调26度 → [{\"action\":\"set_ac\",\"params\":{\"power\":true,\"temp\":26,\"mode\":\"auto\"}}]\n" +
        "用户: 关闭车窗 → [{\"action\":\"control_window\",\"params\":{\"position\":\"all\",\"action\":\"close\"}}]\n" +
        "用户: 打开空调并关闭车窗 → [{\"action\":\"set_ac\",\"params\":{\"power\":true,\"temp\":22,\"mode\":\"auto\"}},{\"action\":\"control_window\",\"params\":{\"position\":\"all\",\"action\":\"close\"}}]\n" +
        "用户: 搜索汽车视频 → [{\"action\":\"video_search\",\"params\":{\"keyword\":\"汽车\"}}]\n" +
        "用户: 你好 → 你好！有什么可以帮你的？";

    private static final String RESULT_PROMPT =
        "根据以下车控执行结果，用自然语言简洁地向用户汇报:\n" +
        "{execution_results}\n\n" +
        "要求: 一句话总结，成功的提一下，失败的重点说。";

    /** @param detailed true=1.5B 详细 few-shot，false=0.5B 简洁版 */
    public String buildSystemPrompt(String toolsSchema, String vehicleStateSummary, boolean detailed) {
        String template = detailed ? SYSTEM_TEMPLATE_DETAILED : SYSTEM_TEMPLATE_SIMPLE;
        return template
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
