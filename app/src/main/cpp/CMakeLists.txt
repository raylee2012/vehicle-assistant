# CMake 构建配置 — Android arm64-v8a 交叉编译
# 将 llama.cpp 静态链接进 libllama_jni.so，供 Java 层通过 JNI 调用
cmake_minimum_required(VERSION 3.22.1)
project("llama_jni")

# llama.cpp 子模块路径（app/src/main/cpp → 项目根目录 → llama.cpp/）
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp")

# ═══════════════════════════════════════════════════════════════
# CPU-only 交叉编译配置
# 目标设备：RedMi Note Pro 13 (骁龙 7s Gen 2, ARMv8.2-A)
# 启用 NEON SIMD + dotprod 点积指令 + fp16 半精度浮点加速
# Vulkan 暂不可用：需要宿主原生 C++17 编译器 + SPIRV-Headers
# ═══════════════════════════════════════════════════════════════
set(GGML_CPU ON CACHE BOOL "启用 CPU 后端" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "ARM 指令集优化目标（Cortex-A78 = ARMv8.2-A）" FORCE)

# Disable repack — 避免 Q4_K gemm 中的内存对齐 crash
set(GGML_CPU_REPACK OFF CACHE BOOL "禁用 repack 防止 SIMD 对齐 crash" FORCE)

# 禁用 Android 不支持的编译选项
set(GGML_OPENMP OFF CACHE BOOL "NDK 的 OpenMP 依赖解析有问题，禁用" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "Llamafile 不支持 Android" FORCE)

# 禁用所有 GPU 后端（Android 只用 CPU 推理）
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)

# 静态链接：所有目标库打包进一个 .so，减少 APK 内的文件数量
set(BUILD_SHARED_LIBS OFF CACHE BOOL "生成静态库 .a" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "编译 llama-common（chat template 等）" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "不需要 SSL" FORCE)

# 引入 llama.cpp 源码编译（产出 libllama.a, libggml.a, libllama-common.a）
add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama)

# JNI 动态库：Java 通过 System.loadLibrary("llama_jni") 加载
add_library(llama_jni SHARED llama_jni.cpp)

# 头文件路径：llama.h, ggml.h, common 工具
target_include_directories(llama_jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/ggml/include
)

# 链接依赖：llama + ggml + llama-common + Android log
find_library(log-lib log)
target_link_libraries(llama_jni llama llama-common ggml ${log-lib})
