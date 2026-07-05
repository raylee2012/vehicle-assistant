# 车载语音助手 — 设计规格说明书

## 概述

基于本地大模型的智能车控对话助手，运行在 RedMi Note Pro 13 上，通过 Function Calling 机制理解用户意图并调用车控方法，支持多指令和语义推理。

## 技术选型

| 维度 | 选择 | 理由 |
|------|------|------|
| 平台/语言 | Android + Java | 原生性能，车控场景最佳 |
| 推理引擎 | llama.cpp (JNI) | 骁龙 ARM NEON 优化成熟，灵活换模型 |
| 模型 | Qwen2.5-1.5B-Instruct + Q4_K_M | ~1.0GB，中文 Function Calling 90%+ 准确率 |
| 上下文窗口 | 4096 tokens | 车控短交互场景足够，内存可控 |
| 调用机制 | Function Calling (JSON Schema) | 结构化输出 + 解析执行 |
| 车控范围 | 全量 35 个方法 | 4 大类：气候/车窗/内饰/驾驶灯光 |
| UI 架构 | MVVM + ViewBinding + XML | 传统布局方案，无需 Compose 依赖 |

## 整体架构

```
┌──────────────────────────────────────────────────┐
│  UI Layer (MVVM + ViewBinding + XML)               │
│  ChatActivity  activity_chat.xml  MainViewModel    │
│  对话列表 · 输入框 · 执行状态卡片                    │
├──────────────────────────────────────────────────┤
│  Agent Layer                                      │
│  AgentManager  CommandPipeline  ContextManager     │
│  对话编排 · 多指令拆解 · 上下文滑动窗口              │
├──────────────────────────────────────────────────┤
│  Engine Layer                                     │
│  LlamaEngine(JNI)  PromptBuilder  OutputParser     │
│  模型推理 · Prompt 组装 · JSON 解析                 │
├──────────────────────────────────────────────────┤
│  Vehicle Layer                                    │
│  VehicleService  FunctionRegistry  VehicleState    │
│  35 个车控方法 · 方法注册表 · 车辆状态维护           │
└──────────────────────────────────────────────────┘
```

### 依赖方向

```
UI → Agent → Engine → Vehicle
              Engine → Vehicle (只读 Function Schema)
```

## 数据流（单轮对话完整流程）

```
用户: "开空调到22度，关所有车窗"
  │
  ▼
AgentManager.receive(input)
  │
  ├─→ ContextManager.buildPrompt(system, tools, history, input)
  │      ↓
  │   PromptBuilder 组装完整 Prompt (Qwen2.5 格式)
  │
  ├─→ LlamaEngine.infer(prompt)    ← 第一次推理（意图提取）
  │      ↓
  │   模型输出 JSON:
  │   [{action:"set_ac", params:{temp:22, power:true}},
  │    {action:"control_window", params:{position:"all", action:"close"}}]
  │
  ├─→ OutputParser.parse(json)
  │      ↓
  │   容错解析 → List<ActionCommand>
  │   ├─ 是合法 JSON → 解析出 commands
  │   └─ 不是 JSON → fallback 为纯文本，直接展示给用户（闲聊兜底）
  │
  ├─→ CommandPipeline.execute(commands)
  │      ↓
  │   逐条执行 VehicleService.execute(cmd)
  │   ├─ 批量冲突检测：同一 target 的指令去重/合并
  │   ├─ 参数校验：min/max 强约束
  │   ├─ 危险操作检查：requireParked 标记验证
  │   ├─ 幂等检查：当前状态已匹配则跳过
  │   └─ 部分失败策略：nonCritical 跳过继续，critical 中断
  │      ↓
  │   收集 ExecutionReport
  │
  ├─→ 执行结果注入 Prompt → 第二次推理（结果汇总）
  │      ↓
  │   模型输出自然语言回复
  │
  ├─→ ContextManager.save(本轮 user + assistant 完整消息)
  │
  └─→ 展示最终回复 + 执行状态卡片
```

## Vehicle Layer

### 车控方法（4 大类，35 个）

**气候控制 ClimateControl**
- `set_ac` — `power: boolean, temp: int(16-32), mode: enum(auto/cool/heat/fan)`
- `set_fan_speed` — `level: int(1-7)`
- `set_air_circulation` — `mode: enum(internal/external/auto)`
- `defrost` — `position: enum(front/rear/both), power: boolean`
- `seat_heat` — `seat: enum(driver/passenger/rear_left/rear_right), level: int(0-3)`
- `seat_vent` — `seat: enum(driver/passenger), level: int(0-3)`
- `steering_heat` — `power: boolean`

**车窗与车门 WindowDoor**
- `control_window` — `position: enum(fl/fr/rl/rr/all), action: enum(open/close/stop), percent: int(0-100)`
- `control_door_lock` — `action: enum(lock/unlock)`
- `control_trunk` — `action: enum(open/close)`
- `control_sunroof` — `action: enum(open/close/tilt), percent: int(0-100)`
- `child_lock` — `power: boolean`

**座椅与内饰 Interior**
- `adjust_seat` — `seat: enum(driver/passenger), direction: enum(forward/backward/up/down/recline), steps: int`
- `memory_seat` — `profile: int(1-3)`
- `adjust_mirror` — `mirror: enum(left/right), direction: enum(up/down/in/out)`
- `fold_mirror` — `power: boolean`
- `ambient_light` — `color: enum(red/blue/green/white/warm/auto), brightness: int(0-100)`

**驾驶与灯光 DriveLight**
- `control_headlight` — `mode: enum(auto/low/high/off)`
- `control_fog_light` — `power: boolean`
- `hazard_light` — `power: boolean`
- `drive_mode` — `mode: enum(eco/comfort/sport/snow/offroad)`
- `cruise_control` — `power: boolean, speed: int(30-150)`
- `wiper` — `speed: enum(off/low/medium/high/auto)`

### FunctionRegistry

- `generateToolsSchema(): String` — 生成完整 JSON Schema 数组
- `execute(action, params): ExecutionResult` — 按名称执行方法

### 模拟执行

当前阶段所有方法本地 mock，打 log 输出：

```java
public ExecutionResult execute(String action, Map<String, Object> params) {
    Log.d("VehicleService", "执行: " + action + " 参数: " + params);
    return new ExecutionResult(true, action, params);
}
```

后续接入真实车控协议时替换 VehicleService 实现即可。

## Engine Layer

### LlamaEngine（JNI 桥接）

```
LlamaEngine.java    ← Java 层: init(modelPath), infer(prompt), release()
    │ JNI
llama_jni.cpp       ← C++ 适配: llama.cpp API, 内存管理, 回调
    │
libllama.so         ← 预编译 .so (android arm64-v8a)
```

### PromptBuilder

组装 Qwen2.5 ChatML 格式 Prompt：

```
<|im_start|>system
你是智能车控助手。根据用户意图：
- 车控指令 → 输出 JSON 数组 [{action, params}, ...]
- 闲聊/模糊意图 → 直接回复文本
可用方法：[Function Schema JSON]
<|im_end|>
<|im_start|>user
{用户输入}
<|im_end|>
```

### OutputParser

```
模型输出
  ├─ 尝试提取 JSON 数组 → 解析为 List<ActionCommand>
  ├─ 修复常见格式错误（缺引号、尾逗号）
  ├─ 容错：提取不到的 JSON 标记 fallback 到关键词匹配
  └─ 都不是 → 当作纯文本直接返回（闲聊兜底）
```

### 推理参数

| 参数 | 值 | 说明 |
|------|-----|------|
| temperature | 0.1 | 车控需确定性输出 |
| top_p | 0.9 | |
| max_tokens | 512 | 输出足够 |
| context_size | 4096 | |
| threads | 4 | 骁龙 8 核留余量 |

### 模型下载

首次启动检查 `model/` 目录 → 无模型弹窗引导通过 DownloadManager 从 OSS 下载 `.gguf` → 后台下载 + 通知栏进度。

## Agent Layer

### AgentManager — 对话总控

编排单轮对话的完整生命周期，包括两次推理（意图提取 + 结果汇总）。

### ContextManager — 上下文管理

- 上下文窗口: **4096 tokens**
- 预留给 prompt + 输出: **1200 tokens**
- 历史消息上限: **2896 tokens**
- 最大对话轮数: **20 轮**
- 裁剪策略: 从最早消息开始成对删除（user + assistant）
- 预估 token 数: `字符数 × 0.5`（中文粗略估算）

### CommandPipeline — 多指令执行

- 顺序执行，逐条收集 ActionResult
- 批量冲突检测: 同一 target 的指令去重/合并，后者覆盖前者
- 部分失败策略: `nonCritical` 标记的跳过继续，`critical` 的中断执行
- 执行结果回注: 本轮所有结果作为上下文注入第二次推理

### ResponseBuilder

执行结果 → 自然语言回复（由第二次推理完成），同时展现实体化的执行状态卡片。

## 边界场景处理

### 安全

| # | 场景 | 处理策略 |
|---|------|---------|
| 1 | 行驶中危险操作 | 部分方法标记 `requireParked: true`，执行前校验车辆状态，行驶中拒绝并提示 |
| 2 | 参数越界 | 所有参数在执行前走 min/max 硬校验，不信任模型输出值 |

### 对话

| # | 场景 | 处理策略 |
|---|------|---------|
| 3 | 批量操作冲突 | 同一批指令中相同 target 做去重/合并，后者覆盖前者 |
| 4 | 模糊意图 | Prompt 引导模型追问确认参数，不瞎猜 |
| 5 | 否定意图 | Prompt 中明确处理否定语义（"不要开空调" = 不调用 set_ac） |
| 6 | 连续追问 | ContextManager 保留完整历史轮次，由模型理解上下文 |
| 7 | 闲聊穿插 | OutputParser fallback: JSON 解析失败 → 纯文本直接展示 |
| 8 | 执行结果回注 | 同一轮内两次推理：第一次提取意图 → 执行 → 结果注入 → 第二次推理生成自然语言回复 |

### 系统

| # | 场景 | 处理策略 |
|---|------|---------|
| 9 | 模型未就绪 | 显示"模型加载中..."，加载完成后自动响应待处理消息 |
| 10 | 内存不足 | 推理前检查可用内存，不足时提示用户清理后台后再试 |
| 11 | 应用切后台 | 推理进行中切后台 → 暂停推理，onResume 时自动恢复 |
| 12 | 快速连发 | 消息队列串行处理，处理中收到新消息则排队等待 |

### 执行

| # | 场景 | 处理策略 |
|---|------|---------|
| 13 | 部分失败 | nonCritical 指令失败跳过继续，critical 指令失败中断后续执行 |
| 14 | 幂等操作 | 执行前检查 VehicleState 当前状态，已处于目标状态则跳过并告知用户 |

## VehicleState — 车辆状态维护

维护一个内存中的车辆状态对象，每次执行成功更新对应字段：

```java
public class VehicleState {
    boolean acPower;          int acTemp;           String acMode;
    int fanSpeed;             String airCirc;
    boolean frontDefrost;     boolean rearDefrost;
    Map<String, Integer> seatHeatLevel;
    Map<String, Integer> seatVentLevel;
    boolean steeringHeat;
    Map<String, Integer> windowPercent; // "fl"→100, "fr"→0 ...
    boolean doorLocked;       boolean trunkOpen;
    boolean sunroofOpen;      int sunroofPercent;
    boolean childLock;
    Map<String, Integer> seatPosition; // seat + direction → steps
    int memorySeatProfile;
    Map<String, String> mirrorPosition;
    boolean mirrorFolded;
    String ambientColor;      int ambientBrightness;
    String headlightMode;     boolean fogLight;     boolean hazardLight;
    String driveMode;         boolean cruiseOn;     int cruiseSpeed;
    String wiperSpeed;
    boolean isParked;         // 来自传感器（当前 mock 为 true）
}
```

状态用于:
1. 幂等检查 — 执行前对比目标状态
2. 安全校验 — `requireParked` 方法检查 `isParked`
3. 上下文注入 — 第二次推理时提供当前状态给模型

## 项目结构

```
app/
├── src/main/java/com/example/vehicleassistant/
│   ├── ui/
│   │   ├── ChatActivity.java
│   │   ├── ChatAdapter.java
│   │   ├── MainViewModel.java
│   │   └── widgets/
│   │       ├── MessageBubble.java
│   │       └── ExecutionCard.java
│   │
│   ├── res/layout/
│   │   ├── activity_chat.xml
│   │   ├── item_message_user.xml
│   │   ├── item_message_assistant.xml
│   │   └── widget_execution_card.xml
│   │
│   ├── agent/
│   │   ├── AgentManager.java          ← 对话编排
│   │   ├── ContextManager.java        ← 上下文管理
│   │   ├── CommandPipeline.java       ← 指令执行管线
│   │   └── ResponseBuilder.java       ← 回复构建
│   │
│   ├── engine/
│   │   ├── LlamaEngine.java           ← JNI 接口
│   │   ├── PromptBuilder.java         ← Prompt 组装
│   │   └── OutputParser.java          ← JSON 解析 + 容错
│   │
│   ├── vehicle/
│   │   ├── VehicleService.java        ← 车控执行入口
│   │   ├── VehicleState.java          ← 车辆状态
│   │   ├── FunctionRegistry.java      ← 方法注册 + Schema 生成
│   │   └── models/
│   │       ├── ToolDefinition.java    ← 工具定义模型
│   │       ├── ActionCommand.java     ← 解析后的指令模型
│   │       └── ExecutionResult.java   ← 执行结果模型
│   │
│   └── model/
│       ├── ChatMessage.java           ← 对话消息模型
│       └── ModelConfig.java           ← 模型配置
│
├── src/main/cpp/
│   ├── llama_jni.cpp                  ← JNI 适配层
│   └── CMakeLists.txt
│
└── src/main/assets/
    └── (不含模型，模型通过下载获取)
```
