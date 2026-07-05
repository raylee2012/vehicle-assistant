package com.example.vehicleassistant.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vehicleassistant.agent.AgentManager;
import com.example.vehicleassistant.engine.LlamaEngine;
import com.example.vehicleassistant.model.ModelConfig;
import com.example.vehicleassistant.model.ModelDownloadManager;
import com.example.vehicleassistant.vehicle.FunctionRegistry;
import com.example.vehicleassistant.vehicle.VehicleService;
import com.example.vehicleassistant.vehicle.VehicleState;

import java.io.File;

public class MainViewModel extends AndroidViewModel {

    private final MutableLiveData<String> statusText = new MutableLiveData<>("正在初始化...");
    private final MutableLiveData<Boolean> inputEnabled = new MutableLiveData<>(false);

    // 下载相关状态
    private final MutableLiveData<Boolean> downloadVisible = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> downloadActive = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> downloadProgress = new MutableLiveData<>(0);
    private final MutableLiveData<String> downloadStatus = new MutableLiveData<>("");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AgentManager agentManager;
    private ChatAdapter adapter;
    private ModelDownloadManager downloadManager;
    private File modelFile;

    public MainViewModel(@NonNull Application application) {
        super(application);
        adapter = new ChatAdapter();
        downloadManager = new ModelDownloadManager();

        File modelDir = new File(application.getExternalFilesDir(null), "models");
        modelFile = new File(modelDir, "qwen2.5-1.5b-instruct-q4_k_m.gguf");

        if (modelFile.exists()) {
            downloadVisible.setValue(false);
            initEngine(application);
        } else {
            downloadVisible.setValue(true);
            downloadStatus.setValue("需下载模型文件（约1.5GB）");
            statusText.setValue("模型未下载");
        }
    }

    private void initEngine(Application app) {
        new Thread(() -> {
            try {
                statusText.postValue("正在加载模型...");

                if (!modelFile.exists()) {
                    statusText.postValue("模型文件未找到");
                    downloadVisible.postValue(true);
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
                downloadVisible.postValue(false);
            } catch (Exception e) {
                statusText.postValue("初始化失败: " + e.getMessage());
            }
        }).start();
    }

    public void startDownload() {
        if (modelFile.exists()) {
            downloadVisible.setValue(false);
            initEngine(getApplication());
            return;
        }

        downloadActive.setValue(true);
        downloadStatus.setValue("正在下载... 0%");
        statusText.setValue("下载模型中...");

        downloadManager.download(ModelDownloadManager.DEFAULT_MODEL_URL, modelFile,
            new ModelDownloadManager.DownloadCallback() {
                @Override
                public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                    downloadProgress.setValue(percent);
                    downloadStatus.setValue("正在下载... " + percent + "%");
                }

                @Override
                public void onComplete(File file) {
                    downloadActive.setValue(false);
                    downloadVisible.setValue(false);
                    statusText.setValue("下载完成，正在加载...");
                    initEngine(getApplication());
                }

                @Override
                public void onError(String message) {
                    downloadActive.setValue(false);
                    downloadStatus.setValue("下载失败: " + message);
                    statusText.setValue("下载失败");
                }
            });
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
            mainHandler.post(() -> {
                adapter.addAssistantMessage(response.text, response.execResults);
                inputEnabled.setValue(true);
                statusText.setValue("就绪");
            });
        });
    }

    public LiveData<String> getStatusText() { return statusText; }
    public LiveData<Boolean> getInputEnabled() { return inputEnabled; }
    public LiveData<Boolean> getDownloadVisible() { return downloadVisible; }
    public LiveData<Boolean> getDownloadActive() { return downloadActive; }
    public LiveData<Integer> getDownloadProgress() { return downloadProgress; }
    public LiveData<String> getDownloadStatus() { return downloadStatus; }
    public ChatAdapter getAdapter() { return adapter; }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (agentManager != null) {
            agentManager.shutdown();
        }
        if (downloadManager != null) {
            downloadManager.cancel();
        }
    }
}
