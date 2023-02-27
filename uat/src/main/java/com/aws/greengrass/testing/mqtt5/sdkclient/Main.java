/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.sdkclient;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class of application.
 */
@SuppressWarnings("PMD.UseUtilityClass")
public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws InterruptedException {
        logger.log(Level.INFO, "Hello, it is a test client");
        Thread.sleep(30_000);
    }
}
