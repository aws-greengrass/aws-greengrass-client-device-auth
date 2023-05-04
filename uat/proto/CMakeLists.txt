add_library(protocol)
target_include_directories(protocol
  PUBLIC
  .
  ${CMAKE_CURRENT_BINARY_DIR}
  ${Protobuf_INCLUDE_DIRS}
)

find_package(Protobuf CONFIG REQUIRED)
find_package(gRPC CONFIG REQUIRED)
target_link_libraries(protocol ${Protobuf_LIBRARIES})
get_target_property(grpc_cpp_plugin_location gRPC::grpc_cpp_plugin LOCATION)

protobuf_generate(
    TARGET
        protocol
    LANGUAGE
        CPP
    PROTOS
        mqtt_client_control.proto
    )

protobuf_generate(
    TARGET
        protocol
    PLUGIN
        "protoc-gen-grpc=${grpc_cpp_plugin_location}"
    LANGUAGE
        grpc
    GENERATE_EXTENSIONS
        .grpc.pb.h .grpc.pb.cc
    PROTOS
        mqtt_client_control.proto
)
