set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -g -O0")
set(CMAKE_CXX_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -g -O0")

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

find_package(PkgConfig REQUIRED)
pkg_check_modules(Mosquitto IMPORTED_TARGET libmosquitto REQUIRED)

find_package(Protobuf CONFIG REQUIRED)
find_package(gRPC CONFIG REQUIRED)

add_executable(mosquitto-test-client ${CLIENT_SRCS})
target_link_libraries(mosquitto-test-client PUBLIC protocol ${Protobuf_LIBRARIES} gRPC::grpc++ PkgConfig::Mosquitto grpc++_reflection)

install(TARGETS mosquitto-test-client RUNTIME DESTINATION "${CMAKE_INSTALL_SBINDIR}")
