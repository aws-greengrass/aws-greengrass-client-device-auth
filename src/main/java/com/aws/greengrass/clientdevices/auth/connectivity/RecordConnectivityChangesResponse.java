/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import lombok.Getter;

import java.util.Set;

@Getter
public class RecordConnectivityChangesResponse {
    private final Set<HostAddress> addedHostAddresses;
    private final Set<HostAddress> removedHostAddresses;

    public RecordConnectivityChangesResponse(Set<HostAddress> addedHostAddresses,
                                             Set<HostAddress> removedHostAddresses) {
        this.addedHostAddresses = addedHostAddresses;
        this.removedHostAddresses = removedHostAddresses;
    }

    public boolean didChange() {
        return !(addedHostAddresses.isEmpty() && removedHostAddresses.isEmpty());
    }
}
