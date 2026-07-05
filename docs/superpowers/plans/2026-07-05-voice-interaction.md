# 语音交互（ASR + TTS）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为车控助手增加语音输入（点击麦克风→ASR→填入输入框）和语音输出（助手回复自动TTS朗读）

**Architecture:** MainViewModel 直接持有 VoiceKitManager 管理 ASR/TTS 生命周期，ChatActivity 通过 LiveData 驱动 UI 状态切换（空闲↔录音识别中）。不新增独立 VoiceController 层。

**Tech Stack:** Lottie 6.1.0（波形动画），科大讯飞 SparkChain（voicekit 封装），ViewBinding + LiveData

## Global Constraints

- VoiceKit 初始化失败时降级为纯文本模式（隐藏麦克风，无 TTS），不阻断 App 使用
- 录音中切后台→自动停止录音并丢弃内容
- 录音中再点麦克风→手动结束录音
- 识别结果为空→Toast 提示
- 模型推理中点击麦克风→阻止并 Toast
- TTS 播报中发新消息→stopPlay 后 startPlay 新回复
- TTS 失败静默处理，不弹错误
- ASR 权限拒绝时 Toast 引导到设置
- 麦克风按钮和 Lottie 波形互斥显示（空闲态显示按钮，录音态显示波形）

---

### Task 1: 添加 Lottie 依赖和波形动画资源

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/main/res/raw/lottie_waveform.json`

**Interfaces:**
- Produces: LottieAnimationView 可加载 `R.raw.lottie_waveform`，灰色调 3 竖条波形动画

- [ ] **Step 1: 在 app/build.gradle 添加 Lottie 依赖**

```groovy
dependencies {
    // ... existing deps ...
    implementation 'com.airbnb.android:lottie:6.1.0'
}
```

- [ ] **Step 2: 创建 Lottie 波形动画 JSON**

创建文件 `app/src/main/res/raw/lottie_waveform.json`：

```json
{"v":"5.9.0","fr":30,"ip":0,"op":60,"w":120,"h":80,"nm":"Waveform","ddd":0,"assets":[],"layers":[{"ddd":0,"ind":1,"ty":4,"nm":"Bar 1","sr":1,"ks":{"o":{"a":1,"k":[{"t":0,"s":[100],"h":1},{"t":15,"s":[40],"h":1},{"t":30,"s":[100],"h":1}],"ix":11},"r":{"a":0,"k":0,"ix":10},"p":{"a":0,"k":[30,40,0],"ix":2},"a":{"a":0,"k":[0,0,0],"ix":1},"s":{"a":0,"k":[100,100,100],"ix":6}},"ao":0,"shapes":[{"ty":"rc","d":1,"s":{"a":1,"k":[{"t":0,"s":[12,40],"h":1},{"t":15,"s":[12,10],"h":1},{"t":30,"s":[12,40],"h":1}],"ix":2},"p":{"a":0,"k":[0,0],"ix":3},"r":{"a":0,"k":6,"ix":4},"nm":"Bar","mn":"ADBE Vector Shape - Rect","hd":false}],"fl":{"a":0,"k":[0.6,0.6,0.6,1],"ix":4},"st":{"a":0,"k":[0,0,0,0],"ix":5}},{"ddd":0,"ind":2,"ty":4,"nm":"Bar 2","sr":1,"ks":{"o":{"a":1,"k":[{"t":5,"s":[100],"h":1},{"t":20,"s":[40],"h":1},{"t":35,"s":[100],"h":1}],"ix":11},"r":{"a":0,"k":0,"ix":10},"p":{"a":0,"k":[60,40,0],"ix":2},"a":{"a":0,"k":[0,0,0],"ix":1},"s":{"a":0,"k":[100,100,100],"ix":6}},"ao":0,"shapes":[{"ty":"rc","d":1,"s":{"a":1,"k":[{"t":5,"s":[12,60],"h":1},{"t":20,"s":[12,15],"h":1},{"t":35,"s":[12,60],"h":1}],"ix":2},"p":{"a":0,"k":[0,0],"ix":3},"r":{"a":0,"k":6,"ix":4},"nm":"Bar","mn":"ADBE Vector Shape - Rect","hd":false}],"fl":{"a":0,"k":[0.6,0.6,0.6,1],"ix":4},"st":{"a":0,"k":[0,0,0,0],"ix":5}},{"ddd":0,"ind":3,"ty":4,"nm":"Bar 3","sr":1,"ks":{"o":{"a":1,"k":[{"t":10,"s":[100],"h":1},{"t":25,"s":[40],"h":1},{"t":40,"s":[100],"h":1}],"ix":11},"r":{"a":0,"k":0,"ix":10},"p":{"a":0,"k":[90,40,0],"ix":2},"a":{"a":0,"k":[0,0,0],"ix":1},"s":{"a":0,"k":[100,100,100],"ix":6}},"ao":0,"shapes":[{"ty":"rc","d":1,"s":{"a":1,"k":[{"t":10,"s":[12,50],"h":1},{"t":25,"s":[12,12],"h":1},{"t":40,"s":[12,50],"h":1}],"ix":2},"p":{"a":0,"k":[0,0],"ix":3},"r":{"a":0,"k":6,"ix":4},"nm":"Bar","mn":"ADBE Vector Shape - Rect","hd":false}],"fl":{"a":0,"k":[0.6,0.6,0.6,1],"ix":4},"st":{"a":0,"k":[0,0,0,0],"ix":5}}]}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL（Lottie 依赖解析通过）

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle app/src/main/res/raw/lottie_waveform.json
git commit -m "feat: 添加 Lottie 依赖和波形动画资源"
```

---

### Task 2: 更新 activity_chat.xml — 添加麦克风按钮和 Lottie 动画

**Files:**
- Modify: `app/src/main/res/layout/activity_chat.xml`

**Interfaces:**
- Consumes: `R.raw.lottie_waveform`（Task 1 创建）
- Produces: `btnMic` (Button), `lottieWaveform` (LottieAnimationView) — ChatActivity 绑定

- [ ] **Step 1: 在输入区域添加 FrameLayout 容器（麦克风 + Lottie 互斥叠加）**

替换输入区域底部 LinearLayout 中的内容：

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="12dp"
    android:background="#16213E"
    android:gravity="center_vertical">

    <EditText
        android:id="@+id/et_input"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:hint="输入指令..."
        android:textColor="#FFF"
        android:textColorHint="#666"
        android:background="@android:color/transparent"
        android:padding="12dp"
        android:maxLines="3" />

    <FrameLayout
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_marginEnd="8dp">

        <Button
            android:id="@+id/btn_mic"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:text="🎤"
            android:textSize="20sp"
            android:backgroundTint="#0F3460"
            android:padding="0dp"
            android:visibility="visible" />

        <com.airbnb.lottie.LottieAnimationView
            android:id="@+id/lottie_waveform"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:visibility="gone"
            android:scaleType="centerCrop" />

    </FrameLayout>

    <Button
        android:id="@+id/btn_send"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="发送"
        android:textColor="#FFF"
        android:backgroundTint="#0F3460" />

</LinearLayout>
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_chat.xml
git commit -m "feat: 聊天布局添加麦克风按钮和 Lottie 波形容器"
```

---

### Task 3: 更新 MainViewModel.java — 集成 VoiceKit，管理 ASR/TTS 状态

**Files:**
- Modify: `app/src/main/java/com/example/vehicleassistant/ui/MainViewModel.java`

**Interfaces:**
- Consumes: `VoiceKitManager` (voicekit 模块), `IAsr`, `ITts`, `IAsrResultListener`, `IInitResult`
- Produces: `getAsrListening(): LiveData<Boolean>`, `getVoiceKitReady(): LiveData<Boolean>`, `getAsrResult(): LiveData<String>`, `toggleListening()`, `onAsrResultConsumed()`

- [ ] **Step 1: 添加新字段和 LiveData**

在 MainViewModel 类体中添加新字段：

```java
// 语音交互
private final MutableLiveData<Boolean> asrListening = new MutableLiveData<>(false);
private final MutableLiveData<Boolean> voiceKitReady = new MutableLiveData<>(false);
private final MutableLiveData<String> asrResult = new MutableLiveData<>(null);

private com.cornex.voicekit.VoiceKitManager voiceKitManager;
private com.cornex.voicekit.asr.IAsr asr;
private com.cornex.voicekit.tts.ITts tts;
private String pendingTtsText; // 助手回复文本，等待 TTS 播报
```

- [ ] **Step 2: 添加 VoiceKit 初始化方法**

```java
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
```

- [ ] **Step 3: 创建 ASR 结果监听器**

```java
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
```

- [ ] **Step 4: 添加录音切换方法**

```java
public void toggleListening() {
    // 场景4: 模型推理中阻止
    if (agentManager != null && !agentManager.isReady()) {
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
```

- [ ] **Step 5: 在 sendMessage 回调和 VoiceKit 初始化中添加 TTS 自动播报**

在 `sendMessage` 方法中，修改 agentManager.receive 的回调：

```java
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
```

在构造函数末尾添加 VoiceKit 初始化调用：

```java
// 在构造函数末尾（agentManager 创建之后）
initVoiceKit(application);
```

注意：VoiceKit 初始化需要在引擎加载完成后调用。将 `initVoiceKit(application)` 移动到 `initEngine` 方法中 `agentManager` 创建之后：

```java
agentManager = new AgentManager(engine, registry, vehicleService, state);

// 初始化语音交互
initVoiceKit(app);

statusText.postValue("就绪");
```

如果引擎加载失败（模型文件不存在等），仍然初始化 VoiceKit 以保证降级模式工作：

```java
// initEngine 中，模型文件不存在或加载失败的分支也调用
// 在 downloadVisible.postValue(true) 之前调用 initVoiceKit(app);
```

- [ ] **Step 6: 添加 onCleared 中的 VoiceKit 释放**

```java
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
```

- [ ] **Step 7: 添加 getter 方法**

```java
public LiveData<Boolean> getAsrListening() { return asrListening; }
public LiveData<Boolean> getVoiceKitReady() { return voiceKitReady; }
public LiveData<String> getAsrResult() { return asrResult; }

/** Activity onPause 时调用 — 场景1: 录音中切后台自动停止 */
public void stopListeningOnPause() {
    if (asr != null && Boolean.TRUE.equals(asrListening.getValue())) {
        asr.stopRecord();
        asrListening.postValue(false);
    }
}
```

- [ ] **Step 8: 编译验证**

Run: `./gradlew compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/ui/MainViewModel.java
git commit -m "feat: MainViewModel 集成 VoiceKit，管理 ASR/TTS 状态和边界场景"
```

---

### Task 4: 更新 ChatActivity.java — 绑定麦克风按钮、权限、UI 切换

**Files:**
- Modify: `app/src/main/java/com/example/vehicleassistant/ui/ChatActivity.java`

**Interfaces:**
- Consumes: `MainViewModel.getAsrListening()`, `getVoiceKitReady()`, `getAsrResult()`, `toggleListening()`, `onAsrResultConsumed()`, `stopListeningOnPause()`
- Produces: 用户可点击麦克风开始/结束录音，Lottie 动画状态由 asrListening 驱动

**Note:** ChatActivity.java 需要在 `onCreate` 中绑定新的 UI 控件并在 `onPause` 中处理场景1。

- [ ] **Step 1: 添加新成员变量**

```java
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;

// 新增成员变量
private Button btnMic;
private LottieAnimationView lottieWaveform;
```

- [ ] **Step 2: 在 onCreate 中绑定新控件并初始化**

在 `onCreate` 中的 `binding = ActivityChatBinding.inflate(...)` 之后添加：

```java
btnMic = binding.btnMic;
lottieWaveform = binding.lottieWaveform;

// 初始化 Lottie 动画
lottieWaveform.setAnimation(R.raw.lottie_waveform);
lottieWaveform.setRepeatCount(LottieDrawable.INFINITE);
lottieWaveform.setRepeatMode(LottieDrawable.RESTART);
```

- [ ] **Step 3: 绑定麦克风按钮点击事件**

在 `onCreate` 中添加：

```java
// 麦克风按钮 — 场景2: 录音中再点 = 手动结束
btnMic.setOnClickListener(v -> viewModel.toggleListening());
```

- [ ] **Step 4: 观察 ASR 录音状态，驱动 UI 切换**

在 `onCreate` 中添加 observer，使用 helper 方法统一管理输入启用状态：

```java
// 辅助方法：输入框和发送按钮只有在"模型就绪 + 不在录音中"才可用
private void updateInputState() {
    Boolean inputOk = viewModel.getInputEnabled().getValue();
    Boolean asrOn = viewModel.getAsrListening().getValue();
    boolean enabled = inputOk != null && inputOk && (asrOn == null || !asrOn);
    etInput.setEnabled(enabled);
    btnSend.setEnabled(enabled);
}

// ASR 录音状态 → 驱动 麦克风/Lottie 互斥显示 + 输入禁用
viewModel.getAsrListening().observe(this, listening -> {
    if (listening != null && listening) {
        btnMic.setVisibility(View.GONE);
        lottieWaveform.setVisibility(View.VISIBLE);
        lottieWaveform.playAnimation();
    } else {
        lottieWaveform.cancelAnimation();
        lottieWaveform.setVisibility(View.GONE);
        btnMic.setVisibility(View.VISIBLE);
    }
    updateInputState();
});
```

- [ ] **Step 5: 观察 VoiceKit 初始化状态，隐藏/显示麦克风**

```java
// 场景6: VoiceKit 初始化失败 → 隐藏麦克风
viewModel.getVoiceKitReady().observe(this, ready -> {
    btnMic.setVisibility(ready != null && ready ? View.VISIBLE : View.GONE);
});
```

- [ ] **Step 6: 观察 ASR 识别结果，填入输入框**

```java
// ASR 识别结果 → 填入输入框
viewModel.getAsrResult().observe(this, result -> {
    if (result != null && !result.isEmpty()) {
        etInput.setText(result);
        etInput.setSelection(result.length());
        viewModel.onAsrResultConsumed();
    }
});
```

- [ ] **Step 7: 修改 inputEnabled observer，使用 helper 统一管理**

修改原有的 inputEnabled observer，加入麦克风可见性逻辑并改用 `updateInputState()`：

```java
// 输入启用状态（原 observer 替换为以下）
viewModel.getInputEnabled().observe(this, enabled -> {
    Boolean vkReady = viewModel.getVoiceKitReady().getValue();
    btnMic.setVisibility(enabled != null && enabled && vkReady != null && vkReady ? View.VISIBLE : View.GONE);
    updateInputState();
});
```

移除步骤 5 中的 voiceKitReady 单独 observer。

- [ ] **Step 8: 添加 onPause 处理**

```java
@Override
protected void onPause() {
    super.onPause();
    // 场景1: 录音中切后台 → 自动停止
    viewModel.stopListeningOnPause();
}
```

- [ ] **Step 9: 编译验证**

Run: `./gradlew compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/vehicleassistant/ui/ChatActivity.java
git commit -m "feat: ChatActivity 绑定语音交互 — 麦克风/Lottie/权限/ASR 填框"
```

---

## 验证方案

1. `./gradlew assembleDebug` 编译成功
2. 安装到设备后：
   - 模型就绪后麦克风按钮可见，点击进入录音态（波形播放、输入框禁用）
   - 再说一句话 → ASR 自动结束 → 文本填入输入框 → 可修改后发送
   - 助手回复后 TTS 自动朗读
   - 快速发送两条消息 → 旧 TTS 被中断，只播最新的
   - 录音中按 Home 键 → 切回来录音已停止
   - 录音中再点麦克风 → 手动结束并识别

