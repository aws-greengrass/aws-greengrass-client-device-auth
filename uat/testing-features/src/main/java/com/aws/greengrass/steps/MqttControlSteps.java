package com.aws.greengrass.steps;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;

@ScenarioScoped
public class MqttControlSteps {

    @Then("I configure Control:")
    public void configureControl(String configurationSpec){
        //@TODO Implement method
    }

    @And("I configure client scenario:")
    public void configureClientScenario(String scenarioSpec){
        //@TODO Implement method
    }
}
