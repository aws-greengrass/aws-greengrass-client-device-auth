/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import lombok.Value;
import org.apache.http.conn.util.InetAddressUtils;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;

@Value
public class HostAddress {
    String host;

    public HostAddress(String host) {
        this.host = host;
    }

    public HostAddress(HostAddress other) {
        this.host = other.getHost();
    }

    public static HostAddress of(String hostAddress) {
        return new HostAddress(hostAddress);
    }

    public static HostAddress of(ConnectivityInfo connectivityInfo) {
        return HostAddress.of(connectivityInfo.hostAddress());
    }

    /**
     * Checks if the address represented by this HostAddress is valid.
     * @return true if valid
     */
    public boolean isValid() {
        return isIPAddress() || "localhost".equals(host);
    }

    /**
     * Checks if the connectivity information is a valid IPv4 or IPv6 address.
     *
     * @return true if valid IPv4 or IPv6 address, else false
     */
    public boolean isIPAddress() {
        return InetAddressUtils.isIPv4Address(host) || InetAddressUtils.isIPv6Address(host);
    }
}
