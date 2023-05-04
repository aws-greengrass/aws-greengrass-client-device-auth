/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_EXCEPTION_H
#define MOSQUITTO_TEST_CLIENT_EXCEPTION_H

#include <exception>
#include <string>


/**
 * ClientException class.
 */
class ClientException : public std::exception {
public:
    ClientException(const char * message, int code = 0)
        : m_message(message), m_code(code) {}
    ~ClientException() {}

    const std::string & getMessage() const { return m_message; }
    int getCode() const { return m_code; }

private:
    std::string m_message;
    int m_code;
};

#endif /* MOSQUITTO_TEST_CLIENT_EXCEPTION_H */
