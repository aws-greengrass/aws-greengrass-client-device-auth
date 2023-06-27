/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.platforms.windows;

import com.aws.greengrass.platforms.NetworkUtils;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.utils.IoUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Log4j2
@AllArgsConstructor
public class NetworkUtilsWindows extends NetworkUtils {
    private static final String NETSH_ADD_RULE_FORMAT = "netsh advfirewall firewall "
            + "add rule name='%s' protocol=tcp dir=in action=block localport=%s && "
            + "netsh advfirewall firewall add rule name='%s' protocol=tcp dir=out action=block remoteport=%s";
    private static final String NETSH_DELETE_RULE_FORMAT = "netsh advfirewall firewall delete rule name='%s'";
    private static final String NETSH_GET_RULE_FORMAT = "netsh advfirewall firewall show rule name='%s'";
    private static final String NO_RULE_FOUND_STRING = "No rules match the specified criteria.";
    private static final String ADD_LOOPBACK_ADDR_FORMAT
        = "netsh interface ipv4 add address LOOPBACK %s 255.255.255.255";
    private static final String REMOVE_LOOPBACK_ADDR_FORMAT
        = "netsh interface ipv4 delete address LOOPBACK %s 255.255.255.255";
    private static final String COMMAND_FAILED_TO_RUN = "Command (%s) failed to run.";

    // Windows requires a name for every firewall name (can have duplicates)
    // Format: evergreen_uat_{PORT_NUMBER}
    // Example:
    // evergreen_uat_8883
    private static final String FIREWALL_RULE_NAME_FORMAT = "evergreen_uat_%s";

    /*
    Network commands for disconnecting entire network in case we want to
    disconnect the network like we do on Mac's network utils

    private static final String DISCONNECT_COMMAND = "ipconfig /release";
    private static final String CONNECT_COMMAND = "ipconfig /renew";
    */

    @Override
    public void disconnectMqtt() throws InterruptedException, IOException {
        blockPorts(MQTT_PORTS);
    }

    @Override
    public void recoverMqtt() throws InterruptedException, IOException {
        deleteRules(MQTT_PORTS);
    }

    @Override
    public void setBandwidth(int egressRateKbps) throws IOException, InterruptedException {
        // TODO: There are bandwidth tools for Windows but not native ones
        // NetLimiter is one but requires a license
        // NetBalancer also requires a license
        throw new UnsupportedOperationException("Bandwidth setting not available on Windows");
    }

    @Override
    public void disconnectNetwork() throws InterruptedException, IOException {
        blockPorts(NETWORK_PORTS);
    }

    @Override
    public void recoverNetwork() throws InterruptedException, IOException {
        deleteRules(NETWORK_PORTS);
    }

    @Override
    public void safelistAddressToPort(String address, int port) throws InterruptedException, IOException {
    }

    @Override
    public void removeSafelistAddressToPort(String address, int port) throws InterruptedException, IOException {
    }

    @Override
    public void disconnectPort(int port) throws InterruptedException, IOException {
        blockPorts(String.valueOf(port));
    }

    @Override
    public void recoverPort(int port) throws InterruptedException, IOException {
        deleteRules(String.valueOf(port));
    }

    @Override
    public void recoverIngressFromPort(int port) throws InterruptedException, IOException {
        //TBD
    }

    @Override
    public void blockIngressFromPort(int port) throws InterruptedException, IOException {
        // TBD
    }

    @Override
    public boolean isPortBlocked(int port) throws InterruptedException, IOException {
        String command = String.format(NETSH_GET_RULE_FORMAT, getRuleName(String.valueOf(port)));
        Process process = new ProcessBuilder().command("cmd", "/c", command).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            throw new RuntimeException(String.format("Command (%s) failed to run.", command));
        }

        return !(new String(IoUtils.toByteArray(process.getInputStream()), StandardCharsets.UTF_8)
            .contains(NO_RULE_FOUND_STRING));
    }

    @Override
    public void addLoopBackAddress(String ipAddress) throws IOException, InterruptedException {
        String command = String.format(ADD_LOOPBACK_ADDR_FORMAT, ipAddress);
        runCommandInTerminal(command, false);
    }

    @Override
    public void removeLoopBackAddress(String ipAddress) throws IOException, InterruptedException {
        String command = String.format(REMOVE_LOOPBACK_ADDR_FORMAT, ipAddress);
        runCommandInTerminal(command, true);
    }

    private String getRuleName(String port) {
        return String.format(FIREWALL_RULE_NAME_FORMAT, port);
    }

    private void deleteRules(String... ports) throws InterruptedException, IOException {
        for (String port : ports) {
            String ruleName = getRuleName(port);
            String command = String.format(NETSH_DELETE_RULE_FORMAT, ruleName);

            runCommandInTerminal(command, true);
        }
    }

    private void blockPorts(String... ports) throws InterruptedException,
            IOException {
        for (String port : ports) {
            blockedPorts.add(Integer.valueOf(port));
            String ruleName = getRuleName(port);
            // Create 2 rules (can have same name) one for in and one for out
            String command = String.format(NETSH_ADD_RULE_FORMAT,
                ruleName,
                port,
                ruleName,
                port);

            Process proc = new ProcessBuilder().command("cmd", "/c", command).start();
            boolean success = proc.waitFor(10, TimeUnit.SECONDS);

            if (!success || !proc.isAlive() && proc.exitValue() != 0) {
                log.error("Failed to run {}. Error {}. Out {}", command,
                        IoUtils.toUtf8String(proc.getErrorStream()),
                        IoUtils.toUtf8String(proc.getInputStream()));
                throw new RuntimeException(String.format("Command (%s) failed to run. Could not create rule %s",
                                                            command, ruleName));
            }
        }
    }

    private void runCommandInTerminal(String command, boolean ignoreError) throws IOException, InterruptedException {
        Process proc = new ProcessBuilder().command("cmd", "/c", command).start();
        boolean success = proc.waitFor(10, TimeUnit.SECONDS);
        if (!success || !proc.isAlive() && proc.exitValue() != 0 && !ignoreError) {
            log.error("Failed to run {}. Error {}. Out {}", command,
                    IoUtils.toUtf8String(proc.getErrorStream()),
                    IoUtils.toUtf8String(proc.getInputStream()));
            throw new RuntimeException(String.format(COMMAND_FAILED_TO_RUN, command));
        }
    }
}
