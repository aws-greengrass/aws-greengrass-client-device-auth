/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

@Log4j2
@UtilityClass
public class IpcClientMainRouter {

    public static final String LOCAL_IPC_SUBSCRIBER = "LocalIpcSubscriber";
    public static final String LOCAL_IPC_PUBLISHER = "LocalIpcPublisher";
    public static final String COMPONENT_NAME_SYS_PROP = "componentName";

    /**
     * Main entry method.
     *
     * @param args arguments to main
     */
    public static void main(String[] args) {
        String operationName = System.getProperty(COMPONENT_NAME_SYS_PROP);

        if (LOCAL_IPC_SUBSCRIBER.equals(operationName)) {
            DaggerIpcComponents.create()
                               .getSubscriber()
                               .accept(args);
        } else if (LOCAL_IPC_PUBLISHER.equals(operationName)) {
            DaggerIpcComponents.create()
                               .getPublisher()
                               .accept(args);
        } else {
            log.error("Unsupported operation {}", operationName);
            System.exit(1);
        }
    }
}
