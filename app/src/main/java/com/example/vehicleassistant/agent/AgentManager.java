package com.example.vehicleassistant.agent;

import android.util.Log;

import com.example.vehicleassistant.engine.LlamaEngine;
import com.example.vehicleassistant.engine.MockCommandExtractor;
import com.example.vehicleassistant.engine.OutputParser;
import com.example.vehicleassistant.engine.PromptBuilder;
import com.example.vehicleassistant.model.ChatMessage;
import com.example.vehicleassistant.vehicle.FunctionRegistry;
import com.example.vehicleassistant.vehicle.VehicleService;
import com.example.vehicleassistant.vehicle.VehicleState;
import com.example.vehicleassistant.vehicle.models.ActionCommand;
import com.example.vehicleassistant.vehicle.models.ExecutionResult;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话编排核心。负责单轮对话的完整生命周期：
 * 第一次推理（意图提取）→ 执行车控 → 第二次推理（结果汇总）
 * 包含模型未就绪、快速连发等边界处理。
 */
public class AgentManager {

    private static final String TAG = "AgentManager";

    private final LlamaEngine engine;
    private final PromptBuilder promptBuilder;
    private final OutputParser outputParser;
    private final ContextManager contextManager;
    private final CommandPipeline pipeline;
    private final VehicleService vehicleService;
    private final FunctionRegistry registry;
    private final VehicleState vehicleState;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean processing = false;
    private String pendingInput = null;

    // 骨架占位输出的特征字符串，用于判断是否走 mock 推理
    private static final String SKELETON_PLACEHOLDER =
        "[{\"action\":\"set_ac\",\"params\":{\"power\":true,\"temp\":22,\"mode\":\"auto\"}}]";

    private final MockCommandExtractor mockExtractor = new MockCommandExtractor();
    private String cachedSystemPrompt;

    public AgentManager(LlamaEngine engine, FunctionRegistry registry,
                        VehicleService vehicleService, VehicleState vehicleState) {
        this.engine = engine;
        this.registry = registry;
        this.vehicleService = vehicleService;
        this.vehicleState = vehicleState;
        this.promptBuilder = new PromptBuilder();
        this.outputParser = new OutputParser();
        this.contextManager = new ContextManager();
        this.pipeline = new CommandPipeline(vehicleService);
    }

    public boolean isReady() {
        return engine.isLoaded();
    }

    /**
     * 接收用户输入。如果正在处理中则排队。
     */
    public void receive(String userInput, Callback callback) {
        if (processing) {
            pendingInput = userInput;
            return;
        }
        processing = true;
        executor.execute(() -> processMessage(userInput, callback));
    }

    private void processMessage(String userInput, Callback callback) {
        try {
            // 检查可用内存 (粗略)
            Runtime rt = Runtime.getRuntime();
            long freeMem = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
            if (freeMem < 200 * 1024 * 1024) { // 不足 200MB
                callback.onResponse(new AgentResponse("内存不足，请清理后台应用后重试", null, true));
                return;
            }

            // 确保 system prompt 已缓存
            if (cachedSystemPrompt == null) {
                String stateSummary = buildVehicleStateSummary();
                cachedSystemPrompt = promptBuilder.buildSystemPrompt(
                    registry.generateToolsSchema(), stateSummary);
            }

            // --- 第一次推理（意图提取）---
            String firstPrompt = promptBuilder.buildFirstInferencePrompt(
                cachedSystemPrompt, contextManager.getHistory(), userInput);
            Log.d(TAG, "First inference... prompt=" + firstPrompt.length() + "chars, schema="
                + cachedSystemPrompt.length() + "chars");
            String firstOutput = engine.infer(firstPrompt);
            Log.d(TAG, "First output(" + firstOutput.length() + "chars): " + firstOutput);

            // 骨架占位检测：如果是硬编码的占位输出，使用关键词 mock 推理
            if (SKELETON_PLACEHOLDER.equals(firstOutput.trim())) {
                String mockJson = mockExtractor.extract(userInput);
                if (mockJson != null) {
                    firstOutput = mockJson;
                    Log.d(TAG, "使用 mock 推理: " + mockJson);
                }
            }

            OutputParser.ParseResult parseResult = outputParser.parse(firstOutput);
            Log.d(TAG, "Parse result: isCommands=" + parseResult.isCommands
                + " commands=" + (parseResult.commands != null ? parseResult.commands.size() : 0)
                + " text=" + (parseResult.text != null ? parseResult.text : "null"));

            if (parseResult.isCommands) {
                // 车控模式: 执行 + 第二次推理
                List<ExecutionResult> execResults = pipeline.execute(parseResult.commands);
                String execReport = promptBuilder.buildExecutionReport(execResults);

                // 执行成功后更新 system prompt 中的车辆状态
                String updatedStateSummary = buildVehicleStateSummary();
                String updatedSystemPrompt = promptBuilder.buildSystemPrompt(
                    registry.generateToolsSchema(), updatedStateSummary);

                String secondPrompt = promptBuilder.buildSecondInferencePrompt(
                    updatedSystemPrompt, contextManager.getHistory(),
                    userInput, firstOutput, "执行结果:\n" + execReport);
                Log.d(TAG, "Second inference...");
                String secondOutput = engine.infer(secondPrompt);

                // 保存本轮对话
                contextManager.saveUserAndAssistant(
                    new ChatMessage(ChatMessage.ROLE_USER, userInput),
                    new ChatMessage(ChatMessage.ROLE_ASSISTANT, secondOutput));

                callback.onResponse(new AgentResponse(secondOutput, execResults, false));
            } else {
                // 闲聊模式: 直接返回文本
                contextManager.saveUserAndAssistant(
                    new ChatMessage(ChatMessage.ROLE_USER, userInput),
                    new ChatMessage(ChatMessage.ROLE_ASSISTANT, parseResult.text));

                callback.onResponse(new AgentResponse(parseResult.text, null, true));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing message", e);
            callback.onResponse(new AgentResponse("抱歉，处理出错: " + e.getMessage(), null, true));
        } finally {
            processing = false;
            // 处理排队中的下一条消息
            if (pendingInput != null) {
                String next = pendingInput;
                pendingInput = null;
                receive(next, callback);
            }
        }
    }

    private String buildVehicleStateSummary() {
        return "空调: " + (vehicleState.acPower ? "开启 " + vehicleState.acTemp + "度 " + vehicleState.acMode : "关闭") +
               "\n风扇: " + vehicleState.fanSpeed + "级" +
               "\n循环: " + vehicleState.airCirculation +
               "\n除霜: 前=" + vehicleState.frontDefrost + " 后=" + vehicleState.rearDefrost +
               "\n车门锁: " + (vehicleState.doorLocked ? "已锁" : "未锁") +
               "\n驾驶模式: " + vehicleState.driveMode +
               "\n驻车状态: " + (vehicleState.isParked ? "已驻车" : "行驶中");
    }

    public List<ChatMessage> getHistory() {
        return contextManager.getHistory();
    }

    public void shutdown() {
        executor.shutdown();
        engine.release();
    }

    public interface Callback {
        void onResponse(AgentResponse response);
    }
}
