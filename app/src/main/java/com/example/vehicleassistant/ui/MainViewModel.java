package com.example.vehicleassistant.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vehicleassistant.agent.AgentManager;
import com.example.vehicleassistant.agent.AgentResponse;
import com.example.vehicleassistant.engine.LlamaEngine;
import com.example.vehicleassistant.model.ModelConfig;
import com.example.vehicleassistant.vehicle.FunctionRegistry;
import com.example.vehicleassistant.vehicle.VehicleService;
import com.example.vehicleassistant.vehicle.VehicleState;

import java.io.File;

public class MainViewModel extends AndroidViewModel {

    private final MutableLiveData<String> statusText = new MutableLiveData<>("正在初始化...");
    private final MutableLiveData<ChatAdapter.ChatItem> newMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> inputEnabled = new MutableLiveData<>(false);

    private AgentManager agentManager;
    private ChatAdapter adapter;

    public MainViewModel(@NonNull Application application) {
        super(application);
        adapter = new ChatAdapter();
        initEngine(application);
    }

    private void initEngine(Application app) {
        new Thread(() -> {
            try {
                statusText.postValue("正在加载模型...");

                // 模型路径: 外部存储或 app 私有目录
                File modelDir = new File(app.getExternalFilesDir(null), "models");
                File modelFile = new File(modelDir, "qwen2.5-1.5b-instruct-q4_k_m.gguf");

                if (!modelFile.exists()) {
                    statusText.postValue("模型文件未找到，请下载到: " + modelFile.getAbsolutePath());
                    return;
                }

                ModelConfig config = new ModelConfig(modelFile.getAbsolutePath());
                LlamaEngine engine = new LlamaEngine();
                engine.init(config);

                VehicleState state = new VehicleState();
                FunctionRegistry registry = new FunctionRegistry();
                VehicleService vehicleService = new VehicleService(registry, state);

                agentManager = new AgentManager(engine, registry, vehicleService, state);

                statusText.postValue("就绪");
                inputEnabled.postValue(true);
            } catch (Exception e) {
                statusText.postValue("初始化失败: " + e.getMessage());
            }
        }).start();
    }

    public void sendMessage(String text) {
        if (agentManager == null || !agentManager.isReady()) {
            statusText.postValue("模型未就绪，请稍候...");
            return;
        }

        adapter.addUserMessage(text);
        inputEnabled.setValue(false);
        statusText.setValue("思考中...");

        agentManager.receive(text, response -> {
            adapter.addAssistantMessage(response.text, response.execResults);
            inputEnabled.postValue(true);
            statusText.postValue("就绪");
        });
    }

    public LiveData<String> getStatusText() { return statusText; }
    public LiveData<Boolean> getInputEnabled() { return inputEnabled; }
    public ChatAdapter getAdapter() { return adapter; }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (agentManager != null) {
            agentManager.shutdown();
        }
    }
}
