/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import lombok.Getter;

public enum ConnectivityInformationSource {
    CONFIGURATION("configuration"),
    CONNECTIVITY_INFORMATION_SERVICE("connectivity-information-service");

    @Getter
    private final String source;

    ConnectivityInformationSource(String source) {
        this.source = source;
    }
}
