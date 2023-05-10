/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include <stdio.h>                              /* fprintf() stderr stdout */
#include <stdarg.h>                             /* va_start() va_end() va_list */

#include "logger.h"                             /* functions declaration */

static void logall(const char * tag, FILE * stream, const char *fmt, va_list ap) {
    fprintf(stream, "[%s]: ", tag);
    vfprintf(stream, fmt, ap);
}

void logd(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    logall("DEBUG", stdout, fmt, args);
    va_end(args);
}

void logw(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    logall("WARN ", stdout, fmt, args);
    va_end(args);
}

void loge(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    logall("ERROR", stderr, fmt, args);
    va_end(args);
}

void logn(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    logall("NOTIC", stdout, fmt, args);
    va_end(args);
}

void logi(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    logall("INFO ", stdout, fmt, args);
    va_end(args);
}
