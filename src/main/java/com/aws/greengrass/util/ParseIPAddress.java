package com.aws.greengrass.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParseIPAddress {
    private static final Pattern IPV4_PAT = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)(?::(\\d+)){0,1}$");
    private static final Pattern IPV6_DOUBL_COL_PAT = Pattern.compile(
            "^\\[{0,1}([0-9a-f:]*)::([0-9a-f:]*)(?:\\]:(\\d+)){0,1}$");
    private static String ipv6Pattern;

    static {
        ipv6Pattern = "^\\[{0,1}";
        for (int i = 1; i <= 7; i++) {
            ipv6Pattern += "([0-9a-f]+):";
        }
        ipv6Pattern += "([0-9a-f]+)(?:\\]:(\\d+)){0,1}$";
    }

    private static final Pattern IPV6_PAT = Pattern.compile(ipv6Pattern);

    private ParseIPAddress() {
    }

    /**
     * Checks if the host string is a valid IPv4 or IPv6 address.
     *
     * @param host Host address string
     * @return true if valid IPv4 or IPv6 address, else false
     */
    public static boolean isValidIP(String host) {
        //  IPV4
        Matcher ipv4Matcher = IPV4_PAT.matcher(host);
        if (ipv4Matcher.matches()) {
            return true;
        }

        //  IPV6, double colon
        Matcher ipv6DoubleColonMatcher = IPV6_DOUBL_COL_PAT.matcher(host);
        if (ipv6DoubleColonMatcher.matches()) {
            return true;
        }

        //  IPV6
        Matcher ipv6Matcher = IPV6_PAT.matcher(host);
        return ipv6Matcher.matches();
    }
}
