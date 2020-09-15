/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.certificatemanager.certificate;

import com.aws.iot.evergreen.cisclient.ConnectivityInfoItem;
import com.aws.iot.evergreen.cisclient.GetConnectivityInfoResponse;
import com.aws.iot.evergreen.testcommons.testutilities.EGExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith({MockitoExtension.class, EGExtension.class})
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class ConnectivityInfoTest extends EGExtension {

    @Test
    public void GIVEN_valid_connectivity_info_response_WHEN_parse_THEN_return_conn_info_cert_gen()
            throws UnknownHostException {
        List<ConnectivityInfoItem> items = new ArrayList<>();
        String dnsName = "somefake.host.com";
        String ipString = "192.168.1.1";
        items.add(new ConnectivityInfoItem(UUID.randomUUID().toString(), dnsName, "metadata", 1000));
        items.add(new ConnectivityInfoItem(UUID.randomUUID().toString(), ipString, "metadata", 2000));
        GetConnectivityInfoResponse response = new GetConnectivityInfoResponse();
        response.setConnectivityInfoItems(items);
        ConnectivityInfo connectivityInfo = new ConnectivityInfo(response);

        assertThat(connectivityInfo.dnsNames.size(), is(1));
        assertThat(connectivityInfo.dnsNames.get(0), is(dnsName));
        assertThat(connectivityInfo.ipAddresses.size(), is(1));
        assertThat(connectivityInfo.ipAddresses.get(0), is(InetAddress.getByName(ipString)));
    }

    // TODO: Add additional test cases
}
