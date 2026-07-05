package com.example.vehicleassistant.model;

public class ModelConfig {
    public final String modelPath;
    public final int contextSize = 2048;
    public final int maxTokens = 64;
    public final float temperature = 0.1f;
    public final float topP = 0.9f;
    public final int threads = 6;

    public ModelConfig(String modelPath) {
        this.modelPath = modelPath;
    }
}
