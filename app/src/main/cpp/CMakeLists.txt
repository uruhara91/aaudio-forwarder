cmake_minimum_required(VERSION 3.10.2)

project("aaudio_forwarder")

# OPTIMISASI STABIL (Tanpa LTO/flto yang bikin error di CI)
# Kita tetap pakai -O3 dan -ffast-math yang aman.
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -O3 -ffast-math")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-stack-protector -fvisibility=hidden -fvisibility-inlines-hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-unused-parameter")

# Hapus -fuse-ld=lld dan -flto. 
# Biarkan NDK v25 memilih linker defaultnya (sudah otomatis LLD) agar tidak crash mencari LLVMgold.so
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections,--strip-all")

add_library(
    aaudio_forwarder
    SHARED
    jni_bridge.cpp
    network_client.cpp
)

target_link_libraries(
    aaudio_forwarder
    log
    android
)

# Hapus properti LTO karena ini butuh setup plugin yang rumit di CI
# set_target_properties(aaudio_forwarder PROPERTIES
#     INTERPROCEDURAL_OPTIMIZATION TRUE
# )