# CMake 构建配置 — Android arm64-v8a 交叉编译
# 将 llama.cpp 静态链接进 libllama_jni.so，供 Java 层通过 JNI 调用
cmake_minimum_required(VERSION 3.22.1)
project("llama_jni")

# llama.cpp 子模块路径（app/src/main/cpp → 项目根目录 → llama.cpp/）
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp")

# ═══════════════════════════════════════════════════════════════
# 纯 CPU 推理配置
# 目标设备：RedMi Note Pro 13 (骁龙 7s Gen 2, Cortex-A78 + A55)
# 关闭 GPU 后端，消除 GPU↔CPU 同步开销
# ═══════════════════════════════════════════════════════════════

# CPU 后端（ARM NEON/dotprod/fp16 优化）
set(GGML_CPU ON CACHE BOOL "启用 CPU 后端" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "ARM 指令集优化目标" FORCE)

# 禁用所有 GPU 后端
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "NDK OpenMP 依赖问题" FORCE)
set(GGML_LLAMAFILE ON CACHE BOOL "启用 Llamafile 优化 GEMM" FORCE)

# 额外编译优化（-ffast-math 与 ggml 不兼容）
add_compile_options(-O3)

# 静态链接
set(BUILD_SHARED_LIBS OFF CACHE BOOL "生成静态库" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "编译 llama-common" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "不需要 SSL" FORCE)

# 引入 llama.cpp 源码编译
add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama)

# JNI 动态库
add_library(llama_jni SHARED llama_jni.cpp)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/ggml/include
)

# 链接依赖：llama + ggml + llama-common + Android log
find_library(log-lib log)
target_link_libraries(llama_jni llama llama-common ggml ${log-lib})
