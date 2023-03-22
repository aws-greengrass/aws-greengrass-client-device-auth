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

    @When("Client {word} subscribe to {word}")
    public void subscribe(String clientId, String topic){
        //@TODO Implement method
    }

    @When("Client {word} publish to {word} message:")
    public void publish(String clientId, String topic, String message){
        //@TODO Implement method
    }
}
