cmake_minimum_required(VERSION 3.10)
project(IchnoisCore-Sample LANGUAGES C CXX)

add_library(native-sample SHARED src/main/cpp/native-sample.cpp)

find_package(ichnois-native-ndk REQUIRED CONFIG)

find_library(LOG_LIB log)

target_link_libraries(native-sample PRIVATE
    ${LOG_LIB}
    ichnois-native-ndk::ichnois-android
    ichnois-native-ndk::ichnois
)

# Android 15: Support 16KB page sizes
# see https://developer.android.com/guide/practices/page-sizes
target_link_options(native-sample PRIVATE "-Wl,-z,max-page-size=16384")
