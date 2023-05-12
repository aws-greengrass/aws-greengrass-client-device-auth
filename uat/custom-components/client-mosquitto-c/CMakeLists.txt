CMAKE_MINIMUM_REQUIRED (VERSION 3.15 FATAL_ERROR)
PROJECT(mosquitto-test-client
    VERSION 0.0.1
    DESCRIPTION "Mosquitto test client"
    LANGUAGES CXX)

set(GRPC_VERSION v1.54.1)
set(CMAKE_CXX_STANDARD 14)

add_subdirectory(src)
