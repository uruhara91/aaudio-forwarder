cmake_minimum_required(VERSION 3.22.1)

project("aaudio_forwarder")

add_library(
        aaudio_forwarder
        SHARED
        main.cpp
        network_sender.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        aaudio_forwarder
        ${log-lib}
        android)

target_link_options(aaudio_forwarder PRIVATE
    "-flto"
    "-Wl,--gc-sections"
    "-Wl,--icf=all"
    "-Wl,-O3"
)