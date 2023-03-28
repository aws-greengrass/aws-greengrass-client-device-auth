package com.aws.greengrass.steps;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

@ScenarioScoped
public class MqttControlSteps {

    @And("agent {string} is connected to MQTT Client Control")
    public void validateAgentIsConnectedToControl(String agentId) {
        //@TODO Implement method
    }

    @And("I associate {word} with ggc")
    public void associateClient(String clientDeviceId) {
        //@TODO Implement method
    }

    @When("I create client device {word} with the following AWS IoT policy")
    public void createClientDevice(String clientDeviceId, List<List<String>> policy) {
        //@TODO Implement method
    }


    @And("I connect device {string} on {string} to {string} as {string}")
    public void connect(String clientDeviceId, String agentId, String brokerId, String logicalConnectionId) {
        //@TODO Implement method
    }

    @Then("connection {string} is successfully established within {int} {word}")
    public void validateConnect(String logicalConnectionId, int value, String unitOfMeasure) {
        //@TODO Implement method
    }

    @When("I subscribe {string} to {string} with qos {int}")
    public void subscribe(String logicalConnectionId, String topic, int qos) {
        //@TODO Implement method
    }

    @Then("subscription to {string} is successfull on {string}")
    public void validateSubscribe(String topic, String logicalConnectionId) {
        //@TODO Implement method
    }

    @When("I publish {string} to {string} with qos {int} and message {string}")
    public void publish(String logicalConnectionId, String topic, int qos, String message) {
        //@TODO Implement method
    }

    @Then("publish message {string} to {string} is successfully on {string}")
    public void validatePublish(String message, String topic, String logicalConnectionId) {
        //@TODO Implement method
    }

    @And("message {string} received on {string}")
    public void receive(String message, String logicalConnectionId) {
        //@TODO Implement method
    }


}
