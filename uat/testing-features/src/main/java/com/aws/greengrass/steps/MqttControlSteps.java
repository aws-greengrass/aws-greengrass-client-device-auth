package com.aws.greengrass.steps;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;

@ScenarioScoped
public class MqttControlSteps {

    @And("my MQTT Client Control is running")
    public void runClientControl() {
        //@TODO Implement method
    }

    @And("I associate {word} with ggc")
    public void associateClient(String clientDeviceId) {
        //@TODO Implement method
    }

    @And("I create client device {word} on {word} with the following policy")
    public void createClientDevice(String clientDeviceId, String agentId) {
        //@TODO Implement method
    }

    @And("I connect {word} to {word}")
    public void connectClientDeviceToTopic(String clientDeviceId, String topic) {
        //@TODO Implement method
    }

    @Then("device {string} is successfully connected to {string} within {int} {word}")
    public void deviceIsSuccessfullyConnectedToWithinSeconds(String clientDeviceId, String topic, int value,
                                                             String unit) {
        //@TODO Implement method
    }
}
