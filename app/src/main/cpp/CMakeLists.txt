cmake_minimum_required(VERSION 3.10.2)

project("sound_service")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -O3 -ffast-math -fno-plt -flto")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-stack-protector -fvisibility=hidden -fvisibility-inlines-hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-unused-parameter")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections,--strip-all -flto")

add_library(
    sound_service
    SHARED
    jni_bridge.cpp
    network_client.cpp
)

find_library( 
        log-lib
        log
)

target_link_libraries( 
        sound_service
        ${log-lib}
)