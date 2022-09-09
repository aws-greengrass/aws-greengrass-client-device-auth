/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api.usecases;

import com.aws.greengrass.util.CrashableFunction;

// Delegates to CrashableFunction but provides a domain rich alias
public interface UseCase<R, D, E extends Exception> extends CrashableFunction<D, R, E> {
}
