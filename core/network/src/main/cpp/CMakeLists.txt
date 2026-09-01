cmake_minimum_required(VERSION 3.22.1)

project(networktoolbox_traceroute)

add_library(
    networktoolbox_traceroute
    SHARED
    udp_traceroute_probe.cpp
)

find_library(log-lib log)

target_compile_features(networktoolbox_traceroute PRIVATE cxx_std_17)
target_compile_options(networktoolbox_traceroute PRIVATE -Wall -Wextra -Werror)
target_link_options(networktoolbox_traceroute PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(networktoolbox_traceroute ${log-lib})
