package com.example.vehicleassistant.engine;

import com.example.vehicleassistant.model.ModelConfig;

/**
 * llama.cpp JNI 桥接。负责模型加载、推理、资源释放。
 * 推理在主线程外调用（由 Agent 层管理线程）。
 */
public class LlamaEngine {

    static {
        System.loadLibrary("llama_jni");
    }

    private long nativePtr = 0;
    private volatile boolean loaded = false;

    // --- Native methods ---
    private native long nativeInit(String modelPath, int contextSize, int maxTokens,
                                   float temperature, float topP, int threads);
    private native String nativeInfer(long ptr, String prompt);
    private native void nativeRelease(long ptr);

    public synchronized void init(ModelConfig config) {
        if (loaded) return;
        nativePtr = nativeInit(config.modelPath, config.contextSize,
            config.maxTokens, config.temperature, config.topP, config.threads);
        loaded = true;
    }

    public synchronized String infer(String prompt) {
        if (!loaded) return "[模型未加载]";
        return nativeInfer(nativePtr, prompt);
    }

    public synchronized void release() {
        if (!loaded) return;
        nativeRelease(nativePtr);
        nativePtr = 0;
        loaded = false;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
