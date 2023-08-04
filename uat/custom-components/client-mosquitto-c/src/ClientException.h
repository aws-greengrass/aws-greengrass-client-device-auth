/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_EXCEPTION_H
#define MOSQUITTO_TEST_CLIENT_EXCEPTION_H

#include <exception>
#include <string>


/**
 * ClientException class represent generic exceptions of client.
 */
class ClientException : public std::exception {
public:

    /**
     * Constructor of ClientException.
     *
     * @param message the message of exception
     * @param code the error code
     */
    ClientException(const char * message, int code = 0)
        : m_message(message), m_code(code) {}
    ~ClientException() {}

    /**
     * Gets message of the exception.
     *
     * @return string with exception's message
     */
    const std::string & getMessage() const { return m_message; }

    /**
     * Gets error code of the exception.
     *
     * @return integer of error code of the exception
     */
    int getCode() const { return m_code; }

private:
    std::string m_message;
    int m_code;
};

#endif /* MOSQUITTO_TEST_CLIENT_EXCEPTION_H */
