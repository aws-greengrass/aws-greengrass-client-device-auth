/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;


public final class Result<T> {
    private final Status status;
    private final T value;

    private Exception err;

    private enum Status {
        OK, WARNING, ERROR
    }

    private Result(Status status, T value) {
        this.value = value;
        this.status = status;
    }

    private Result(Status status, T value, Exception e) {
        this.value = value;
        this.status = status;
        this.err = e;
    }

    public static <V> Result<V> ok(V value) {
        return new Result<>(Status.OK, value);
    }

    public static <V> Result<V> ok() {
        return ok(null);
    }

    public static <T, E extends Exception> Result<T> error(E value) {
        return new Result<>(Status.ERROR, null, value);
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

    public Exception getError() {
        return this.err;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public boolean isOk() {
        return status == Status.OK;
    }
}