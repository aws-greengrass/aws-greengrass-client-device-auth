/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.util;

import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
@ExtendWith({MockitoExtension.class, GGExtension.class})
public class ParseIPAddressTest {

    @Test
    public void GIVEN_valid_IPv4_WHEN_isValidIP_THEN_true_returned() {
        assertTrue(ParseIPAddress.isValidIP("192.168.0.1"));
        assertTrue(ParseIPAddress.isValidIP("127.0.0.1"));
        assertTrue(ParseIPAddress.isValidIP("127.0.0.1:80"));
    }

    @Test
    public void GIVEN_valid_IPv6_WHEN_isValidIP_THEN_true_returned() {
        assertTrue(ParseIPAddress.isValidIP("::1"));
        assertTrue(ParseIPAddress.isValidIP("[::1]:80"));
        assertTrue(ParseIPAddress.isValidIP("[32e::12f]:80"));
        assertTrue(ParseIPAddress.isValidIP("2605:2700:0:3::4713:93e3"));
        assertTrue(ParseIPAddress.isValidIP("[2605:2700:0:3::4713:93e3]:80"));
        assertTrue(ParseIPAddress.isValidIP("2001:db8:85a3:0:0:8a2e:370:7334"));
    }

    @Test
    public void GIVEN_invalid_IP_WHEN_isValidIP_THEN_false_returned() {
        assertFalse(ParseIPAddress.isValidIP("localhost"));
        assertFalse(ParseIPAddress.isValidIP("www.amazon.com"));
        assertFalse(ParseIPAddress.isValidIP("256.0.0.1"));
        assertFalse(ParseIPAddress.isValidIP("fe80:2030:31:24"));
        assertFalse(ParseIPAddress.isValidIP("56fe::2159:5bbc::6594"));
    }
}
