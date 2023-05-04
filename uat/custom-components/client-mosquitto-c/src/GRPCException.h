/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_GRPCEXCEPTION_H
#define MOSQUITTO_TEST_CLIENT_GRPCEXCEPTION_H

#include "ClientException.h"


/**
 * MqttException class.
 */
class GRPCException : public ClientException {
public:
    GRPCException(const char * message, int code = 0)
        :ClientException(message, code) {}
    ~GRPCException() {}
};

#endif /* MOSQUITTO_TEST_CLIENT_GRPCEXCEPTION_H */
