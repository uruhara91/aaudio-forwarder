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
    -pipe
    -ffast-math
    -fno-plt
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -fno-exceptions
    -fno-rtti
    -fomit-frame-pointer
    -ffunction-sections
    -fdata-sections
    -fno-unwind-tables
    -fno-asynchronous-unwind-tables
    -flto
)

target_link_options(sound_service PRIVATE
    -fuse-ld=lld
    -Wl,-O3
    -Wl,--gc-sections
    -Wl,--sort-common
    -Wl,--as-needed
    -Wl,--icf=all
    -Wl,--strip-all
    -Wl,-z,relro
    -Wl,-z,now
    -flto
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