/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.cisclient;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectivityInfoItem {
    @JsonProperty("Id")
    String id;
    @JsonProperty("HostAddress")
    String hostAddress;
    @JsonProperty("Metadata")
    String metadata;
    @JsonProperty("PortNumber")
    int portNumber;
}
