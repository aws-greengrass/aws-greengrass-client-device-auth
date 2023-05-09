/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include <stdio.h>                              /* fprintf() stderr stdout */
#include <stdarg.h>                             /* va_start() va_end() va_list */

#include "logger.h"                             /* functions declaration */
void logd(const char *fmt, ...) {
    va_list args;

    fprintf(stdout, "[DEBUG]: ");
    va_start(args, fmt);
    vfprintf(stdout, fmt, args);
    va_end(args);
}

void logw(const char *fmt, ...) {
    va_list args;

    fprintf(stdout, "[WARN]: ");
    va_start(args, fmt);
    vfprintf(stdout, fmt, args);
    va_end(args);
}

void loge(const char *fmt, ...) {
    va_list args;

    fprintf(stdout, "[ERROR]: ");
    va_start(args, fmt);
    vfprintf(stderr, fmt, args);
    va_end(args);
}
