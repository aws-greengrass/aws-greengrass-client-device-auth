#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

function(GRPC_GENERATE PROTO_SRCS GRPC_SRCS DEST)
    foreach(FIL ${ARGN})
        get_filename_component(ABS_FIL ${FIL} ABSOLUTE)
        get_filename_component(FIL_WE ${FIL} NAME_WE)
        get_filename_component(ABS_PATH ${ABS_FIL} PATH)

        list(APPEND ${PROTO_SRCS} "${DEST}/${FIL_WE}.pb.cc")
        list(APPEND ${PROTO_HDRS} "${DEST}/${FIL_WE}.pb.h")
        list(APPEND ${GRPC_SRCS} "${DEST}/${FIL_WE}.grpc.pb.cc")
        list(APPEND ${GRPC_HDRS} "${DEST}/${FIL_WE}.grpc.pb.h")

        add_custom_command(
            OUTPUT
                "${DEST}/${FIL_WE}.pb.cc"
                "${DEST}/${FIL_WE}.pb.h"
                "${DEST}/${FIL_WE}.grpc.pb.cc"
                "${DEST}/${FIL_WE}.grpc.pb.h"
            COMMAND
                ${_PROTOBUF_PROTOC}
                ARGS
                    --grpc_out "${DEST}"
                    --cpp_out "${DEST}"
                    -I "${ABS_PATH}"
                    --plugin=protoc-gen-grpc=${_GRPC_CPP_PLUGIN_EXECUTABLE}
                ${ABS_FIL}
            DEPENDS
                ${ABS_FIL}
                ${_GRPC_CPP_PLUGIN_EXECUTABLE}
            COMMENT "Running C++ gRPC compiler on ${FIL}"
            VERBATIM
        )
    endforeach()
    set(${PROTO_SRCS} ${${PROTO_SRCS}} PARENT_SCOPE)
    set(${GRPC_SRCS} ${${GRPC_SRCS}} PARENT_SCOPE)

endfunction()


set (CLIENT_SRCS
    ClientException.h

    GRPCException.h
    GRPCLib.cpp GRPCLib.h
    GRPCLink.cpp GRPCLink.h
    GRPCControlServer.cpp GRPCControlServer.h
    GRPCDiscoveryClient.cpp GRPCDiscoveryClient.h

    MqttException.h
    MqttLib.cpp MqttLib.h
    MqttConnection.cpp MqttConnection.h

    logger.cpp logger.h
    main.cpp
    )

find_package(Threads REQUIRED)
find_package(PkgConfig REQUIRED)
pkg_check_modules(Mosquitto IMPORTED_TARGET libmosquitto REQUIRED)


message(STATUS "Using gRPC ${GRPC_VERSION} via add_subdirectory (FetchContent).")
include(FetchContent)
FetchContent_Declare(
    gRPC
    GIT_REPOSITORY https://github.com/grpc/grpc
    GIT_TAG ${GRPC_VERSION}
)
set(FETCHCONTENT_QUIET OFF)
FetchContent_MakeAvailable(gRPC)

# Since FetchContent uses add_subdirectory under the hood, we can use
# the grpc targets directly from this build.
set(_PROTOBUF_LIBPROTOBUF libprotobuf)
set(_REFLECTION grpc++_reflection)
set(_PROTOBUF_PROTOC $<TARGET_FILE:protoc>)
set(_GRPC_GRPCPP grpc++)
if(CMAKE_CROSSCOMPILING)
    find_program(_GRPC_CPP_PLUGIN_EXECUTABLE grpc_cpp_plugin)
else()
    set(_GRPC_CPP_PLUGIN_EXECUTABLE $<TARGET_FILE:grpc_cpp_plugin>)
endif()


# List proto files
list(APPEND PROTO_FILES  ../../proto/mqtt_client_control.proto)

GRPC_GENERATE(
    PROTO_SRCS
    GRPC_SRCS
    ${CMAKE_CURRENT_BINARY_DIR}
    ../../../proto/mqtt_client_control.proto
    )



add_executable(mosquitto-test-client ${CLIENT_SRCS} ${PROTO_SRCS} ${GRPC_SRCS})
target_link_libraries(mosquitto-test-client PUBLIC ${_REFLECTION} ${_GRPC_GRPCPP} ${_PROTOBUF_LIBPROTOBUF} PkgConfig::Mosquitto)
target_include_directories(mosquitto-test-client PUBLIC ${CMAKE_CURRENT_BINARY_DIR})

install(TARGETS mosquitto-test-client RUNTIME DESTINATION "${CMAKE_INSTALL_SBINDIR}")
