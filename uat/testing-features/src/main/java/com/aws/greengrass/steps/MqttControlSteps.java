/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.steps;

import com.aws.greengrass.testing.features.IotSteps;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
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

import java.io.IOException;
import javax.inject.Inject;

@Log4j2
@ScenarioScoped
public class MqttControlSteps {

    private static final String DEFAULT_CLIENT_DEVICE_POLICY_CONFIG = "/configs/iot/basic_client_device_policy.yaml";

    private static final int DEFAULT_CONTROL_GRPC_PORT = 47_619;
    private static final int DEFAULT_MQTT_KEEP_ALIVE = 60;
    private static final int DEFAULT_MQTT_CONNECT_TIMEOUT = 30;
    private static final int DEFAULT_MQTT_BROKER_PORT = 8883;
    private static final String DEFAULT_MQTT_BROKER_HOST = "localhost";

    private final TestContext testContext;

    private final ScenarioContext scenarioContext;

    private final AWSResources resources;

    private final IotSteps iotSteps;
    private final EngineControl engineControl;

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
    public MqttControlSteps(TestContext testContext, ScenarioContext scenarioContext, AWSResources resources,
                            IotSteps iotSteps, EngineControl engineControl) throws IOException {
        this.testContext = testContext;
        this.scenarioContext = scenarioContext;
        this.resources = resources;
        this.iotSteps = iotSteps;
        this.engineControl = engineControl;
        startMqttControl();
    }

    @When("I associate {word} with ggc")
    public void associateClient(String clientDeviceId) {
        //@TODO Implement method
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

    @Then("connection for device {string} is successfully established within {int} {word}")
    public void validateConnect(String clientDeviceId, int value, String unitOfMeasure) {
        //@TODO Implement method
    }

    @When("I subscribe {string} to {string} with qos {int}")
    public void subscribe(String clientDeviceId, String topic, int qos) {
        //@TODO Implement method
    }

    @Then("subscription to {string} is successfull on {string}")
    public void validateSubscribe(String topic, String clientDeviceId) {
        //@TODO Implement method
    }

    @When("I publish from {string} to {string} with qos {int} and message {string}")
    public void publish(String clientDeviceId, String topic, int qos, String message) {
        //@TODO Implement method
    }

    @Then("publish message {string} to {string} is successfully on {string}")
    public void validatePublish(String message, String topic, String clientDeviceId) {
        //@TODO Implement method
    }

    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    @And("message {string} received on {string} from {string} topic within {int} {word}")
    public void receive(String message, String clientDeviceId, String topic, int value, String unitOfMeasure) {
        //@TODO Implement method
    }

    @And("I discover core device broker as {string}")
    public void discoverCoreDeviceBroker(String string) {
        //@TODO Implement method
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
        final IotThingSpec thingSpec = getClientDeviceThingSpec(clientDeviceId);
        return MqttConnectRequest.newBuilder()
                                 .setClientId(getAgentId(componentId))
                                 .setHost(getBrokerHost(brokerId))
                                 .setPort(getBrokerPort(brokerId))
                                 .setKeepalive(DEFAULT_MQTT_KEEP_ALIVE)
                                 .setCleanSession(true)
                                 .setTimeout(DEFAULT_MQTT_CONNECT_TIMEOUT)
                                 .setTls(buildTlsSettings(thingSpec, brokerId))
                                 .setProtocolVersion(MqttProtoVersion.MQTT_PROTOCOL_V50)
                                 .build();
    }

    private IotThingSpec getClientDeviceThingSpec(String clientDeviceId) {
        final String name = testContext.testId()
                                       .idFor(clientDeviceId);
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

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private String getBrokerHost(String brokerId) {
        //@TODO Implement method
        return DEFAULT_MQTT_BROKER_HOST;
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private int getBrokerPort(String brokerId) {
        //@TODO Implement method
        return DEFAULT_MQTT_BROKER_PORT;
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private String getAgentId(String componentName) {
        //@TODO Implement method
        return "";
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private String getBrokerCa(String brokerId) {
        //@TODO Implement method
        return "";
    }
}
