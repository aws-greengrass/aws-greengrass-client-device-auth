package com.aws.greengrass.steps;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

@ScenarioScoped
public class MqttControlSteps {
    @And("I associate {word} with ggc")
    public void associateClient(String clientDeviceId) {
        //@TODO Implement method
    }

    @When("I create client device {word}")
    public void createClientDevice(String clientDeviceId) {
        //@TODO Implement method
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

    @And("message {string} received on {string} from {string} topic within {int} {word}")
    public void receive(String message, String clientDeviceId, String topic, int value, String unitOfMeasure) {
        //@TODO Implement method
    }

    @And("I discover core device broker as {string}")
    public void discoverCoreDeviceBroker(String string) {
        //@TODO Implement method
    }
}
