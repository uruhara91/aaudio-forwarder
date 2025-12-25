cmake_minimum_required(VERSION 3.18.0)
project(aaudio_forwarder)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -flto")

find_library(AAUDIO_LIB aaudio)
find_library(LOG_LIB log)

# Perhatikan: Tidak perlu pakai 'src/main/cpp/' lagi karena file ini sudah di situ
add_library(aaudio_forwarder SHARED
    audio_capture.cpp
    network_sender.cpp
    main.cpp
)

# Include header dari folder saat ini (.)
target_include_directories(aaudio_forwarder PRIVATE 
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(aaudio_forwarder
    ${AAUDIO_LIB}
    ${LOG_LIB}
    android
)