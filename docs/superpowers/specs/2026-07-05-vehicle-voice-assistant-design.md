# 车载语音助手 — 设计规格说明书

## 概述

基于本地大模型的智能车控对话助手，运行在 RedMi Note Pro 13 上，通过 Function Calling 机制理解用户意图并调用车控方法，支持多指令和语义推理。

## 技术选型

| 维度 | 选择 | 理由 |
|------|------|------|
| 平台/语言 | Android + Java | 原生性能，车控场景最佳 |
| 推理引擎 | llama.cpp (JNI) | 骁龙 ARM NEON 优化成熟，灵活换模型 |
| 模型 | Qwen2.5-1.5B-Instruct Q4_K_M | 详见下方「模型选型说明」 |
| 模型大小 | ~1.04GB (gguf) | 骁龙 7s Gen 2 8GB RAM 可承载 |
| 上下文窗口 | 4096 tokens | 车控短交互场景足够，内存可控 |
| 调用机制 | Function Calling (JSON 模式) | 结构化输出 + 解析执行 |
| 车控范围 | 35 个方法（4 大类） | 气候/车窗车门/座椅内饰/驾驶灯光 |
| UI 架构 | MVVM + ViewBinding + XML | 传统布局方案，无 Compose 依赖 |

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
  │   └─ 不是 JSON → 兜底为纯文本，直接展示给用户（闲聊兜底）
  │
  ├─→ CommandPipeline.execute(commands)
  │      ↓
  │   逐条执行 VehicleService.execute(cmd)
  │   ├─ 批量冲突检测：同一目标的指令去重/合并
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

当前阶段所有方法本地模拟执行，打 log 输出：

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
  ├─ 容错：提取不到的 JSON 标记兜底到关键词匹配
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

## 模型选型说明

### 为什么选 Qwen2.5-1.5B-Instruct

| 维度 | 说明 |
|------|------|
| **中文能力** | Qwen 系列在中文理解/生成上的表现优于同参数量的 LLaMA/Phi 系列，CLUE 基准领先 |
| **Function Calling** | Qwen2.5 原生支持结构化 JSON 输出，Function Calling 准确率 90%+ |
| **参数规模** | 1.5B 是「手机能跑 + 效果够用」的最佳平衡点。0.5B 太弱无法稳定输出 JSON，3B+ 需要 4GB+ 显存 |
| **设备适配** | 骁龙 7s Gen 2 (RedMi Note Pro 13) 8GB RAM，1.5B Q4 量化后推理占用 ~1.5GB，系统可用内存约 5GB，留足余量 |
| **推理速度** | 4 线程推理约 8-12 tokens/s，车控场景输出 50-200 tokens，响应时间 2-5 秒，可接受 |
| **生态成熟** | GGUF 格式广泛，llama.cpp 有成熟的 ARM NEON 优化，社区活跃 |

### 为什么选 Q4_K_M 量化

| 维度 | 说明 |
|------|------|
| **文件体积** | FP16 原始 ~3GB → Q4_K_M ~1.04GB，减少 65%，便于分发和存储 |
| **精度损失** | Q4_K_M 相比 FP16 的困惑度损失 <2%，Function Calling JSON 格式准确率几乎无差异 |
| **内存占用** | 推理时内存占用 ~1.5GB（含 KV cache），8GB RAM 设备平稳运行 |
| **与 Q4_0 对比** | Q4_K_M 比 Q4_0 多了 attention 层的 6-bit 权重保留，对中文语义理解更友好 |

### 备选方案

| 方案 | 模型 | 优点 | 缺点 | 适用场景 |
|------|------|------|------|---------|
| A | InternLM2-1.8B | 中文优秀，数学推理强 | 生态不如 Qwen，GGUF 少 | 需要更强推理时 |
| B | Phi-3-mini | 英文顶尖，小模型 SOTA | 中文弱，Function Calling 差 | 仅英文场景 |
| C | Qwen2.5-3B + Q4_K_M | 效果更好 | 内存 > 2.5GB，8GB 设备勉强 | 高端设备 |

当前选择 Qwen2.5-1.5B 是最稳妥的方案，后续可平滑升级到更大量化版本。

### 模型下载

| 字段 | 值 |
|------|-----|
| **文件名** | `qwen2.5-1.5b-instruct-q4_k_m.gguf` |
| **文件大小** | ~1.04 GB |
| **下载地址** | `https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf` |
| **备用镜像** | ModelScope: `https://modelscope.cn/models/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/qwen2.5-1.5b-instruct-q4_k_m.gguf` |
| **存放路径** | `ExternalFilesDir/models/qwen2.5-1.5b-instruct-q4_k_m.gguf` |
| **下载方式** | App 内 `HttpURLConnection` 下载，显示进度条，支持断点续传（后续版本） |

**首次启动流程：**
1. 检查 `models/qwen2.5-1.5b-instruct-q4_k_m.gguf` 是否存在
2. 不存在 → 显示下载界面（进度条 + "下载模型"按钮 + 文件大小提示），输入框禁用
3. 用户点击"下载模型" → 后台线程下载，实时更新进度百分比
4. 下载完成 → 自动隐藏下载区域，加载模型，就绪后启用输入框
5. 下载失败 → 显示错误信息，按钮恢复可点击重试
6. 已存在 → 跳过下载界面，直接加载

**注意事项：**
- 不将模型打包进 APK（APK 体积 < 6MB，模型 1GB+ 分离分发）
- 联网权限已在 Manifest 声明 `INTERNET`
- 后续可添加 ModelScope 镜像切换，解决 HuggingFace 国内访问慢的问题

## Agent Layer

Agent 层是对话系统的中枢编排层。它不直接处理模型推理（交给 Engine 层），也不直接执行车控（交给 Vehicle 层），而是负责「理解用户 → 调度执行 → 组织回复」的完整生命周期。这种编排式设计使得各层职责清晰、可独立测试。

### 设计原则

**1. 单向依赖，逐层解耦**

```
UI  →  Agent  →  Engine  →  Vehicle
       (编排)    (推理)     (执行)
```

每一层只依赖下一层的抽象接口，Agent 不关心 Engine 内部是 llama.cpp 还是 API 调用，不关心 Vehicle 是 mock 还是真实 CAN 总线。这种设计使得：
- **可测试性**：Agent 可以用假 Engine 和假 Vehicle 独立测试编排逻辑
- **可替换性**：后续切换推理引擎或接入真实车控协议时，Agent 层代码零改动

**2. 单线程顺序处理，避免并发复杂性**

AgentManager 使用 `SingleThreadExecutor` 串行处理所有消息。原因：
- 车控操作有状态依赖（先关窗再锁车），并发执行会导致状态不一致
- 避免多线程竞争 VehicleState，无需加锁，减少 bug 来源
- 消息排队 + 丢弃中间输入 的策略在车控场景下反而更安全——用户快速说三句话，只需执行最终意图

**3. 两次推理优于单次**

第一次推理只做意图提取（输出结构化 JSON），第二次推理只做结果总结（输出自然语言）。拆成两次的原因：
- 相比一次推理同时输出 JSON + 文本，两次推理每步更简单，模型出错的概率更低
- 执行结果（成功/失败/跳过/错误信息）只有执行完才知道，所以只能在第一次推理后注入
- 中间的 JSON 解析/校验/安全审查 机会可以拦截危险操作（单次推理则暴露到用户界面）

### AgentManager — 对话总控

```
┌─────────────────────────────────────────────────────┐
│                  AgentManager.receive(input)          │
│                                                       │
│  1. 检查引擎是否就绪 ─→ 未就绪排队等待                   │
│  2. 检查可用内存 ─→ < 200MB 提示用户清理                 │
│  3. 组装上下文 ─→ ContextManager + PromptBuilder       │
│  4. 第一次推理 ─→ LlamaEngine.infer() ─→ JSON 输出      │
│  5. 解析指令 ─→ OutputParser.parse() ─→ List<ActionCmd> │
│  6. 执行指令 ─→ CommandPipeline.execute()               │
│  7. 收集结果 ─→ 注入 Prompt ─→ 第二次推理               │
│  8. 保存上下文 ─→ ContextManager.save()                 │
│  9. 回调 UI ─→ 展示回复 + 执行状态卡片                   │
│                                                       │
└─────────────────────────────────────────────────────┘
```

**设计考量：**

| 考量点 | 决策 | 原因 |
|--------|------|------|
| 线程模型 | `SingleThreadExecutor` | 避免车控并发，简化状态管理 |
| 快速连发 | 排队 + 丢弃中间输入 | 车控场景下最后一条指令代表最终意图，中间输入已过时 |
| 内存检查 | 推理前检查可用内存 < 200MB | 低于阈值推理可能 OOM，预先拒绝比崩溃好 |
| 超时控制 | 无（后续添加） | 当前模型本地推理不存在网络超时，后续可加推理硬超时 |

### ContextManager — 上下文管理

**核心问题**：4096 tokens 窗口如何装下系统提示词 + Function Schema + 历史对话 + 当前输入 + 模型输出？

**预算分配：**

```
┌──────────────────────────────────────────── 4096 tokens ─┐
│ System Prompt       │ 历史对话    │ 用户输入 │ 模型输出    │
│ + Function Schema   │ (滑动窗口)  │ ~50-100  │ ~200-400   │
│ ~950 tokens         │ ~2000 tokens│          │            │
│ (固定)              │ (动态管理)  │          │            │
└──────────────────────────────────────────────────────────┘
```

**设计考量：**

| 考量点 | 决策 | 原因 |
|--------|------|------|
| 窗口大小 | 4096 tokens | Qwen2.5-1.5B 原生上下文窗口，不超窗口避免截断 |
| 预留比例 | ~1200 tokens 固定给系统 + 输出 | 如果被历史对话占满，模型无法输出完整 JSON |
| 裁剪策略 | 成对删除最早消息 | 单条删除会丢失配对关系，保留完整 Q&A 语义 |
| 上限 20 轮 | 硬限制 | 即使 token 未满也截断，控制推理耗时（上下文越长推理越慢） |
| Token 估算 | `字符数 × 0.5` | 中文 1 字符 ≈ 1.5-2 tokens，0.5 是保守估算，实际会多留余量 |
| 数据结构 | `ArrayDeque<ChatMessage>` | 头尾高效增删，适配滑动窗口 |

**为什么不支持无限对话？**
- 车控场景的对话绝大多数是短交互（"开空调" → 完成），不需要长篇对话记忆
- 窗口内的 20 轮已经覆盖了「调整空调→开窗→换驾驶模式→问天气」这样的复杂连续交互
- 超过 20 轮，最早的对话大概率与当前意图无关，保留反而引入噪声

### CommandPipeline — 多指令执行

**核心问题**：用户一句话 "关车窗、锁车门、开空调 22 度" 会产出 3 条指令，如何可靠执行？

**执行流程：**

```
List<ActionCommand>
  │
  ├── 第1步：去重合并
  │   按 _target 分组（同一控制域的指令）
  │   "开窗 50%" + "开窗 100%" → 只执行后者
  │   目的：避免冲突、减少执行次数
  │
  ├── 第2步：参数校验（VehicleService）
  │   所有参数走 min/max/enum 硬约束
  │   例：空调 temp: 16-32，拒绝 temp: 100
  │   目的：不信任模型输出，避免越界操作
  │
  ├── 第3步：安全检查（VehicleService）
  │   requireParked 标记的方法检查 isParked
  │   例：行驶中拒绝 adjust_seat/memory_seat
  │   目的：安全第一，防止行驶中危险操作
  │
  ├── 第4步：幂等检查（VehicleService）
  │   对比当前 VehicleState，已在目标状态则跳过
  │   例：空调已开 24°C，再发 "开空调 24°C" → 跳过
  │   目的：减少冗余执行，让用户感知到"已处理"
  │
  └── 第5步：执行（顺序）
      逐条执行，收集 ExecutionResult
      nonCritical 失败 → 跳过继续
      critical 失败 → 中断，后续指令不执行
```

**设计考量：**

| 考量点 | 决策 | 原因 |
|--------|------|------|
| 执行顺序 | **顺序**而非并行 | 车控操作有依赖（先关窗才能锁车），并行会破坏状态一致性 |
| 去重策略 | 后者覆盖前者 | 用户的意图在最后一句，后半句才是修正后的需求 |
| 部分失败 | 区分 critical/nonCritical | 安全类（锁车）失败必须中断；舒适类（氛围灯）失败不阻塞空调 |
| 执行时机 | 全部校验完再执行 | 先通过所有检查再开始，拒绝执行到一半才发现后面的指令不安全 |
| 结果回注 | 全部结果注入第二次推理 | 模型需要知道每个指令的成败才能生成准确的汇总回复 |

**标记说明：**

| 标记 | 含义 | 示例 | 失败行为 |
|------|------|------|---------|
| `requireParked: true` | 需驻车 | 座椅调节、驾驶模式 | 行驶中拒绝执行 |
| `critical: true` | 安全关键 | 车门锁、巡航控制 | 失败时中断后续指令 |
| `_target` | 控制域 | "ac"、"window"、"door_lock" | 同域去重合并 |

## 边界场景处理

### 安全

| # | 场景 | 处理策略 |
|---|------|---------|
| 1 | 行驶中危险操作 | 部分方法标记 `requireParked: true`，执行前校验车辆状态，行驶中拒绝并提示 |
| 2 | 参数越界 | 所有参数在执行前走 min/max 硬校验，不信任模型输出值 |

### 对话

| # | 场景 | 处理策略 |
|---|------|---------|
| 3 | 批量操作冲突 | 同一批指令中相同目标做去重/合并，后者覆盖前者 |
| 4 | 模糊意图 | Prompt 引导模型追问确认参数，不瞎猜 |
| 5 | 否定意图 | Prompt 中明确处理否定语义（"不要开空调" = 不调用 set_ac） |
| 6 | 连续追问 | ContextManager 保留完整历史轮次，由模型理解上下文 |
| 7 | 闲聊穿插 | OutputParser 兜底：JSON 解析失败 → 纯文本直接展示 |
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
