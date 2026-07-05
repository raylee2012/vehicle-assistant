#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaContext {
    bool initialized = false;
    // TODO: 接入真实 llama.cpp 后补充 llama_model*, llama_context* 等
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_vehicleassistant_engine_LlamaEngine_nativeInit(
    JNIEnv* env, jobject thiz,
    jstring modelPath, jint contextSize, jint maxTokens,
    jfloat temperature, jfloat topP, jint threads) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGD("Loading model: %s (ctx=%d, threads=%d)", path, contextSize, threads);
    env->ReleaseStringUTFChars(modelPath, path);

    auto* ctx = new LlamaContext();
    ctx->initialized = true;
    // TODO: 接入 real llama.cpp:
    //   llama_backend_init()
    //   llama_model_params model_params = llama_model_default_params();
    //   ctx->model = llama_load_model_from_file(path, model_params);
    //   llama_context_params ctx_params = llama_context_default_params();
    //   ctx_params.n_ctx = contextSize;
    //   ctx->llama_ctx = llama_new_context_with_model(ctx->model, ctx_params);

    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_example_vehicleassistant_engine_LlamaEngine_nativeInfer(
    JNIEnv* env, jobject thiz, jlong ptr, jstring prompt) {

    auto* ctx = reinterpret_cast<LlamaContext*>(ptr);
    if (!ctx || !ctx->initialized) {
        return env->NewStringUTF("[模型未初始化]");
    }

    const char* input = env->GetStringUTFChars(prompt, nullptr);
    LOGD("Inference prompt length: %zu chars", strlen(input));
    env->ReleaseStringUTFChars(prompt, input);

    // TODO: 接入 real llama.cpp:
    //   llama_eval(ctx->llama_ctx, tokens, ...)
    //   llama_sample_token(ctx->llama_ctx, ...)
    //   std::string output = detokenize(sampled_tokens);

    // 当前返回占位 JSON（无模型时的降级回复）
    return env->NewStringUTF("[{\"action\":\"set_ac\",\"params\":{\"power\":true,\"temp\":22,\"mode\":\"auto\"}}]");
}

JNIEXPORT void JNICALL
Java_com_example_vehicleassistant_engine_LlamaEngine_nativeRelease(
    JNIEnv* env, jobject thiz, jlong ptr) {

    auto* ctx = reinterpret_cast<LlamaContext*>(ptr);
    if (ctx) {
        // TODO: 接入 real llama.cpp:
        //   llama_free(ctx->llama_ctx);
        //   llama_free_model(ctx->model);
        //   llama_backend_free();
        delete ctx;
        LOGD("Model released");
    }
}

} // extern "C"
