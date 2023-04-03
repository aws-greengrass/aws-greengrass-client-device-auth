/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.steps;

import com.aws.greengrass.testing.features.IotSteps;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotCertificateSpec;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.log4j.Log4j2;
import lombok.val;

import java.io.IOException;
import javax.inject.Inject;

@Log4j2
@ScenarioScoped
public class MqttControlSteps {

    private static final String DEFAULT_CLIENT_DEVICE_POLICY_CONFIG = "/configs/iot/basic_client_device_policy.yaml";

    private final TestContext testContext;

    private final ScenarioContext scenarioContext;

    private final AWSResources resources;

    private final IotSteps iotSteps;

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public MqttControlSteps(TestContext testContext, ScenarioContext scenarioContext, AWSResources resources,
                            IotSteps iotSteps) {
        this.testContext = testContext;
        this.scenarioContext = scenarioContext;
        this.resources = resources;
        this.iotSteps = iotSteps;
    }

    @When("I associate {word} with ggc")
    public void associateClient(String clientDeviceId) {
        //@TODO Implement method
    }

    /**
     * Creates IoT Thing with IoT certificate and IoT policy.
     *
     * @param clientDeviceId string user defined client device id
     */
    @And("I create client device {string}")
    public void createClientDevice(String clientDeviceId) {
        val clientDeviceThingName = testContext.testId()
                                               .idFor(clientDeviceId);
        scenarioContext.put(clientDeviceId, clientDeviceThingName);
        val iotPolicySpec = createDefaultClientDevicePolicy(clientDeviceId);
        val iotCertificateSpec = IotCertificateSpec.builder()
                                                   .thingName(clientDeviceThingName)
                                                   .build();
        val iotThingSpec = IotThingSpec.builder()
                                       .thingName(clientDeviceThingName)
                                       .policySpec(iotPolicySpec)
                                       .createCertificate(true)
                                       .certificateSpec(iotCertificateSpec)
                                       .build();
        val iotThing = resources.create(iotThingSpec);
        log.debug("IoT Thing for client device {} is: {}", clientDeviceId, iotThing);
    }


    @And("I connect device {string} on {word} to {string}")
    public void connect(String clientDeviceId, String componentId, String brokerId) {
        //@TODO Implement method
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

    /**
     * Create the default client device policy with a name override.
     *
     * @param policyNameOverride name to use for IoT policy
     * @return IotPolicySpec
     * @throws RuntimeException failed to create an IoT policy for some reason
     */
    public IotPolicySpec createDefaultClientDevicePolicy(String policyNameOverride) {
        try {
            return iotSteps.createPolicy(DEFAULT_CLIENT_DEVICE_POLICY_CONFIG, policyNameOverride);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
