cmake_minimum_required(VERSION 3.10.2)

project("aaudio_forwarder")

# Optimization flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -O3 -ffast-math -flto")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-unused-parameter")

# Strip symbols for release
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fuse-ld=lld -Wl,-O3 -Wl,--gc-sections,--strip-all -flto")

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

# LTO optimization
set_target_properties(aaudio_forwarder PROPERTIES
    INTERPROCEDURAL_OPTIMIZATION TRUE
)
