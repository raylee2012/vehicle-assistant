# 端侧模型车机助手

基于 llama.cpp + Qwen2.5 的 Android 端侧智能语音车控助手。无需联网，本地推理，支持 Function Calling 车控指令、语音交互、视频搜索跳转抖音。

## 功能

- **本地 LLM 推理** — 基于 llama.cpp JNI，CPU-only 运行 Qwen2.5 0.5B/1.5B Q4_K_M 量化模型
- **车控 Function Calling** — 支持空调、车窗、天窗、大灯、雨刮、驾驶模式等 35 个车控指令
- **语音交互** — ASR 语音识别 + TTS 语音播报
- **视频搜索** — 关键词命中后跳转抖音搜索
- **双模式意图提取** — Mock 关键词匹配优先 or 模型 Function Calling 优先，可在设置中切换

## 架构

```
UI (MVVM + ViewBinding) → Agent (对话编排) → Engine (llama.cpp JNI) → Vehicle (车控执行)
```

## 构建

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

构建要求: Android SDK 34, NDK 25+, CMake 3.22.1, JDK 17, Gradle 8.11.1

## 运行

1. 安装 APK 后首次启动需下载模型（0.5B ≈ 400MB / 1.5B ≈ 1GB）
2. 模型下载完成后自动加载，状态栏显示"就绪"即可使用
3. 支持文字输入和语音输入（需授权录音权限）
4. 设置中可切换模型和意图提取模式

## 技术栈

- **语言**: Java, C++ (JNI)
- **推理引擎**: [llama.cpp](https://github.com/ggml-org/llama.cpp) (tag b9871)
- **模型**: Qwen2.5-0.5B / Qwen2.5-1.5B Q4_K_M (GGUF)
- **UI**: Material3, ViewBinding, RecyclerView
- **语音**: VoiceKit (ASR + TTS)
