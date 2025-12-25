cmake_minimum_required(VERSION 3.18)
project(aaudio_forwarder)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimasi untuk performa maksimal
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -flto")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math -flto")

# AAudio library (Android 8.0+)
find_library(AAUDIO_LIB aaudio)
find_library(LOG_LIB log)

# Source files (relative to app/)
set(SOURCES
    src/main/cpp/audio_capture.cpp
    src/main/cpp/network_sender.cpp
    src/main/cpp/main.cpp
)

# Build native library
add_library(aaudio_forwarder SHARED ${SOURCES})

target_include_directories(aaudio_forwarder PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp
)

target_link_libraries(aaudio_forwarder
    ${AAUDIO_LIB}
    ${LOG_LIB}
    android
)
