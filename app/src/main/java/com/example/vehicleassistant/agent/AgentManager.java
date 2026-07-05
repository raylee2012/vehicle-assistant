package com.example.vehicleassistant.agent;

import android.util.Log;

import com.example.vehicleassistant.engine.LlamaEngine;
import com.example.vehicleassistant.engine.MockCommandExtractor;
import com.example.vehicleassistant.engine.OutputParser;
import com.example.vehicleassistant.engine.PromptBuilder;
import com.example.vehicleassistant.engine.VideoSearchHelper;
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
    private boolean pendingMockFirst = true;

    // 骨架占位输出的特征字符串，用于判断是否走 mock 推理
    private static final String SKELETON_PLACEHOLDER =
        "[{\"action\":\"set_ac\",\"params\":{\"power\":true,\"temp\":22,\"mode\":\"auto\"}}]";

    private final MockCommandExtractor mockExtractor = new MockCommandExtractor();
    private String cachedSystemPrompt;
    private final boolean detailedPrompt;

    public AgentManager(LlamaEngine engine, FunctionRegistry registry,
                        VehicleService vehicleService, VehicleState vehicleState,
                        boolean detailedPrompt) {
        this.engine = engine;
        this.registry = registry;
        this.vehicleService = vehicleService;
        this.vehicleState = vehicleState;
        this.detailedPrompt = detailedPrompt;
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
    public void receive(String userInput, Callback callback, boolean mockFirst) {
        if (processing) {
            pendingInput = userInput;
            pendingMockFirst = mockFirst;
            return;
        }
        processing = true;
        executor.execute(() -> processMessage(userInput, callback, mockFirst));
    }

    private void processMessage(String userInput, Callback callback, boolean mockFirst) {
        try {
            // 检查可用内存 (粗略)
            Runtime rt = Runtime.getRuntime();
            long freeMem = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
            if (freeMem < 200 * 1024 * 1024) { // 不足 200MB
                callback.onResponse(new AgentResponse("内存不足，请清理后台应用后重试", null, true));
                return;
            }

            // 视频搜索关键词拦截 — 在模型推理前处理
            String videoKeyword = VideoSearchHelper.extractKeyword(userInput);
            if (videoKeyword != null) {
                Log.d(TAG, "Video search: " + videoKeyword);
                callback.onResponse(AgentResponse.videoSearch(
                    "已为您找到以下视频结果", videoKeyword));
                return;
            }

            // 确保 system prompt 已缓存
            if (cachedSystemPrompt == null) {
                String stateSummary = buildVehicleStateSummary();
                cachedSystemPrompt = promptBuilder.buildSystemPrompt(
                    registry.generateToolsSchema(), stateSummary, detailedPrompt);
            }

            // --- 意图提取：mockFirst 决定优先级 ---
            OutputParser.ParseResult parseResult;
            String firstOutput;

            if (mockFirst) {
                // Mock 优先：关键词匹配 → 模型兜底
                String mockJson = mockExtractor.extract(userInput);
                if (mockJson != null) {
                    firstOutput = mockJson;
                    parseResult = outputParser.parse(mockJson);
                    Log.d(TAG, "Mock intent: " + mockJson);
                } else {
                    firstOutput = doModelInference(userInput);
                    parseResult = outputParser.parse(firstOutput);
                }
            } else {
                // 模型优先：Function Calling → Mock 兜底
                firstOutput = doModelInference(userInput);
                parseResult = outputParser.parse(firstOutput);
                if (!parseResult.isCommands || SKELETON_PLACEHOLDER.equals(firstOutput.trim())) {
                    String mockJson = mockExtractor.extract(userInput);
                    if (mockJson != null) {
                        firstOutput = mockJson;
                        parseResult = outputParser.parse(mockJson);
                        Log.d(TAG, "Model failed, mock fallback: " + mockJson);
                    }
                }
            }

            if (parseResult.isCommands) {
                // 车控模式: 执行 + 第二次推理
                List<ExecutionResult> execResults = pipeline.execute(parseResult.commands);
                String execReport = promptBuilder.buildExecutionReport(execResults);

                // 执行成功后更新 system prompt 中的车辆状态
                String updatedStateSummary = buildVehicleStateSummary();
                String updatedSystemPrompt = promptBuilder.buildSystemPrompt(
                    registry.generateToolsSchema(), updatedStateSummary, detailedPrompt);

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
                receive(next, callback, pendingMockFirst);
            }
        }
    }

    private String doModelInference(String userInput) {
        String firstPrompt = promptBuilder.buildFirstInferencePrompt(
            cachedSystemPrompt, contextManager.getHistory(), userInput);
        Log.d(TAG, "Model inference... prompt=" + firstPrompt.length() + "chars");
        String output = engine.infer(firstPrompt);
        Log.d(TAG, "Model output(" + output.length() + "chars): " + output);
        return output;
    }

    private String buildVehicleStateSummary() {
        return "状态: AC=" + (vehicleState.acPower ? "开" + vehicleState.acTemp + "°" + vehicleState.acMode : "关") +
               " 风=" + vehicleState.fanSpeed +
               " 锁=" + (vehicleState.doorLocked ? "锁" : "开") +
               " 驾=" + vehicleState.driveMode +
               " P=" + (vehicleState.isParked ? "1" : "0");
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
