/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MqttBrokers {
    private final Map<String, List<ConnectivityInfo>> brokers = new HashMap<>();

    @Data
    @AllArgsConstructor
    public static class ConnectivityInfo {
        private String host;
        private Integer port;
        private List<String> caList;
    }


    public void setConnectivityInfo(String brokerId, List<ConnectivityInfo> info) {
        brokers.put(brokerId, info);
    }

    public List<ConnectivityInfo> getConnectivityInfo(String brokerId) {
        return brokers.get(brokerId);
    }
}
