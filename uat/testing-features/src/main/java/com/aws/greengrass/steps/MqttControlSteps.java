package com.aws.greengrass.steps;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;

@ScenarioScoped
public class MqttControlSteps {

    @And("the control component configured:")
    public void configureControl(String configurationSpec){
        //@TODO Implement method
    }

    @When("Client {word} subscribe to {word} qos {int}")
    public void subscribe(String clientId, String topic, int qos){
        //@TODO Implement method
    }

    @When("Client {word} publish to {word} qos {int} message:")
    public void publish(String clientId, String topic, int qos, String message){
        //@TODO Implement method
    }

    @When("I add {string} ggad with the following policy to the CDA configuration")
    public void addClientWithPolicy(String clientId, String policy) {
        //@TODO Implement method
    }

    @And("I associate {word} with ggc")
    public void associateClient(String clientId) {
        //@TODO Implement method
    }

    @And("run cloud discovery on {word}")
    public void runCloudDiscoveryOnClient(String clientId) {
        //@TODO Implement method
    }
}
