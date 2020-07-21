/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class VersionAndNetworkUpdateHandler implements VersionAndNetworkUpdateManager.UpdateHandler {
    // TODO: Do we need this?
    private final ExecutorService executorServiceForCertGenWorkFlow;

    public VersionAndNetworkUpdateHandler(ExecutorService executorServiceForCertGenWorkFlow) {
        this.executorServiceForCertGenWorkFlow = executorServiceForCertGenWorkFlow;
    }

    @Override
    public CompletableFuture<Void> handleServiceVersionUpdate(String service) {
        return CompletableFuture.runAsync(() -> {
            // Run the cert gen workflow

        }, executorServiceForCertGenWorkFlow);
    }

    @Override
    public void handleNetworkReconnect() {

    }
}
