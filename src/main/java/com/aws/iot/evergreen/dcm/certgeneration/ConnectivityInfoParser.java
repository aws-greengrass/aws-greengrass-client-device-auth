/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm.certgeneration;

import com.aws.iot.evergreen.cisclient.GetConnectivityInfoResponse;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public final class ConnectivityInfoParser {
    private ConnectivityInfoParser() {

    }

    /**
     * Converts CIS info into a format used in x509Certificate generation.
     *
     * @param info response from CIS
     * @return connectivity info used for cert generation
     */
    public static ConnectivityInfoForCertGen parseConnectivityInfo(GetConnectivityInfoResponse info) {
        List<InetAddress> ipAddresses = new ArrayList<>();
        List<String> dnsNames = new ArrayList<>();

        info.getConnectivityInfoItems().forEach(connectivityInfoItem -> {
            try {
                ipAddresses.add(InetAddress.getByName(connectivityInfoItem.getHostAddress()));
            } catch (UnknownHostException e) {
                dnsNames.add(connectivityInfoItem.getHostAddress());
            }
        });

        return new ConnectivityInfoForCertGen(ipAddresses, dnsNames);
    }
}
