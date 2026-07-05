# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作提供指引。

## 项目概述

车载语音助手 — 基于本地大模型的智能车控对话助手，运行在 RedMi Note Pro 13 (Android) 上。通过 llama.cpp JNI 推理 Qwen2.5-1.5B Q4_K_M 量化模型，使用 Function Calling 机制解析用户意图并执行车控方法。

## 构建与运行

```bash
# 构建 debug APK
./gradlew assembleDebug

# 安装到已连接设备
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 编译检查（不打包）
./gradlew compileDebugJavaWithJavac
```

构建要求: Android SDK 34, NDK (arm64-v8a), CMake 3.22.1, JDK 17, Gradle 8.11.1.

Gradle 分发源使用腾讯云镜像，Maven 仓库使用阿里云镜像。JDK 路径在 `gradle.properties` 中通过 `org.gradle.java.home` 指定。

## 架构

四层架构，依赖方向严格: `UI → Agent → Engine → Vehicle`

```
┌──────────────────────────────────────────────────┐
│  UI 层 (MVVM + ViewBinding + XML)                │
│  ChatActivity, MainViewModel, ChatAdapter         │
├──────────────────────────────────────────────────┤
│  Agent 层                                         │
│  AgentManager, ContextManager, CommandPipeline     │
├──────────────────────────────────────────────────┤
│  Engine 层                                        │
│  LlamaEngine(JNI), PromptBuilder, OutputParser     │
├──────────────────────────────────────────────────┤
│  Vehicle 层                                       │
│  VehicleService, FunctionRegistry, VehicleState   │
└──────────────────────────────────────────────────┘
```

## 核心数据流（两次推理）

```
用户输入
  → PromptBuilder (ChatML 格式 + Function Schema + 车辆状态)
  → LlamaEngine.infer()         ← 第一次推理：意图提取 → JSON
  → OutputParser.parse(json)    ← JSON 容错 / 闲聊兜底
  → CommandPipeline.execute()   ← 去重 → 参数校验 → 危险操作检查 → 幂等检查 → mock 执行
  → PromptBuilder (注入执行结果)
  → LlamaEngine.infer()         ← 第二次推理：自然语言回复
  → ContextManager.save()       ← 本轮对话存入滑动窗口
```

## 各层详情

### Vehicle 层
- `VehicleState` — 内存中的车辆全状态快照（空调、车窗、座椅、灯光、驾驶模式等 30+ 字段），用于幂等检查和安全校验
- `FunctionRegistry` — 23 个车控方法的注册表，每个方法定义 name/params/min-max/enum，自动生成 JSON Schema 注入 prompt
- `VehicleService` — 执行入口，四阶段: 参数校验(min/max/enum) → 危险操作检查(requireParked) → 幂等检查 → mock 执行(Log.d)
- 所有方法当前为 mock 实现，打 log 输出。后续接入真实车控时替换 `VehicleService` 内部实现即可

### Engine 层
- `LlamaEngine` — JNI 桥接层，Java 侧提供 `init(ModelConfig)`, `infer(String):String`, `release()`, `isLoaded()`，所有方法 synchronized
- `llama_jni.cpp` — 完整接入 llama.cpp (b9871)，CPU-only 静态链接 libllama + libggml + libllama-common，ARM NEON/dotprod/fp16 优化
- `libllama_jni.so` — Debug ~83MB（unstripped），Release ~15-20MB，APK 从 5.6MB → 16MB
- `MockCommandExtractor` — 当模型文件不存在时的关键词匹配兜底，覆盖全部 23 个车控方法
- `PromptBuilder` — 组装 Qwen2.5 ChatML 格式 (`<|im_start|>system/user/assistant<|im_end|>`)，区分第一次推理(意图提取)和第二次推理(结果汇总)
- `OutputParser` — 优先尝试 JSON 数组解析，修复常见格式错误(尾逗号、单引号)，失败则兜底到纯文本(闲聊兜底)

### Agent 层
- `AgentManager` — 对话编排核心，单线程 ExecutorService 保证消息串行处理，pendingInput 队列处理快速连发
- `ContextManager` — 滑动窗口 4096 tokens(预留 1200 给 prompt+输出)，最多 20 轮，从最早消息成对裁剪
- `CommandPipeline` — 批量去重(同 target 后者覆盖前者) + 顺序执行 + 部分失败策略(nonCritical 跳过/critical 中断)

### UI 层
- `MainViewModel` — AndroidViewModel，后台线程初始化引擎，LiveData 驱动状态文本和输入启用状态
- `ChatActivity` — ViewBinding (`ActivityChatBinding`)，IME_ACTION_SEND 键盘发送，AdapterDataObserver 自动滚动
- `ChatAdapter` — 双 ViewType RecyclerView(用户右/助手左)，助手消息可包含动态注入的 ExecutionCard 列表

## 上下文窗口预算 (4096 tokens)

| 占用项 | Token 数 |
|--------|---------|
| System Prompt + Function Schema | ~950 |
| 历史对话 (6-8 轮) | ~600-1000 |
| 当前用户输入 | ~50-100 |
| 模型输出 | ~200-400 |
| 余量 | ~40% |

推理参数: temperature=0.1, top_p=0.9, max_tokens=512, threads=4

## 模型与 llama.cpp

- llama.cpp 作为 git submodule (`llama.cpp/`) 管理，tag `b9871`
- 模型不打包进 APK。首次启动检查 `ExternalFilesDir/models/qwen2.5-1.5b-instruct-q4_k_m.gguf`
- 模型未找到时显示下载界面（HuggingFace 主地址 + ModelScope 备用镜像）
- 模型也未加载时 `AgentManager` 自动使用 `MockCommandExtractor` 关键词匹配兜底
- 真实推理和 mock 之间无感切换：骨架占位检测 → mock 覆盖 → 真实模型加载后自动走 llama.cpp

## 设计约束

- 车控方法注册时标记 `requireParked: true` 的方法在行驶中拒绝执行
- `nonCritical` 指令失败跳过继续，`critical` 指令失败中断后续执行
- 用户快速连发时，消息排队串行处理，丢弃中间未完成的输入
- 推理前检查可用内存（< 200MB 时提示用户）
- org.json 库由 Android SDK 内置提供，无需额外依赖
- llama.cpp 源码: `llama.cpp/` (git submodule)，CMake 配置: `app/src/main/cpp/CMakeLists.txt`
- 国内网络克隆 llama.cpp 时可能需要镜像代理（如 ghfast.top）
- org.json 库由 Android SDK 内置提供，无需额外依赖
