/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.steps;

import com.aws.greengrass.testing.features.IotSteps;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.MqttConnectRequest;
import com.aws.greengrass.testing.mqtt.client.MqttProtoVersion;
import com.aws.greengrass.testing.mqtt.client.TLSSettings;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import com.aws.greengrass.testing.mqtt.client.control.api.EngineControl;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotCertificateSpec;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContextOptions;
import software.amazon.awssdk.iot.discovery.DiscoveryClient;
import software.amazon.awssdk.iot.discovery.DiscoveryClientConfig;
import software.amazon.awssdk.iot.discovery.model.ConnectivityInfo;
import software.amazon.awssdk.iot.discovery.model.DiscoverResponse;
import software.amazon.awssdk.iot.discovery.model.GGGroup;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;

import static software.amazon.awssdk.iot.discovery.DiscoveryClient.TLS_EXT_ALPN;

@Log4j2
@ScenarioScoped
public class MqttControlSteps {

    private static final String DEFAULT_CLIENT_DEVICE_POLICY_CONFIG = "/configs/iot/basic_client_device_policy.yaml";

    private static final int DEFAULT_CONTROL_GRPC_PORT = 47_619;
    private static final int DEFAULT_MQTT_KEEP_ALIVE = 60;
    private static final int DEFAULT_MQTT_TIMEOUT = 30;

    private final TestContext testContext;

    private final ScenarioContext scenarioContext;
    private final RegistrationContext registrationContext;
    private final AWSResources resources;
    private final AWSResourcesContext resourcesContext;

    private final IotSteps iotSteps;
    private final EngineControl engineControl;

    private final GreengrassV2Client greengrassClient;

    private Integer mqttTimeout;

    private final EngineControl.EngineEvents engineEvents = new EngineControl.EngineEvents() {
        @Override
        public void onAgentAttached(AgentControl agent) {
            log.info("Agent {} is connected", agent.getAgentId());
        }

        @Override
        public void onAgentDeattached(AgentControl agent) {
            log.info("Agent {} is disconnected", agent.getAgentId());
        }
    };

    private final AgentControl.ConnectionEvents connectionEvents = new AgentControl.ConnectionEvents() {
        @Override
        public void onMessageReceived(ConnectionControl connectionControl, Mqtt5Message message) {
            log.info("Message received: {}", message.toString());
        }

        @Override
        public void onMqttDisconnect(ConnectionControl connectionControl, Mqtt5Disconnect disconnect, String error) {
            log.info("Disconnected. Error: {}", error);
        }
    };

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public MqttControlSteps(
            TestContext testContext,
            ScenarioContext scenarioContext,
            RegistrationContext registrationContext,
            AWSResources resources,
            AWSResourcesContext resourcesContext,
            IotSteps iotSteps,
            EngineControl engineControl,
            GreengrassV2Client greengrassClient) throws IOException {
        this.testContext = testContext;
        this.scenarioContext = scenarioContext;
        this.registrationContext = registrationContext;
        this.resources = resources;
        this.resourcesContext = resourcesContext;
        this.iotSteps = iotSteps;
        this.engineControl = engineControl;
        this.greengrassClient = greengrassClient;
        startMqttControl();
    }

    /**
     * Associate client device with a core device .
     *
     * @param clientDeviceId string user defined client device id
     */
    @When("I associate {string} with ggc")
    public void associateClient(String clientDeviceId) {
        final String coreName = testContext.coreThingName();
        final String deviceName = testContext.testId()
                                             .idFor(clientDeviceId);
        greengrassClient.batchAssociateClientDeviceWithCoreDevice(b -> b.coreDeviceThingName(coreName)
                                                                        .entries(d -> d.thingName(deviceName)));
    }

    /**
     * Creates IoT Thing with IoT certificate and IoT policy.
     *
     * @param clientDeviceId string user defined client device id
     * @throws IOException thrown when default device policy is not found
     */
    @And("I create client device {string}")
    public void createClientDevice(String clientDeviceId) throws IOException {
        final String clientDeviceThingName = testContext.testId()
                                                        .idFor(clientDeviceId);
        scenarioContext.put(clientDeviceId, clientDeviceThingName);
        IotPolicySpec iotPolicySpec = createDefaultClientDevicePolicy(clientDeviceId);
        IotCertificateSpec iotCertificateSpec = IotCertificateSpec.builder()
                                                                  .thingName(clientDeviceThingName)
                                                                  .build();
        IotThingSpec iotThingSpec = IotThingSpec.builder()
                                                .thingName(clientDeviceThingName)
                                                .policySpec(iotPolicySpec)
                                                .createCertificate(true)
                                                .certificateSpec(iotCertificateSpec)
                                                .build();
        IotThingSpec iotThing = resources.create(iotThingSpec);
        log.debug("IoT Thing for client device {} is: {}", clientDeviceId, iotThing);
    }

    /**
     * Pass IoT Thing certificate for connection into specified client component.
     *
     * @param clientDeviceId string user defined client device id
     * @param componentId    componentId of MQTT client
     * @param brokerId       broker id
     */
    @And("I connect device {string} on {word} to {string}")
    public void connect(String clientDeviceId, String componentId, String brokerId) {
        final MqttConnectRequest request = getMqttConnectRequest(clientDeviceId, componentId, brokerId);
        engineControl.getAgent(getAgentId(componentId))
                     .createMqttConnection(request, connectionEvents);
    }

    @And("I set MQTT timeout {int}")
    public void setMqttTimeout(int mqttTimeout) {
        this.mqttTimeout = mqttTimeout;
    }

    @When("I subscribe {string} to {string} with qos {int}")
    public void subscribe(String clientDeviceId, String topic, int qos) {
        //@TODO Implement method
        throw new RuntimeException("Method subscribe is not implemented");
    }

    @Then("subscription to {string} is successfull on {string}")
    public void validateSubscribe(String topic, String clientDeviceId) {
        //@TODO Implement method
        throw new RuntimeException("Method validateSubscribe is not implemented");
    }

    @When("I publish from {string} to {string} with qos {int} and message {string}")
    public void publish(String clientDeviceId, String topic, int qos, String message) {
        //@TODO Implement method
        throw new RuntimeException("Method publish is not implemented");
    }

    @Then("publish message {string} to {string} is successfully on {string}")
    public void validatePublish(String message, String topic, String clientDeviceId) {
        //@TODO Implement method
        throw new RuntimeException("Method validatePublish is not implemented");
    }

    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    @And("message {string} received on {string} from {string} topic within {int} {word}")
    public void receive(String message, String clientDeviceId, String topic, int value, String unitOfMeasure) {
        //@TODO Implement method
        throw new RuntimeException("Method receive is not implemented");
    }

    /**
     * Discover IoT core device broker.
     *
     * @param brokerId       broker name in tests
     * @param clientDeviceId user defined client device id
     * @throws ExecutionException   thrown when future completed exceptionally
     * @throws InterruptedException thrown when the current thread was interrupted while waiting
     */
    @And("I discover core device broker as {string} from {string}")
    public void discoverCoreDeviceBroker(String brokerId, String clientDeviceId)
            throws ExecutionException, InterruptedException {
        final IotThingSpec thingSpec = getThingSpec(clientDeviceId);
        final String crt = thingSpec.resource()
                                    .certificate()
                                    .certificatePem();
        final String key = thingSpec.resource()
                                    .certificate()
                                    .keyPair()
                                    .privateKey();
        final String region = resourcesContext.region()
                                              .toString();
        final String ca = registrationContext.rootCA();
        final String thingName = testContext.testId()
                                            .idFor(clientDeviceId);
        try (SocketOptions socketOptions = new SocketOptions();
             TlsContextOptions tlsOptions = TlsContextOptions.createWithMtls(crt, key)
                                                             .withCertificateAuthority(ca)
                                                             .withAlpnList(TLS_EXT_ALPN);
             DiscoveryClientConfig config = new DiscoveryClientConfig(tlsOptions, socketOptions, region, 1, null);
             DiscoveryClient client = new DiscoveryClient(config)) {
            processDiscoveryResponse(
                    brokerId,
                    client.discover(thingName)
                          .get());
        }
    }

    private IotPolicySpec createDefaultClientDevicePolicy(String policyNameOverride) throws IOException {
        return iotSteps.createPolicy(DEFAULT_CLIENT_DEVICE_POLICY_CONFIG, policyNameOverride);
    }

    private void startMqttControl() throws IOException {
        if (!engineControl.isEngineRunning()) {
            engineControl.startEngine(DEFAULT_CONTROL_GRPC_PORT, engineEvents);
        }
    }

    private MqttConnectRequest getMqttConnectRequest(String clientDeviceId, String componentId, String brokerId) {
        final IotThingSpec thingSpec = getThingSpec(clientDeviceId);
        return MqttConnectRequest.newBuilder()
                                 .setClientId(getAgentId(componentId))
                                 .setHost(getBrokerHost(brokerId))
                                 .setPort(getBrokerPort(brokerId))
                                 .setKeepalive(DEFAULT_MQTT_KEEP_ALIVE)
                                 .setCleanSession(true)
                                 .setTimeout(getMqttTimeout())
                                 .setTls(buildTlsSettings(thingSpec, brokerId))
                                 .setProtocolVersion(MqttProtoVersion.MQTT_PROTOCOL_V50)
                                 .build();
    }

    private IotThingSpec getThingSpec(String thingName) {
        final String name = testContext.testId()
                                       .idFor(thingName);
        return resources.trackingSpecs(IotThingSpec.class)
                        .filter(t -> name.equals(t.resource()
                                                  .thingName()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Thing spec is not found"));
    }

    private TLSSettings buildTlsSettings(IotThingSpec thingSpec, String brokerId) {
        return TLSSettings.newBuilder()
                          .setCa(getBrokerCa(brokerId))
                          .setCert(thingSpec.resource()
                                            .certificate()
                                            .certificatePem())

                          .setKey(thingSpec.resource()
                                           .certificate()
                                           .keyPair()
                                           .privateKey())
                          .build();
    }

    private void processDiscoveryResponse(String brokerId, DiscoverResponse response) {
        if (response.getGGGroups() != null) {
            final Optional<GGGroup> groupOpt = response.getGGGroups()
                                                       .stream()
                                                       .findFirst();
            if (groupOpt.isPresent()) {
                final GGGroup group = groupOpt.get();
                final String ca = group.getCAs()
                                       .get(0);
                putBrokerCa(brokerId, ca);
                for (ConnectivityInfo info : group.getCores()
                                                  .get(0)
                                                  .getConnectivity()) {
                    final String host = info.getHostAddress();
                    final Integer port = info.getPortNumber();
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), getMqttTimeout());
                        putBrokerHost(brokerId, host);
                        putBrokerPort(brokerId, port);
                        log.debug("Core Device ConnectivityInfo, endpoint {}:{} is reachable", host, port);
                        break;
                    } catch (IOException e) {
                        log.warn("Core Device ConnectivityInfo, endpoint {}:{} is not reachable", host, port);
                    }
                }

            }
        }
    }

    private String getBrokerHost(String brokerId) {
        return scenarioContext.get("host-" + brokerId);
    }

    private void putBrokerHost(String brokerId, String host) {
        scenarioContext.put("host-" + brokerId, host);
    }

    private int getBrokerPort(String brokerId) {
        return Integer.parseInt(scenarioContext.get("port-" + brokerId));
    }

    private void putBrokerPort(String brokerId, Integer port) {
        scenarioContext.put("port-" + brokerId, port.toString());
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private String getAgentId(String componentName) {
        //@TODO Implement method
        throw new RuntimeException("Method getAgentId is not implemented");
    }

    private String getBrokerCa(String brokerId) {
        return scenarioContext.get("ca-" + brokerId);
    }

    private void putBrokerCa(String brokerId, String ca) {
        scenarioContext.put("ca-" + brokerId, ca);
    }

    private int getMqttTimeout() {
        return mqttTimeout == null ? DEFAULT_MQTT_TIMEOUT : mqttTimeout;

    }
}
