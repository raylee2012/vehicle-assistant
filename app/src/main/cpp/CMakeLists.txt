# CMake 构建配置 — Android arm64-v8a 交叉编译
# 将 llama.cpp 静态链接进 libllama_jni.so，供 Java 层通过 JNI 调用
cmake_minimum_required(VERSION 3.22.1)
project("llama_jni")

# llama.cpp 子模块路径（app/src/main/cpp → 项目根目录 → llama.cpp/）
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp")

# ═══════════════════════════════════════════════════════════════
# OpenCL + CPU 混合配置
# 目标设备：RedMi Note Pro 13 (骁龙 7s Gen 2, Adreno 710)
# Adreno OpenCL 比 Vulkan 稳定可靠
# ═══════════════════════════════════════════════════════════════

# CPU 后端（ARM NEON/dotprod/fp16 优化，作为 fallback）
set(GGML_CPU ON CACHE BOOL "启用 CPU 后端" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "ARM 指令集优化目标" FORCE)
set(GGML_CPU_REPACK OFF CACHE BOOL "禁用 repack 防止 SIMD 对齐 crash" FORCE)

# OpenCL 后端（Adreno 专用 kernel）
set(GGML_OPENCL ON CACHE BOOL "启用 OpenCL GPU 加速" FORCE)
set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "Adreno 专用 kernel" FORCE)
set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "嵌入 OpenCL kernel" FORCE)

# OpenCL 头文件和库 — 交叉编译 Android arm64-v8a
# stub libOpenCL.so 仅构建时链接，dev 端 /vendor/lib64/libOpenCL.so 运行时解析
set(OpenCL_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../opencl_include" CACHE INTERNAL "OpenCL headers")
set(OpenCL_LIBRARY "${CMAKE_CURRENT_SOURCE_DIR}/../../../../opencl_stub/libOpenCL.so" CACHE INTERNAL "OpenCL stub (build only)")
set(OpenCL_LIBRARIES "${OpenCL_LIBRARY}" CACHE INTERNAL "")
set(OpenCL_INCLUDE_DIRS "${OpenCL_INCLUDE_DIR}" CACHE INTERNAL "")

# 允许 NDK 未定义的 OpenCL 符号（运行时由设备端 libOpenCL.so 解析）
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--allow-shlib-undefined")

# 禁用 Vulkan 和其他 GPU 后端
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "NDK OpenMP 依赖问题" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "Llamafile 不支持 Android" FORCE)

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

# 链接依赖：llama + ggml + llama-common + OpenCL + Android log
find_library(log-lib log)
target_link_libraries(llama_jni llama llama-common ggml ${log-lib})
