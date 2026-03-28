cmake_minimum_required(VERSION 3.22.1)
project(tor_x_core)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Rust library path
set(RUST_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/rust/target/release")

# Add Rust library
add_library(tor_x_core SHARED IMPORTED)
set_target_properties(tor_x_core PROPERTIES
    IMPORTED_LOCATION "${RUST_LIB_DIR}/libtorxcore.so"
)

# JNI bridge source
add_library(torx_jni SHARED
    src/main/cpp/jni_bridge.cpp
)

target_link_libraries(torx_jni
    tor_x_core
    android
    log
)

# Include directories
target_include_directories(torx_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp
)
