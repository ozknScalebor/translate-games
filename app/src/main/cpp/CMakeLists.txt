# PS5 Remote Play support: builds chiaki-ng's protocol library (AGPL-3.0, third_party/chiaki-ng)
# and a small JNI bridge. Only the parts this app needs are configured: no audio, no GUI, no
# FFmpeg; video is decoded with the platform's hardware MediaCodec.
cmake_minimum_required(VERSION 3.22)
project(altyazi_remote C CXX)

set(CHIAKI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/chiaki-ng")
get_filename_component(CHIAKI_DIR "${CHIAKI_DIR}" ABSOLUTE)
if(NOT EXISTS "${CHIAKI_DIR}/lib/CMakeLists.txt")
	message(FATAL_ERROR "chiaki-ng sources missing. Run: git submodule update --init third_party/chiaki-ng "
		"and inside it: git submodule update --init third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl")
endif()

# Options read by chiaki-ng's lib/ and third-party/ CMake files (normally set by its root CMakeLists).
set(CHIAKI_ENABLE_ANDROID ON)            # skips the desktop-only libevent dependency
set(CHIAKI_ENABLE_FFMPEG_DECODER OFF)
set(CHIAKI_ENABLE_PI_DECODER OFF)
set(CHIAKI_LIB_ENABLE_OPUS OFF)          # audio is not needed: the TV plays the game sound
set(CHIAKI_LIB_ENABLE_MBEDTLS OFF)
set(CHIAKI_LIB_OPENSSL_EXTERNAL_PROJECT ON CACHE BOOL "" FORCE)
set(CHIAKI_LIB_JSONC_EXTERNAL_PROJECT ON)
set(CHIAKI_LIB_MINIUPNPC_EXTERNAL_PROJECT ON)
set(CHIAKI_ENABLE_STEAM_SHORTCUT OFF)
set(CHIAKI_USE_SYSTEM_JERASURE OFF)
set(CHIAKI_USE_SYSTEM_NANOPB OFF)
set(CHIAKI_USE_SYSTEM_CURL OFF)
set(CHIAKI_IS_SWITCH OFF)

# libcurl is linked by chiaki-lib for Remote Play over the internet, which this app does not use;
# build it as small as possible and without TLS or optional system libraries.
set(CURL_ENABLE_SSL OFF CACHE BOOL "" FORCE)
set(CURL_USE_OPENSSL OFF CACHE BOOL "" FORCE)
set(CURL_USE_LIBPSL OFF CACHE BOOL "" FORCE)
set(CURL_USE_LIBSSH2 OFF CACHE BOOL "" FORCE)
set(CURL_ZLIB OFF CACHE BOOL "" FORCE)
set(CURL_BROTLI OFF CACHE BOOL "" FORCE)
set(CURL_ZSTD OFF CACHE BOOL "" FORCE)
set(USE_NGHTTP2 OFF CACHE BOOL "" FORCE)
set(USE_LIBIDN2 OFF CACHE BOOL "" FORCE)
set(ENABLE_ARES OFF CACHE BOOL "" FORCE)
set(CURL_DISABLE_LDAP ON CACHE BOOL "" FORCE)

# Hosts without NDK support (local checks) may use the system OpenSSL instead of building it.
if(NOT ANDROID AND ALTYAZI_SYSTEM_OPENSSL)
	set(CHIAKI_LIB_OPENSSL_EXTERNAL_PROJECT OFF CACHE BOOL "" FORCE)
endif()

list(APPEND CMAKE_MODULE_PATH "${CHIAKI_DIR}/cmake")
set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(PythonInterp 3 REQUIRED) # nanopb's code generator

if(CHIAKI_LIB_OPENSSL_EXTERNAL_PROJECT)
	include(OpenSSLExternalProject)
endif()

add_subdirectory("${CHIAKI_DIR}/third-party" chiaki-third-party)

add_definitions(-DCHIAKI_VERSION_MAJOR=1 -DCHIAKI_VERSION_MINOR=10 -DCHIAKI_VERSION_PATCH=0 -DCHIAKI_VERSION=\"1.10.0\")

include(JsoncExternalProject)
include(MiniupnpcExternalProject)

add_subdirectory("${CHIAKI_DIR}/lib" chiaki-lib)

# Every wait on a socket goes through the stop pipe; use the poll()-based copy so descriptors above
# FD_SETSIZE never abort the app.
get_target_property(CHIAKI_LIB_SOURCES chiaki-lib SOURCES)
list(REMOVE_ITEM CHIAKI_LIB_SOURCES "src/stoppipe.c")
list(APPEND CHIAKI_LIB_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/chiaki-overrides/stoppipe.c")
set_property(TARGET chiaki-lib PROPERTY SOURCES ${CHIAKI_LIB_SOURCES})

if(NOT ANDROID AND ALTYAZI_SYSTEM_OPENSSL)
	# Host-only configuration check: compile the internet hole-punching code along its Android
	# path, which does not need libevent.
	set_source_files_properties("${CHIAKI_DIR}/lib/src/remote/holepunch.c"
		DIRECTORY "${CHIAKI_DIR}/lib" PROPERTIES COMPILE_DEFINITIONS "__ANDROID__")
endif()

if(ANDROID)
	add_library(altyazi-remote SHARED
		remote-jni.c
		video-decoder.c
		log.c)
	target_link_libraries(altyazi-remote chiaki-lib)

	find_library(ANDROID_LIB_LOG log)
	find_library(ANDROID_LIB_MEDIANDK mediandk)
	find_library(ANDROID_LIB_ANDROID android)
	target_link_libraries(altyazi-remote "${ANDROID_LIB_LOG}" "${ANDROID_LIB_MEDIANDK}" "${ANDROID_LIB_ANDROID}")
	# Android 15+ devices may use 16 KB memory pages. Symbols of the statically linked libraries
	# (OpenSSL, curl, ...) stay private, so they never clash with other native libraries in the app.
	target_link_options(altyazi-remote PRIVATE
		"-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384" "-Wl,--exclude-libs,ALL")
endif()
