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

    // 语音交互
    private final MutableLiveData<Boolean> asrListening = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> voiceKitReady = new MutableLiveData<>(false);
    private final MutableLiveData<String> asrResult = new MutableLiveData<>(null);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AgentManager agentManager;
    private ChatAdapter adapter;
    private ModelDownloadManager downloadManager;
    private File modelFile;

    private com.cornex.voicekit.VoiceKitManager voiceKitManager;
    private com.cornex.voicekit.asr.IAsr asr;
    private com.cornex.voicekit.tts.ITts tts;

    public MainViewModel(@NonNull Application application) {
        super(application);
        adapter = new ChatAdapter();
        downloadManager = new ModelDownloadManager();

        File modelDir = new File(application.getExternalFilesDir(null), "models");
        modelFile = new File(modelDir, "qwen2.5-0.5b-instruct-q4_k_m.gguf");

        if (modelFile.exists()) {
            downloadVisible.setValue(false);
            initEngine(application);
        } else {
            downloadVisible.setValue(true);
            downloadStatus.setValue("需下载模型文件（约760MB）");
            statusText.setValue("模型未下载");
        }
    }

    private void initEngine(Application app) {
        new Thread(() -> {
            try {
                statusText.postValue("正在加载模型...");

                if (!modelFile.exists()) {
                    statusText.postValue("模型文件未找到");
                    mainHandler.post(() -> initVoiceKit(app));
                    downloadVisible.postValue(true);
                    return;
                }

                ModelConfig config = new ModelConfig(modelFile.getAbsolutePath());
                LlamaEngine engine = new LlamaEngine();
                engine.init(config);

                VehicleState state = new VehicleState();
                FunctionRegistry registry = new FunctionRegistry();
                VehicleService vehicleService = new VehicleService(registry, state);

                if (!engine.isLoaded()) {
                    modelFile.delete(); // 删除损坏文件，下次走下载路径
                    statusText.postValue("模型文件损坏，请重新下载");
                    mainHandler.post(() -> initVoiceKit(app));
                    downloadVisible.postValue(true);
                    return;
                }

                agentManager = new AgentManager(engine, registry, vehicleService, state);

                // 初始化语音交互
                mainHandler.post(() -> initVoiceKit(app));

                statusText.postValue("就绪");
                inputEnabled.postValue(true);
                downloadVisible.postValue(false);
            } catch (Exception e) {
                statusText.postValue("初始化失败: " + e.getMessage());
            }
        }).start();
    }

    private static final long MIN_MODEL_SIZE = 700_000_000L; // Q3_K_M 约 760MB

    public void startDownload() {
        // 文件已存在且大小合理 → 直接初始化
        if (modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE) {
            downloadVisible.setValue(false);
            initEngine(getApplication());
            return;
        }

        // 小文件残留 → 删除后重新下载
        if (modelFile.exists() && modelFile.length() < MIN_MODEL_SIZE) {
            modelFile.delete();
        }

        downloadActive.setValue(true);
        downloadStatus.setValue("正在下载... 0%");
        statusText.setValue("下载模型中...");

        downloadManager.download(modelFile,
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

                // TTS 自动播报助手回复
                if (tts != null && response.text != null && !response.text.isEmpty()) {
                    tts.stopPlay();
                    tts.startPlay(response.text);
                }
            });
        });
    }

    // ==================== 语音交互 ====================

    private void initVoiceKit(Application app) {
        try {
            voiceKitManager = new com.cornex.voicekit.VoiceKitManager();
            voiceKitManager.init(app.getApplicationContext(), new com.cornex.voicekit.api.IInitResult() {
                @Override
                public void onSuccess() {
                    asr = voiceKitManager.asr();
                    tts = voiceKitManager.tts();
                    asr.initAsr(app.getApplicationContext());
                    tts.initTts();
                    asr.setOnAsrResultListener(createAsrListener());
                    voiceKitReady.postValue(true);
                }

                @Override
                public void onFail(String errorMsg) {
                    voiceKitReady.postValue(false);
                }
            });
        } catch (Exception e) {
            voiceKitReady.postValue(false);
        }
    }

    private com.cornex.voicekit.asr.IAsrResultListener createAsrListener() {
        return new com.cornex.voicekit.asr.IAsrResultListener() {
            @Override
            public void onStart() {
                asrListening.postValue(true);
            }

            @Override
            public void onResult(int status, String result) {
                if (status == com.cornex.voicekit.constants.VoiceKitDef.FINAL_PART) {
                    mainHandler.post(() -> onAsrResult(result));
                }
            }

            @Override
            public void onFinish() {
                asrListening.postValue(false);
            }

            @Override
            public void onFail(String errorMsg) {
                asrListening.postValue(false);
            }
        };
    }

    public void toggleListening() {
        // 场景4: 模型推理中阻止（inputEnabled=false 表示模型正在处理中）
        if (!Boolean.TRUE.equals(inputEnabled.getValue())) {
            android.widget.Toast.makeText(getApplication(), "模型正在处理中，请稍候",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (asr == null) return;

        Boolean listening = asrListening.getValue();
        if (listening != null && listening) {
            // 场景2: 手动结束录音
            asr.stopRecord();
        } else {
            // 检查权限, 开始录音
            asr.startRecord();
        }
    }

    public void onAsrResult(String text) {
        if (text != null && !text.trim().isEmpty()) {
            asrResult.postValue(text.trim());
        } else {
            // 场景3: 识别结果为空
            android.widget.Toast.makeText(getApplication(), "未识别到语音，请重试",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public void onAsrResultConsumed() {
        asrResult.postValue(null);
    }

    /** Activity onDestroy 时调用 — 停止 TTS 播放 */
    public void stopTts() {
        if (tts != null) {
            tts.stopPlay();
        }
    }

    /** Activity onPause 时调用 — 场景1: 录音中切后台自动停止 */
    public void stopListeningOnPause() {
        if (asr != null && Boolean.TRUE.equals(asrListening.getValue())) {
            asr.stopRecord();
            asrListening.postValue(false);
        }
    }

    // ==================== Getters ====================

    public LiveData<String> getStatusText() { return statusText; }
    public LiveData<Boolean> getInputEnabled() { return inputEnabled; }
    public LiveData<Boolean> getDownloadVisible() { return downloadVisible; }
    public LiveData<Boolean> getDownloadActive() { return downloadActive; }
    public LiveData<Integer> getDownloadProgress() { return downloadProgress; }
    public LiveData<String> getDownloadStatus() { return downloadStatus; }
    public LiveData<Boolean> getAsrListening() { return asrListening; }
    public LiveData<Boolean> getVoiceKitReady() { return voiceKitReady; }
    public LiveData<String> getAsrResult() { return asrResult; }
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
        if (voiceKitManager != null) {
            voiceKitManager.dispose();
        }
    }
}
