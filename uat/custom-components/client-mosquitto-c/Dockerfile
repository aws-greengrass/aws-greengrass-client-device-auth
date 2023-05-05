FROM ubuntu:22.04 AS builder
LABEL Description="Mosquitto MQTT client build environment"

ENV HOME /root
SHELL ["/bin/bash", "-c"]
ENV REQUIREMENTS \
    build-essential \
    gcc \
    cmake \
    git \
    autoconf \
    libtool \
    pkg-config \
    libmosquitto-dev

RUN apt-get clean && \
    apt-get update && \
    apt-get -y --no-install-recommends install $REQUIREMENTS && \
    apt-get clean

COPY src            /usr/src/aws-greengrass-client-device-auth/uat/custom-components/client-mosquitto-c/src
COPY tools          /usr/src/aws-greengrass-client-device-auth/uat/custom-components/client-mosquitto-c/tools
COPY CMakeLists.txt /usr/src/aws-greengrass-client-device-auth/uat/custom-components/client-mosquitto-c/CMakeLists.txt
COPY proto          /usr/src/aws-greengrass-client-device-auth/uat/proto


RUN set -ex; \
    cd /usr/src/aws-greengrass-client-device-auth/uat/custom-components/client-mosquitto-c && \
    CXXFLAG="-Wall -Wextra -O2" cmake -Bbuild -H. && \
    cmake --build build -j 4 --target all && \
    cmake --install build --prefix "/usr/local"

# Define the default command
CMD ["bash"]
