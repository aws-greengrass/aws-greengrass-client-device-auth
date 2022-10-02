/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;


import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Result<T> {
    private final Status status;
    private final T value;
    private Exception error;


    private enum Status {
        OK, WARNING, ERROR
    }

    private Result(Status status, T value) {
        this.value = value;
        this.status = status;
    }

    private Result(Status status, T value, Exception err) {
        this(status, value);
        this.error = err;
    }

    public static <V> Result<V> ok(V value) {
        return new Result<>(Status.OK, value);
    }

    public static <V> Result<V> ok() {
        return ok(null);
    }

    public static <T, E extends Exception> Result<T> error(E err) {
        return new Result<>(Status.ERROR, null, err);
    }

    public static <T, E extends Exception> Result<T> error(T value, E err) {
        return new Result<>(Status.ERROR, value, err);
    }


    public static <E extends Exception> Result<E> warning(E err) {
        return new Result<>(Status.WARNING, null, err);
    }

    public static <V> Result<V> warning() {
        return new Result<>(Status.WARNING, null);
    }

    /**
     * If a value is present in this Result, returns the value, otherwise throws NoSuchElementException.
     *
     * @throws NoSuchElementException when no value present
     */
    public T get() {
        return value;
    }

    public Exception getError() {
        return this.error;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    /**
     * Calls a consumer with the stored value if the Result isOk.
     * @param consumer a consumer function.
     */
    public void ifOk(Consumer<T> consumer) {
        if (isOk()) {
            consumer.accept(value);
        }
    }

    public <R> Result<R> map(Function<T, R> fn) {
       R value = Objects.isNull(this.value) ? null : fn.apply(this.value);
       return new Result<>(this.status, value, this.error);
    }
}