/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_LOGGER_H
#define MOSQUITTO_TEST_CLIENT_LOGGER_H

#ifdef __cplusplus
extern "C" {
#endif

void logd(const char *fmt, ...);
void logw(const char *fmt, ...);
void loge(const char *fmt, ...);
void logn(const char *fmt, ...);
void logi(const char *fmt, ...);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* MOSQUITTO_TEST_CLIENT_LOGGER_H */
