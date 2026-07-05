/**
 * llama.cpp JNI 桥接层 — 完整推理实现
 *
 * 职责：
 *   1. 加载 GGUF 模型文件（Qwen2.5-1.5B Q4_K_M）
 *   2. 将 ChatML 格式 Prompt 分词后送入 llama.cpp 推理
 *   3. 采样生成输出文本，返回给 Java 层
 *
 * 对应 Java 类：com.example.vehicleassistant.engine.LlamaEngine
 * JNI 函数命名规则：Java_<包名>_<类名>_<方法名>
 *
 * 链接方式：静态链接 libllama.a + libggml.a + libllama-common.a
 *          全部打包进 libllama_jni.so，无需额外 .so 文件
 *
 * 编译目标：Android arm64-v8a (ARMv8.6-A + dotprod + fp16)
 */

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

// Android 日志标签，在 logcat 中过滤：adb logcat -s LlamaJNI
#define TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * 推理上下文结构体
 *
 * 存储 llama.cpp 运行时所需的三个核心对象：
 * - model:   加载到内存的模型权重（只读，可被多个 context 共享）
 * - context: 推理会话状态（KV cache、token 缓冲区等）
 * - vocab:   词表指针，用于 token ↔ 文本 互转
 *
 * Java 侧通过 jlong 持有本结构体的指针，跨 JNI 调用传递。
 * 注意：jlong 在 64 位系统上是 64 位，足够存放指针。
 */
struct LlamaContext {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    bool initialized = false;
};

extern "C" {

// ═══════════════════════════════════════════════════════════════
// nativeInit — 加载模型并创建推理上下文
// ═══════════════════════════════════════════════════════════════
//
// 参数（来自 Java 层 ModelConfig）：
//   modelPath   — GGUF 文件绝对路径
//   contextSize — 上下文窗口大小（4096 tokens）
//   maxTokens   — 最大输出 token 数（512，当前未用，由 nativeInfer 控制）
//   temperature — 温度参数（当前未用，由 nativeInfer 内置 0.1）
//   topP        — Top-P 采样参数（当前未用，由 nativeInfer 内置 0.9）
//   threads     — CPU 推理线程数（4）
//
// 返回值：
//   成功 → LlamaContext 指针（jlong）
//   失败 → 0（Java 侧 isLoaded() 返回 false）
//
JNIEXPORT jlong JNICALL
Java_com_example_vehicleassistant_engine_LlamaEngine_nativeInit(
    JNIEnv* env, jobject /*thiz*/,
    jstring modelPath, jint contextSize, jint /*maxTokens*/,
    jfloat /*temperature*/, jfloat /*topP*/, jint threads) {

    // JNI 字符串 → C 字符串
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGD("正在加载模型: %s (ctx=%d, threads=%d)", path, contextSize, threads);

    auto* ctx = new LlamaContext();

    // 步骤 1：初始化 llama.cpp 全局后端（只需调用一次）
    llama_backend_init();

    // 步骤 2：从文件加载模型权重到内存
    //         Q4_K_M 量化模型约占用 ~1.5GB RAM（含 MMAP）
    llama_model_params model_params = llama_model_default_params();
    ctx->model = llama_model_load_from_file(path, model_params);
    if (!ctx->model) {
        LOGE("模型加载失败: %s（文件路径是否正确？文件是否完整？）", path);
        delete ctx;
        env->ReleaseStringUTFChars(modelPath, path);
        return 0; // 返回 0 表示加载失败
    }

    // 步骤 3：创建推理上下文（分配 KV cache 等工作内存）
    //         n_ctx=4096 时 KV cache 约占用 ~256MB
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads; // 批量解码也用相同线程数
    ctx->ctx = llama_init_from_model(ctx->model, ctx_params);
    if (!ctx->ctx) {
        LOGE("创建推理上下文失败（可能内存不足）");
        llama_model_free(ctx->model);
        delete ctx;
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }

    // 步骤 4：获取词表指针
    ctx->vocab = llama_model_get_vocab(ctx->model);
    ctx->initialized = true;

    env->ReleaseStringUTFChars(modelPath, path);
    LOGD("模型加载完成");
    return reinterpret_cast<jlong>(ctx);
}

// ═══════════════════════════════════════════════════════════════
// nativeInfer — 执行推理并返回生成文本
// ═══════════════════════════════════════════════════════════════
//
// 参数：
//   ptr    — nativeInit 返回的 LlamaContext 指针
//   prompt — ChatML 格式的完整 Prompt 字符串（UTF-8）
//
// 返回值：
//   成功 → 模型生成的文本（UTF-8）
//   失败 → 错误提示字符串（以 [ 开头）
//
// 推理流程：
//   1. Tokenize            — 将 Prompt 文本转为 token 序列
//   2. Prefill (批量解码)   — 把输入 token 一次性喂给模型（batch=512）
//   3. Generate (自回归生成) — 逐 token 采样，直到 EOS 或到达 max_new
//   4. Detokenize          — 把采样出的 token 转回文本
//
JNIEXPORT jstring JNICALL
Java_com_example_vehicleassistant_engine_LlamaEngine_nativeInfer(
    JNIEnv* env, jobject /*thiz*/, jlong ptr, jstring prompt) {

    auto* ctx = reinterpret_cast<LlamaContext*>(ptr);
    if (!ctx || !ctx->initialized) {
        return env->NewStringUTF("[模型未初始化]");
    }

    const char* input = env->GetStringUTFChars(prompt, nullptr);
    std::string text(input);
    int inputLen = static_cast<int>(text.size());
    env->ReleaseStringUTFChars(prompt, input);

    LOGD("推理输入: %d 字符", inputLen);

    // --- 第 1 步：Tokenize ---
    // 第一次调用传 nullptr 获取所需缓冲区大小（返回负数表示计数模式）
    // 第二次调用传实际缓冲区进行分词
    // 参数：add_bos=true（添加句首标记）, parse_special=true（解析特殊 token）
    int n_tokens = -llama_tokenize(ctx->vocab, text.c_str(), inputLen, nullptr, 0, true, true);
    if (n_tokens < 0) {
        LOGE("Tokenize 失败：输入文本可能包含非法字符");
        return env->NewStringUTF("[分词错误]");
    }
    std::vector<llama_token> tokens(n_tokens);
    llama_tokenize(ctx->vocab, text.c_str(), inputLen, tokens.data(), n_tokens, true, true);
    LOGD("分词完成: %d tokens (ctx=%d)", n_tokens, llama_n_ctx(ctx->ctx));

    // --- 第 2 步：Prefill（批量推理输入 token）---
    // 先清零 KV cache，避免多次推理间缓存累积超出 n_ctx 限制
    llama_memory_clear(llama_get_memory(ctx->ctx), true);

    // 每次最多处理 512 个 token，超长输入分多批执行
    const int n_batch = 512;
    for (size_t i = 0; i < tokens.size(); i += n_batch) {
        int n = std::min(n_batch, static_cast<int>(tokens.size() - i));
        if (llama_decode(ctx->ctx, llama_batch_get_one(tokens.data() + i, n)) != 0) {
            LOGE("推理执行失败：llama_decode 返回错误");
            return env->NewStringUTF("[推理错误]");
        }
    }

    // --- 第 3 步：Generate（自回归采样生成）---
    // 采样链：Temperature(0.1) → Top-P(0.9) → 随机种子(1234)
    // 温度 0.1 接近贪心解码，适合车控这种需要确定性输出的场景
    std::string output;
    const int max_new = 64; // 最多生成 64 个新 token（车控 JSON 通常只需 15-30 tokens）
    const auto sparams = llama_sampler_chain_default_params();
    auto* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.1f));      // 低温度 → 确定性
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));  // Top-P 核采样
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));       // 随机种子

    const llama_token eos = llama_vocab_eos(ctx->vocab); // 结束标记
    for (int i = 0; i < max_new; i++) {
        // 采样一个 token
        llama_token token = llama_sampler_sample(smpl, ctx->ctx, -1);
        if (token == eos) break; // 遇到 EOS 停止

        // 步骤 4：token → 文本（边生成边拼接，避免最后再遍历一遍）
        char buf[256];
        int len = llama_token_to_piece(ctx->vocab, token, buf, sizeof(buf), 0, true);
        if (len > 0) output.append(buf, len);

        // 增量推理：把刚生成的 token 喂回去，更新 KV cache
        if (llama_decode(ctx->ctx, llama_batch_get_one(&token, 1)) != 0) {
            break;
        }
    }
    llama_sampler_free(smpl);

    LOGD("推理输出: %zu 字符", output.size());
    return env->NewStringUTF(output.c_str());
}

// ═══════════════════════════════════════════════════════════════
// nativeRelease — 释放模型和推理上下文
// ═══════════════════════════════════════════════════════════════
//
// 释放顺序：context → model → backend
// 注意必须按此顺序，context 依赖 model，不能先释放 model
//
JNIEXPORT void JNICALL
Java_com_example_vehicleassistant_engine_LlamaEngine_nativeRelease(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong ptr) {

    auto* ctx = reinterpret_cast<LlamaContext*>(ptr);
    if (ctx) {
        if (ctx->ctx)   llama_free(ctx->ctx);       // 1. 释放推理上下文（KV cache）
        if (ctx->model) llama_model_free(ctx->model); // 2. 释放模型权重
        llama_backend_free();                         // 3. 释放全局后端
        delete ctx;
        LOGD("模型已释放");
    }
}

} // extern "C"
