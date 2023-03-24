package com.aws.greengrass.steps;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

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

    @And("I connect device {word} to broker")
    public void connectClientDeviceToBroker(String clientDeviceId) {
        //@TODO Implement method
    }

    @Then("device {string} is successfully connected to broker within {int} {word}")
    public void deviceIsSuccessfullyConnectedToWithinSeconds(String clientDeviceId, int value, String unit) {
        //@TODO Implement method
    }

    @When("I subscribe device {word} to {word} with qos {int}")
    public void subscribeClientDeviceToTopic(String clientDeviceId, String topic, int qos) {
        //@TODO Implement method
    }

    @Then("device {word} is successfully subscribed to {word}")
    public void deviceIsSuccessfullySubscribedToTopic(String clientDeviceId, String topic) {
        //@TODO Implement method
    }

    @When("I publish device {word} to {word} with qos {word} and message {string}")
    public void publishClientDEviceToTopic(String clientDeviceId, String topic, int qos, String message) {
        //@TODO Implement method
    }

    @Then("device {word} is successfully published message {string} to {word}")
    public void deviceIsSuccessfullyPublishedMessage(String clientDeviceId, String message, String topic) {
        //@TODO Implement method
    }

    @And("device {word} received from {word} message {string}")
    public void deviceReceivedFromMessage(String clientDeviceId, String topic, String message) {
        //@TODO Implement method
    }
}
