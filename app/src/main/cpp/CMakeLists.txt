cmake_minimum_required(VERSION 3.22.1)
project("llama_jni")

add_library(llama_jni SHARED llama_jni.cpp)

find_library(log-lib log)

target_link_libraries(llama_jni ${log-lib})

# TODO: 接入真实 llama.cpp 后取消以下注释
# add_subdirectory(llama.cpp)  # llama.cpp 源码目录
# target_link_libraries(llama_jni llama ${log-lib})
