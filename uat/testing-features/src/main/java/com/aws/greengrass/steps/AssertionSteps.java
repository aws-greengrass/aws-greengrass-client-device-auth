/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */


package com.aws.greengrass.steps;

import com.aws.greengrass.testing.model.ScenarioContext;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.en.When;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@Log4j2
@ScenarioScoped
public class AssertionSteps {

    private static final String ASSERTION_SERVER_URL = "assertionServerUrl";

    private final ScenarioContext scenarioContext;

    private WireMockServer wireMockServer;

    @Inject
    public AssertionSteps(ScenarioContext scenarioContext) {
        this.scenarioContext = scenarioContext;
    }

    /**
     * Start Assertion Server.
     */
    @When("I start an assertion server")
    public void startAssertionServer() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        scenarioContext.put(ASSERTION_SERVER_URL, wireMockServer.baseUrl());
        log.info("Assertion server started, url: {}", wireMockServer.baseUrl());
        wireMockServer.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
    }

    /**
     * Verify by context and message .
     *
     * @param count   int exact count of requests
     * @param context string context name
     * @param message string recieved message
     */
    @When("I get {int} assertions with context {string} and message {string}")
    public void verifyByContextAndMessage(int count, String context, String message) {
        wireMockServer.verify(
                exactly(count),
                anyRequestedFor(anyUrl()).withRequestBody(matchingJsonPath("$.context", equalTo(context)))
                                         .withRequestBody(matchingJsonPath("$.message", equalTo(message))));
    }

    /**
     * Stop Assertion Server.
     */
    @After
    public void stopAssertionServer() {
        if (wireMockServer != null) {
            wireMockServer.shutdown();
        }
    }

}
