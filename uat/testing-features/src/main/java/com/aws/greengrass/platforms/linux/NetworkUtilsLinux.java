
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.platforms.linux;

import com.aws.greengrass.platforms.NetworkUtils;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
public class NetworkUtilsLinux extends NetworkUtils {
    private static final String DISABLE_OPTION = "--delete";
    private static final String APPEND_OPTION = "-A";
    private static final String IPTABLES_DROP_DPORT_EXTERNAL_ONLY_COMMAND_STR
            = "iptables %s INPUT -p tcp -s localhost --dport %s -j ACCEPT && "
            + "iptables %s INPUT -p tcp --dport %s -j DROP && "
            + "iptables %s OUTPUT -p tcp -d localhost --dport %s -j ACCEPT && "
            + "iptables %s OUTPUT -p tcp --dport %s -j DROP";

    @Override
    public void disconnectMqtt() throws InterruptedException, IOException {
        modifyMqttConnection(APPEND_OPTION);
    }

    @Override
    public void recoverMqtt() throws InterruptedException, IOException {
        modifyMqttConnection(DISABLE_OPTION);
    }

    private void modifyMqttConnection(String action) throws IOException, InterruptedException {
        for (String port : MQTT_PORTS) {
            new ProcessBuilder().command(
                    "sh", "-c", String.format(IPTABLES_DROP_DPORT_EXTERNAL_ONLY_COMMAND_STR,
                            action, port, action, port, action, port, action, port)
                    ).start().waitFor(2, TimeUnit.SECONDS);
        }
    }
}
