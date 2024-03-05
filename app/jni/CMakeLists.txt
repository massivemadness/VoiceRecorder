cmake_minimum_required(VERSION 3.10.2 FATAL_ERROR)
project(tgx VERSION 1.0.0 LANGUAGES C CXX)

# == Include ==

include("${CMAKE_HOME_DIRECTORY}/cmake/ReadVariables.cmake")
include("${CMAKE_HOME_DIRECTORY}/cmake/Prefix.cmake")
include("${CMAKE_HOME_DIRECTORY}/cmake/Join.cmake")

# == Dirs ==

set(THIRDPARTY_DIR "${CMAKE_HOME_DIRECTORY}/third_party")
set(TGX_ROOT_DIR "${CMAKE_HOME_DIRECTORY}/../..")

# == Constants ==

if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(IS_ARM_FAMILY yes)
else()
    set(IS_ARM_FAMILY no)
endif()

message(STATUS "Building native library... \
        CMAKE_BUILD_TYPE: ${CMAKE_BUILD_TYPE} \
        IS_ARM_FAMILY: ${IS_ARM_FAMILY}")

# == CMake global settings ==

set(CMAKE_SKIP_RPATH ON)
set(CMAKE_BUILD_WITH_INSTALL_RPATH ON)

if (${IS_ARM_FAMILY})
    enable_language(ASM)
else()
    enable_language(ASM_NASM)
endif()
set(ORIGINAL_CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS}")

set(EXCLUDE_LIBS
        libopus.a
        libogg.a
        libopusfile.a
        )
Join(EXCLUDE_LIBS "${EXCLUDE_LIBS}" ",")
list(APPEND ADD_LINKER_FLAGS
        -Wl,--exclude-libs,${EXCLUDE_LIBS}
        )
Join(ADD_LINKER_FLAGS "${ADD_LINKER_FLAGS}" " ")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${ADD_LINKER_FLAGS}")

# == Libraries ==

# opus
include("${CMAKE_HOME_DIRECTORY}/BuildOpus.cmake")

# ogg
include("${CMAKE_HOME_DIRECTORY}/BuildOgg.cmake")

# opusfile
include("${CMAKE_HOME_DIRECTORY}/BuildOpusfile.cmake")

# == Target ==

set(NATIVE_LIB "tgxjni")
add_library(${NATIVE_LIB} SHARED
        voice.c
        )
target_include_directories(${NATIVE_LIB} PRIVATE
        "${THIRDPARTY_DIR}"
        .
        )

target_compile_definitions(${NATIVE_LIB} PUBLIC
        SOCKLEN_T=socklen_t
        LOCALE_NOT_USED

        DISABLE_IMPORTGL
        BSD=1
        AVOID_TABLES
        ANDROID_TILE_BASED_DECODE
        ANDROID_ARMV6_IDCT
        __STDC_CONSTANT_MACROS
        )
target_compile_options(${NATIVE_LIB} PUBLIC
        -Wall -Werror -Wno-deprecated-declarations
        -Wno-macro-redefined -Wno-unused-variable
        -fno-math-errno -fno-strict-aliasing -funroll-loops
        -ffast-math
        -fno-rtti
        )

# == Linking dependencies ==

target_link_libraries(${NATIVE_LIB}
        ogg
        opusfile
        opus
        )

target_link_libraries(${NATIVE_LIB}
        android
        cpufeatures
        )

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()