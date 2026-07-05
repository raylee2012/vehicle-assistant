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

    // ---- 模型定义 ----
    private static final String MODEL_05B_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf";
    private static final String[] MODEL_05B_URLS = {
        "https://modelscope.cn/models/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/master/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    };
    private static final long MODEL_05B_MIN_SIZE = 350_000_000L;
    private static final String MODEL_05B_SIZE_TEXT = "约400MB";

    private static final String MODEL_15B_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf";
    private static final String[] MODEL_15B_URLS = {
        "https://modelscope.cn/models/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    };
    private static final long MODEL_15B_MIN_SIZE = 900_000_000L;
    private static final String MODEL_15B_SIZE_TEXT = "约1GB";

    // ---- LiveData ----
    private final MutableLiveData<String> statusText = new MutableLiveData<>("正在初始化...");
    private final MutableLiveData<Boolean> inputEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> downloadVisible = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> downloadActive = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> downloadProgress = new MutableLiveData<>(0);
    private final MutableLiveData<String> downloadStatus = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> asrListening = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> voiceKitReady = new MutableLiveData<>(false);
    private final MutableLiveData<String> asrResult = new MutableLiveData<>(null);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AgentManager agentManager;
    private ChatAdapter adapter;
    private ModelDownloadManager downloadManager;
    private File modelDir;
    private File modelFile;
    private String currentModelKey;

    private com.cornex.voicekit.VoiceKitManager voiceKitManager;
    private com.cornex.voicekit.asr.IAsr asr;
    private com.cornex.voicekit.tts.ITts tts;

    public MainViewModel(@NonNull Application application) {
        super(application);
        adapter = new ChatAdapter();
        downloadManager = new ModelDownloadManager();
        modelDir = new File(application.getExternalFilesDir(null), "models");
        initFromSettings(application);
    }

    /** 根据 SharedPreferences 选择模型并初始化 */
    private void initFromSettings(Application app) {
        String modelKey = SettingsActivity.getModel(app);
        currentModelKey = modelKey;

        String modelName;
        long minSize;
        String sizeText;
        if ("0.5b".equals(modelKey)) {
            modelName = MODEL_05B_NAME;
            minSize = MODEL_05B_MIN_SIZE;
            sizeText = MODEL_05B_SIZE_TEXT;
        } else {
            modelName = MODEL_15B_NAME;
            minSize = MODEL_15B_MIN_SIZE;
            sizeText = MODEL_15B_SIZE_TEXT;
        }

        modelFile = new File(modelDir, modelName);

        if (modelFile.exists() && modelFile.length() > minSize) {
            downloadVisible.setValue(false);
            initEngine(app);
        } else {
            downloadVisible.setValue(true);
            downloadStatus.setValue("需下载模型文件（" + sizeText + "）");
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
                    modelFile.delete();
                    statusText.postValue("模型文件损坏，请重新下载");
                    mainHandler.post(() -> initVoiceKit(app));
                    downloadVisible.postValue(true);
                    return;
                }

                if (agentManager != null) {
                    agentManager.shutdown();
                }
                boolean is15B = "1.5b".equals(currentModelKey);
                agentManager = new AgentManager(engine, registry, vehicleService, state, is15B);

                mainHandler.post(() -> initVoiceKit(app));

                statusText.postValue("就绪");
                inputEnabled.postValue(true);
                downloadVisible.postValue(false);
            } catch (Exception e) {
                statusText.postValue("初始化失败: " + e.getMessage());
            }
        }).start();
    }

    /** 检查设置中的模型是否改变，改变则重新初始化 */
    public void checkModelChange() {
        Application app = getApplication();
        String newKey = SettingsActivity.getModel(app);
        if (!newKey.equals(currentModelKey)) {
            // 模型切换 → 关闭旧引擎，重新选择模型文件
            if (agentManager != null) {
                agentManager.shutdown();
                agentManager = null;
            }
            inputEnabled.postValue(false);
            initFromSettings(app);
        }
    }

    public void startDownload() {
        String modelKey = SettingsActivity.getModel(getApplication());
        String[] urls = "0.5b".equals(modelKey) ? MODEL_05B_URLS : MODEL_15B_URLS;
        long minSize = "0.5b".equals(modelKey) ? MODEL_05B_MIN_SIZE : MODEL_15B_MIN_SIZE;

        if (modelFile.exists() && modelFile.length() > minSize) {
            downloadVisible.setValue(false);
            initEngine(getApplication());
            return;
        }

        if (modelFile.exists() && modelFile.length() < minSize) {
            modelFile.delete();
        }

        downloadActive.setValue(true);
        downloadStatus.setValue("正在下载... 0%");
        statusText.setValue("下载模型中...");

        downloadManager.download(modelFile, urls,
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
        final int placeholderPos = adapter.addThinkingPlaceholder();

        boolean mockFirst = SettingsActivity.isMockFirst(getApplication());
        agentManager.receive(text, response -> {
            mainHandler.post(() -> {
                inputEnabled.setValue(true);
                statusText.setValue("就绪");

                if (response.videoSearchKeyword != null) {
                    adapter.finishThinking(placeholderPos, response.text, null);
                    adapter.addVideoSearchCard(response.videoSearchKeyword);
                } else {
                    adapter.finishThinking(placeholderPos, response.text, response.execResults);
                }

                if (tts != null && response.text != null && !response.text.isEmpty()) {
                    tts.stopPlay();
                    tts.startPlay(response.text);
                }
            });
        }, mockFirst);
    }

    // ==================== 语音交互 ====================

    private void initVoiceKit(Application app) {
        android.util.Log.d("MainViewModel", "initVoiceKit starting...");
        try {
            voiceKitManager = new com.cornex.voicekit.VoiceKitManager();
            voiceKitManager.init(app.getApplicationContext(), new com.cornex.voicekit.api.IInitResult() {
                @Override
                public void onSuccess() {
                    android.util.Log.d("MainViewModel", "VoiceKit init success");
                    asr = voiceKitManager.asr();
                    tts = voiceKitManager.tts();
                    asr.initAsr(app.getApplicationContext());
                    tts.initTts();
                    asr.setOnAsrResultListener(createAsrListener());
                    voiceKitReady.postValue(true);
                }

                @Override
                public void onFail(String errorMsg) {
                    android.util.Log.e("MainViewModel", "VoiceKit init failed: " + errorMsg);
                    voiceKitReady.postValue(false);
                }
            });
        } catch (Exception e) {
            android.util.Log.e("MainViewModel", "VoiceKit init exception: " + e.getMessage());
            voiceKitReady.postValue(false);
        }
    }

    private com.cornex.voicekit.asr.IAsrResultListener createAsrListener() {
        return new com.cornex.voicekit.asr.IAsrResultListener() {
            @Override
            public void onStart() {
                android.util.Log.d("MainViewModel", "ASR onStart");
                asrListening.postValue(true);
            }

            @Override
            public void onResult(int status, String result) {
                android.util.Log.d("MainViewModel", "ASR onResult status=" + status + " result=" + result);
                if (result != null && !result.isEmpty()) {
                    mainHandler.post(() -> onAsrResult(result));
                }
            }

            @Override
            public void onFinish() {
                android.util.Log.d("MainViewModel", "ASR onFinish");
                asrListening.postValue(false);
            }

            @Override
            public void onFail(String errorMsg) {
                android.util.Log.e("MainViewModel", "ASR onFail: " + errorMsg);
                asrListening.postValue(false);
            }
        };
    }

    public void toggleListening() {
        if (!Boolean.TRUE.equals(inputEnabled.getValue())) {
            android.util.Log.d("MainViewModel", "toggleListening blocked: inputEnabled=false");
            android.widget.Toast.makeText(getApplication(), "模型正在处理中，请稍候",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (asr == null) {
            android.util.Log.e("MainViewModel", "toggleListening: asr is null");
            return;
        }

        Boolean listening = asrListening.getValue();
        if (listening != null && listening) {
            android.util.Log.d("MainViewModel", "toggleListening: stopping record");
            asr.stopRecord();
        } else {
            android.util.Log.d("MainViewModel", "toggleListening: starting record");
            asr.startRecord();
        }
    }

    public void onAsrResult(String text) {
        android.util.Log.d("MainViewModel", "onAsrResult: text='" + text + "'");
        if (text != null && !text.trim().isEmpty()) {
            asrResult.postValue(text.trim());
        } else {
            android.widget.Toast.makeText(getApplication(), "未识别到语音，请重试",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public void onAsrResultConsumed() {
        asrResult.postValue(null);
    }

    public void stopTts() {
        if (tts != null) {
            tts.stopPlay();
        }
    }

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
