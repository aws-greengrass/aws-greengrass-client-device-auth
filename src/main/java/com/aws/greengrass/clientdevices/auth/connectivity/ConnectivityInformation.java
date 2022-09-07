/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectivityInformation {
    private static final Pattern IPV4_PAT = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)(?::(\\d+)){0,1}$");
    private static final Pattern IPV6_DOUBL_COL_PAT = Pattern.compile(
            "^\\[{0,1}([0-9a-f:]*)::([0-9a-f:]*)(?:\\]:(\\d+)){0,1}$");
    private static String ipv6Pattern;
    private static final Pattern IPV6_PAT = Pattern.compile(ipv6Pattern);

    static {
        ipv6Pattern = "^\\[{0,1}";
        for (int i = 1; i <= 7; i++) {
            ipv6Pattern += "([0-9a-f]+):";
        }
        ipv6Pattern += "([0-9a-f]+)(?:\\]:(\\d+)){0,1}$";
    }

    private final String hostAddress;


    public ConnectivityInformation(String hostAddress) {
        this.hostAddress = hostAddress;
    }

    public static ConnectivityInformation fromConnectivityInfo(ConnectivityInfo connectivityInfo) {
        return new ConnectivityInformation(connectivityInfo.hostAddress());
    }

    // TODO: Need to implement equals method so de-dup works as intended when adding multiple
    // connectivity information objects to a set

    /**
     * Checks if the connectivity information is a valid IPv4 or IPv6 address.
     *
     * @return true if valid IPv4 or IPv6 address, else false
     */
    public boolean isIPAddress() {
        return isValidIP(hostAddress);
    }

    /**
     * Checks if the connectivity information is a valid DNS name.
     *
     * @return false if valid IPv4 or IPv6 address, else true
     */
    public boolean isDNSName() {
        return !isIPAddress();
    }


    private static boolean isValidIP(String host) {
        //  IPV4
        Matcher ipv4Matcher = IPV4_PAT.matcher(host);
        if (ipv4Matcher.matches()) {
            for (int i = 1; i <= 4; i++) {
                if (!isValidHex4(ipv4Matcher.group(i))) {
                    return false;
                }
            }
            return true;
        }

        //  IPV6, double colon
        Matcher ipv6DoubleColonMatcher = IPV6_DOUBL_COL_PAT.matcher(host);
        if (ipv6DoubleColonMatcher.matches()) {
            String p1 = ipv6DoubleColonMatcher.group(1);
            if (p1.isEmpty()) {
                p1 = "0";
            }
            String p2 = ipv6DoubleColonMatcher.group(2);
            if (p2.isEmpty()) {
                p2 = "0";
            }
            host =  p1 + getZero(8 - numCount(p1) - numCount(p2)) + p2;
            if (ipv6DoubleColonMatcher.group(3) != null) {
                host = "[" + host + "]:" + ipv6DoubleColonMatcher.group(3);
            }
        }

        //  IPV6
        Matcher ipv6Matcher = IPV6_PAT.matcher(host);
        if (ipv6Matcher.matches()) {
            for (int i = 1; i <= 8; i++) {
                if (!isValidHex6(ipv6Matcher.group(i))) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private static int numCount(String s) {
        return s.split(":").length;
    }

    private static String getZero(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append(':');
        while (count > 0) {
            sb.append("0:");
            count--;
        }
        return sb.toString();
    }

    private static boolean isValidHex4(String s) {
        int val = Integer.parseInt(s);
        return val >= 0 && val <= 255;
    }

    private static boolean isValidHex6(String s) {
        int val = Integer.parseInt(s, 16);
        return val >= 0 && val <= 65_536;
    }
}
