/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm.certgeneration;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.net.InetAddress;
import java.util.List;

@Data
@AllArgsConstructor
public class ConnectivityInfoForCertGen {
    final List<InetAddress> ipAddresses;
    final List<String> dnsNames;
}
