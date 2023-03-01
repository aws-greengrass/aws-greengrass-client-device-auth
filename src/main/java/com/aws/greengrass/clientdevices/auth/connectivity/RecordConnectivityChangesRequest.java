/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import lombok.Getter;

import java.util.Set;

@Getter
public class RecordConnectivityChangesRequest {
    private final ConnectivityInformationSource source;
    private final Set<HostAddress> connectivityInformation;

    public RecordConnectivityChangesRequest(ConnectivityInformationSource source, Set<HostAddress> connectivityInformation) {
        this.source = source;
        this.connectivityInformation = connectivityInformation;
    }
}
