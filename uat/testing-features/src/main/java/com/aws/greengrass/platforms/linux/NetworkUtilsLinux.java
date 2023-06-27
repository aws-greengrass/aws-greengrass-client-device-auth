/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.platforms.linux;

import com.aws.greengrass.platforms.NetworkUtils;
import com.aws.greengrass.platforms.PlatformResolver;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.utils.IoUtils;

import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
public class NetworkUtilsLinux extends NetworkUtils {
    private static final String ENABLE_OPTION = "--insert";
    private static final String DISABLE_OPTION = "--delete";
    private static final String APPEND_OPTION = "-A";
    private static final String IPTABLE_COMMAND_BLOCK_INGRESS_STR =
            "sudo iptables %s INPUT -p tcp --sport %s -j REJECT";
    private static final String IPTABLE_COMMAND_STR = "sudo iptables %s OUTPUT -p tcp --dport %s -j REJECT && "
            + "sudo iptables %s INPUT -p tcp --sport %s -j REJECT";
    private static final String IPTABLES_DROP_DPORT_EXTERNAL_ONLY_COMMAND_STR
            = "sudo iptables %s INPUT -p tcp -s localhost --dport %s -j ACCEPT && "
            + "sudo iptables %s INPUT -p tcp --dport %s -j DROP && "
            + "sudo iptables %s OUTPUT -p tcp -d localhost --dport %s -j ACCEPT && "
            + "sudo iptables %s OUTPUT -p tcp --dport %s -j DROP";
    private static final String IPTABLE_SAFELIST_COMMAND_STR
            = "sudo iptables %s OUTPUT -p tcp -d %s --dport %d -j ACCEPT && "
            + "sudo iptables %s INPUT -p tcp -s %s --sport %d -j ACCEPT";
    private static final String GET_IPTABLES_RULES = "sudo iptables -S";

    // The string we are looking for to verify that there is an iptables rule to reject a port
    // We only need to look for sport because sport only gets created if dport is successful
    private static final String IPTABLES_RULE = "-m tcp --sport %s -j REJECT";

    private static final AtomicBoolean bandwidthSetup = new AtomicBoolean(false);

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

    @Override
    public void setBandwidth(int egressRateKbps) throws IOException, InterruptedException {
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface netint : Collections.list(nets)) {
            if (netint.isPointToPoint() || netint.isLoopback()) {
                continue;
            }
            createRootNetemQdiscOnInterface(netint.getName(), egressRateKbps);
            // set setup to true here, as cleanup should run even if subsequent command fail
            bandwidthSetup.set(true);
            for (int port: GG_UPSTREAM_PORTS) {
                filterPortOnInterface(netint.getName(), port);
            }
        }
    }

    private void filterPortOnInterface(String iface, int port) throws IOException, InterruptedException {
        // Filtering SSH traffic impacts test execution, so we explicitly disallow it
        if (port == SSH_PORT) {
            return;
        }
        List<String> filterSourcePortCommand = Stream.of("sudo", "tc", "filter", "add", "dev",
                iface, "parent", "1:", "protocol", "ip", "prio", "1", "u32", "match",
                "ip", "sport", Integer.toString(port), "0xffff", "flowid", "1:2").collect(Collectors.toList());
        executeCommand(filterSourcePortCommand);

        List<String> filterDestPortCommand = Stream.of("sudo", "tc", "filter", "add", "dev", iface,
                "parent", "1:", "protocol", "ip", "prio", "1", "u32", "match",
                "ip", "dport", Integer.toString(port), "0xffff", "flowid", "1:2").collect(Collectors.toList());
        executeCommand(filterDestPortCommand);
    }

    private void deleteRootNetemQdiscOnInterface() throws InterruptedException, IOException {
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface netint : Collections.list(nets)) {
            if (netint.isPointToPoint() || netint.isLoopback()) {
                continue;
            }
            executeCommand(Stream.of("sudo", "tc", "qdisc", "del", "dev", netint.getName(), "root")
                            .collect(Collectors.toList()));
        }
    }

    private void createRootNetemQdiscOnInterface(String iface, int netemRateKbps)
                throws InterruptedException, IOException {
        // TODO: Add support for setting packet loss and delay
        int netemDelayMs = 750;
        List<String> addQdiscCommand = Stream.of("sudo", "tc", "qdisc", "add", "dev", iface, "root", "handle",
                "1:", "prio", "bands", "2", "priomap", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0",
                "0", "0", "0", "0", "0").collect(Collectors.toList());
        executeCommand(addQdiscCommand);

        List<String> netemCommand = Stream.of("sudo", "tc", "qdisc", "add", "dev", iface, "parent", "1:2", "netem",
                "delay", String.format("%dms", netemDelayMs), "rate", String.format("%dkbit", netemRateKbps))
                .collect(Collectors.toList());
        executeCommand(netemCommand);
    }

    private String executeCommand(List<String> command) throws IOException, InterruptedException {
        Process proc = new ProcessBuilder().command(command).start();
        proc.waitFor(2, TimeUnit.SECONDS);
        if (proc.exitValue() != 0) {
            throw new IOException("CLI command " + command + " failed with error "
                    + new String(IoUtils.toByteArray(proc.getErrorStream()), StandardCharsets.UTF_8));
        }
        return new String(IoUtils.toByteArray(proc.getInputStream()), StandardCharsets.UTF_8);
    }

    @Override
    public void disconnectNetwork() throws InterruptedException, IOException {
        modifyInterfaceUpDownPolicy(IPTABLE_COMMAND_STR, ENABLE_OPTION, "connection-loss", NETWORK_PORTS);
    }

    @Override
    public void recoverNetwork() throws InterruptedException, IOException {
        modifyInterfaceUpDownPolicy(IPTABLE_COMMAND_STR, DISABLE_OPTION, "connection-recover", NETWORK_PORTS);
        if (bandwidthSetup.get()) {
            deleteRootNetemQdiscOnInterface();
            bandwidthSetup.set(false);
        }
    }

    @Override
    public void disconnectPort(int port) throws InterruptedException, IOException {
        modifyInterfaceUpDownPolicy(IPTABLE_COMMAND_STR, ENABLE_OPTION, "connection-loss", String.valueOf(port));
        blockedPorts.add(port);
    }

    @Override
    public void recoverPort(int port) throws InterruptedException, IOException {
        modifyInterfaceUpDownPolicy(IPTABLE_COMMAND_STR, DISABLE_OPTION, "connection-recover", String.valueOf(port));
    }

    @Override
    public boolean isPortBlocked(int port) throws InterruptedException, IOException {
        String output = executeCommand(Arrays.asList("sh", "-c",  GET_IPTABLES_RULES));
        String rule = String.format(IPTABLES_RULE, String.valueOf(port));
        return output.contains(rule);
    }

    @Override
    public void blockIngressFromPort(int port) throws InterruptedException, IOException {
        modifyInterfaceUpDownPolicy(IPTABLE_COMMAND_BLOCK_INGRESS_STR, ENABLE_OPTION, "connection-one-way",
                                    String.valueOf(port));
        blockedPorts.add(port);
    }

    @Override
    public void recoverIngressFromPort(int port) throws InterruptedException, IOException {
        modifyInterfaceUpDownPolicy(IPTABLE_COMMAND_BLOCK_INGRESS_STR, DISABLE_OPTION, "connection-one-way",
                                    String.valueOf(port));
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void modifyInterfaceUpDownPolicy(String iptableCommandString, String option, String eventName,
                                                String... ports) throws InterruptedException,
            IOException {
        for (String port : ports) {
            new ProcessBuilder().command("sh", "-c", String.format(iptableCommandString, option, port, option, port))
                    .start().waitFor(2, TimeUnit.SECONDS);
        }
    }


    @Override
    public void safelistAddressToPort(String address, int port) throws InterruptedException, IOException {
        modifySafelistPolicy(ENABLE_OPTION, address, port);
    }

    @Override
    public void removeSafelistAddressToPort(String address, int port) throws InterruptedException, IOException {
        modifySafelistPolicy(DISABLE_OPTION, address, port);
    }

    private void modifySafelistPolicy(String option, String address, int port)
                    throws InterruptedException, IOException {
        new ProcessBuilder().command("sh", "-c", String.format(IPTABLE_SAFELIST_COMMAND_STR, option, address, port,
                option, address, port)).start().waitFor(2, TimeUnit.SECONDS);
    }

    @Override
    public void addLoopBackAddress(String ipAddress) throws IOException, InterruptedException {
        if ("ubuntu".equals(PlatformResolver.getPlatform())) {
            (new ProcessBuilder(new String[0]))
                    .command("sh", "-c", String.format(" sudo ip addr add %s dev lo", ipAddress)).start()
                    .waitFor(2L, TimeUnit.SECONDS);
        } else {
            (new ProcessBuilder(new String[0]))
                    .command("sh", "-c", String.format(" sudo ifconfig lo:0 %s netmask 255.0.0.0 up", ipAddress))
                    .start()
                    .waitFor(2L, TimeUnit.SECONDS);
        }

    }

    @Override
    public void removeLoopBackAddress(String ipAddress) throws InterruptedException, IOException {
        if ("ubuntu".equals(PlatformResolver.getPlatform())) {
            (new ProcessBuilder(new String[0]))
                    .command("sh", "-c", String.format(" sudo ip addr del %s dev lo", ipAddress)).start()
                    .waitFor(2L, TimeUnit.SECONDS);
        } else {
            (new ProcessBuilder(new String[0]))
                    .command("sh", "-c", String.format(" sudo ifconfig lo:0 %s netmask 255.0.0.0 down", ipAddress))
                    .start()
                    .waitFor(2L, TimeUnit.SECONDS);
        }
    }
}
