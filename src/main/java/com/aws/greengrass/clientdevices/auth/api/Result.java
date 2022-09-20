/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;


public final class Result<T> {
    private final Status status;
    private final T value;

    private enum Status {
        OK, WARNING, ERROR
    }

    private Result(Status status, T value) {
        this.value = value;
        this.status = status;
    }

    public static <V> Result<V> ok(V value) {
        return new Result<>(Status.OK, value);
    }

    public static <V> Result<V> ok() {
        return ok(null);
    }

    public static <E extends Exception> Result<E> error(E value) {
        return new Result<>(Status.ERROR, value);
    }

    public static <E extends Exception> Result<E> warning(E value) {
        return new Result<>(Status.WARNING, value);
    }

    public static <V> Result<V> warning() {
        return new Result<>(Status.WARNING, null);
    }


    public T get() {
        return value;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public boolean isOk() {
        return status == Status.OK;
    }
}