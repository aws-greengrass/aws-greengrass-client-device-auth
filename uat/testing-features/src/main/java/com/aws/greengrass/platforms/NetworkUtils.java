/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.platforms;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public abstract class NetworkUtils {
    protected static final String[] MQTT_PORTS = {"8883", "443"};
    // 8888 and 8889 are used by the squid proxy which runs on a remote DUT
    // and need to disable access to test offline proxy scenarios
    protected static final String[] NETWORK_PORTS = {"443", "8888", "8889"};
    protected static final int[] GG_UPSTREAM_PORTS = {8883, 8443, 443};
    protected static final int SSH_PORT = 22;
    protected final List<Integer> blockedPorts = new ArrayList<>();

    public abstract void setBandwidth(int egressRateKbps) throws InterruptedException, IOException;

    public abstract void disconnectMqtt() throws InterruptedException, IOException;

    public abstract void recoverMqtt() throws InterruptedException, IOException;

    public abstract void disconnectNetwork() throws InterruptedException, IOException;

    public abstract void recoverNetwork() throws InterruptedException, IOException;

    public abstract boolean isPortBlocked(int port) throws InterruptedException, IOException;

    /**
     * Unblock all previously blocked ports.
     *
     * @throws IOException on IO errors
     * @throws InterruptedException when thread has been interrupted
     */
    public void recoverPorts() throws InterruptedException, IOException {
        for (Integer blockedPort : blockedPorts) {
            recoverPort(blockedPort);
        }
        blockedPorts.clear();
    }

    public abstract void disconnectPort(int port) throws InterruptedException, IOException;

    public abstract void recoverPort(int port) throws InterruptedException, IOException;

    public abstract void blockIngressFromPort(int port) throws InterruptedException, IOException;

    public abstract void recoverIngressFromPort(int port) throws InterruptedException, IOException;

    public void safelistAddressToPort(String address, int port) throws InterruptedException, IOException {
        // TODO add support for windows and mac
        throw new UnsupportedOperationException("safelist not supported");
    }

    public void removeSafelistAddressToPort(String address, int port) throws InterruptedException, IOException {
        // TODO add support for windows and mac
        throw new UnsupportedEncodingException("remove safelist not supported");
    }

    public void addLoopBackAddress(String ipAddress) throws IOException, InterruptedException {
        // TODO add support for mac
        throw new UnsupportedEncodingException("adding loop back address not supported");
    }

    public void removeLoopBackAddress(String ipAddress) throws IOException, InterruptedException {
        // TODO add support for mac
        throw new UnsupportedEncodingException("removeing loop back address not supported");
    }
}
