cmake_minimum_required(VERSION 3.22.1)

project("sound_service")

add_library(
    sound_service
    SHARED
    jni_bridge.cpp
    network_client.cpp
)

target_compile_options(sound_service PRIVATE 
    -O3 
    -flto
    -ffast-math 
    -fno-plt 
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -Wall
    -fexceptions
    -fomit-frame-pointer
    -ffunction-sections
    -fdata-sections
    -fno-unwind-tables
    -fno-asynchronous-unwind-tables
)

target_link_options(sound_service PRIVATE
    -flto
    -fuse-ld=lld
    -Wl,--gc-sections
    -Wl,--strip-all
)

set_target_properties(sound_service PROPERTIES
    CXX_STANDARD 20
    CXX_STANDARD_REQUIRED ON
)

find_library( 
        log-lib
        log
)

target_link_libraries( 
        sound_service
        ${log-lib}
)