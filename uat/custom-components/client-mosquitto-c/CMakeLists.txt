CMAKE_MINIMUM_REQUIRED (VERSION 3.16.3)
PROJECT(mosquitto-test-client
    VERSION 0.0.1
    DESCRIPTION "Mosquitto test client"
    LANGUAGES C CXX)

add_subdirectory(../../proto protocol)
add_subdirectory(src)
