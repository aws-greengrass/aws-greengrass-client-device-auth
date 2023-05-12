/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import dagger.Component;

import javax.inject.Singleton;

@Component
@Singleton
public interface IpcComponents {

    LocalIpcSubscriber getSubscriber();
}
