# 语音交互（ASR + TTS）— 设计规格说明书

## 概述

为车载语音助手增加语音输入（ASR）和语音输出（TTS）能力。用户可以通过麦克风说出指令，识别文本填入输入框，确认后发送走现有推理流程。助手回复自动 TTS 朗读，让驾驶场景无需看屏幕。

语音 SDK 基于科大讯飞 SparkChain，通过 voicekit 模块封装，提供 ASR 和 TTS 能力。

## 核心流程

```
用户点击麦克风 → 开始录音识别（按钮变灰色波形动画，输入框禁用）
  ├─ 用户再点 → 手动结束，送识别
  └─ ASR 检测到语音结束 → 自动停止
  → 识别文本填入输入框 → 按钮恢复空闲态
  → 用户确认/修改 → 点击发送 → 走现有推理流程
  → 助手回复 → stopPlay() 停止旧TTS → startPlay() 播报新回复
```

## 交互状态

两状态切换：

| 状态 | 麦克风按钮 | Lottie 波形 | 输入框 + 发送按钮 |
|------|-----------|-------------|-----------------|
| 空闲 | 显示（默认色） | 隐藏 | 可用 |
| 录音识别中 | 隐藏 | 在按钮位置播放（灰色调波形） | 禁用 |

Lottie 波形动画在录音时覆盖麦克风按钮原位置，灰色调，表示正在录音识别中。

## 架构

```
MainViewModel 新增:
  ├─ VoiceKitManager     ← ASR + TTS 生命周期管理
  ├─ asrState LiveData   ← idle / listening（驱动 UI 状态）
  └─ onAssistantResponse → 自动 stopPlay + startPlay

ChatActivity 新增:
  ├─ LottieAnimationView ← 录音波形动画（灰色）
  ├─ 麦克风按钮          ← 点击切换录音状态
  └─ 首次录音时申请权限
```

不新增独立的 VoiceController 层。MainViewModel 直接持有 VoiceKitManager，逻辑够简单不值得多一层抽象。

## 边界场景

| # | 场景 | 处理 |
|---|------|------|
| 1 | 录音中切后台 | 自动 stopRecord()，丢弃已录内容，恢复空闲态 |
| 2 | 录音中再点麦克风 | 手动结束录音，触发识别 |
| 3 | 识别结果为空 | Toast "未识别到语音，请重试"，恢复空闲态 |
| 4 | 模型推理中点击麦克风 | 阻止，Toast "模型正在处理中，请稍候" |
| 5 | 已有 TTS 播报中发新消息 | stopPlay() → startPlay(新回复文本) |
| 6 | VoiceKit 初始化失败 | 降级：隐藏麦克风按钮，无 TTS 播报；文本输入 + 模型推理照常可用 |
| 7 | TTS 合成/播放失败 | 静默失败，不弹错误提示，不中断对话流程 |

## 权限处理

ASR 需要 `RECORD_AUDIO` 权限。首次点击麦克风时申请，流程：

- 已授权 → 直接开始录音
- 弹系统授权弹窗 → 用户允许 → 开始录音
- 用户拒绝 → Toast 引导到设置页面

TTS 不需要额外运行时权限（通过扬声器输出）。

## 新增依赖

**Lottie** — 用于录音波形动画。在 `app/build.gradle` 中添加：

```groovy
implementation 'com.airbnb.android:lottie:6.1.0'
```

## VoiceKit 摘要

已存在于项目中的语音能力封装模块，直接使用无需修改：

| 组件 | 接口 | 用途 |
|------|------|------|
| VoiceKitManager | init(), asr(), tts(), dispose() | 入口，管理生命周期 |
| IAsr | startRecord(), stopRecord(), setOnAsrResultListener() | 录音识别 |
| ITts | startPlay(text), stopPlay() | 文本合成播报 |
| IAsrResultListener | onStart(), onResult(status, text), onFinish(), onFail() | ASR 回调 |
| IInitResult | onSuccess(), onFail() | 初始化回调 |

VoiceKitManager 使用 `@Inject constructor()`，无需 Hilt 依赖可直接 `new VoiceKitManager()`。

## 不做的

- 静音按钮（用户明确不需要）
- 语音唤醒词 / 免提始终监听（保持简单，点击触发）
- TTS 文本过滤/截断（当前模型回复短，无需处理；后续出问题再加）
- 录音音量随声音波动（Lottie 是固定动画，非实时波形）
