/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.certificatemanager.certificate;

import com.aws.iot.evergreen.cisclient.GetConnectivityInfoResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class ConnectivityInfo {
    final List<InetAddress> ipAddresses;
    final List<String> dnsNames;

    /**
     * Construct ConnectivityInfo from GetConnectivityInfoResponse.
     *
     * @param getConnectivityInfoResponse GetConnectivityInfo response object
     */
    public ConnectivityInfo(GetConnectivityInfoResponse getConnectivityInfoResponse) {
        this.ipAddresses = new ArrayList<>();
        this.dnsNames = new ArrayList<>();

        getConnectivityInfoResponse.getConnectivityInfoItems().forEach(connectivityInfoItem -> {
            try {
                ipAddresses.add(InetAddress.getByName(connectivityInfoItem.getHostAddress()));
            } catch (UnknownHostException e) {
                dnsNames.add(connectivityInfoItem.getHostAddress());
            }
        });
    }
}
