/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.steps;

import com.aws.greengrass.platforms.Platform;
import com.aws.greengrass.testing.model.ScenarioContext;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.When;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;

@Log4j2
@ScenarioScoped
public class NetworkUtilsSteps {

    private final ScenarioContext scenarioContext;

    private final AtomicBoolean mqttConnectivity = new AtomicBoolean(true);

    @Inject
    public NetworkUtilsSteps(ScenarioContext scenarioContext) {
        this.scenarioContext = scenarioContext;
    }

    /**
     * Convert offline/online string to boolean.
     *
     * @param value the string of offline/online value
     * @throws RuntimeException on invalid value
     */
    @SuppressWarnings("PMD.UnnecessaryAnnotationValueElement")
    @ParameterType(value = "offline|Offline|OFFLINE|online|Online|ONLINE")
    public boolean connectivityValue(String value) {
        switch (value) {
            case "offline":
            case "Offline":
            case "OFFLINE":
                return false;

            case "online":
            case "Online":
            case "ONLINE":
                return true;

            default:
                throw new RuntimeException("Invalid connectivity value " + value);
        }
    }

    /**
     * Disables or dnables device MQTT connectivity to IoT Core by blocking traffic on ports 8883 and 443.
     *
     * @param connectivity the value of connectivity to set
     * @throws IOException on IO errors
     * @throws InterruptedException when thread has been interrupted
     */
    @When("I set device mqtt connectivity to {connectivityValue}")
    public void setDeviceMqtt(final boolean connectivity) throws IOException, InterruptedException {
        boolean old = mqttConnectivity.getAndSet(connectivity);
        if (old != connectivity) {
            if (connectivity) {
                log.info("Unblocking MQTT connections");
                Platform.getInstance().getNetworkUtils().recoverMqtt();
            } else {
                log.info("Blocking MQTT connections");
                Platform.getInstance().getNetworkUtils().disconnectMqtt();
            }
        }
    }

    /**
     * Restore settings to defaults.
     *
     * @throws IOException on IO errors
     * @throws InterruptedException when thread has been interrupted
     */
    @After(order = Integer.MAX_VALUE)
    public void restoreDefaultSettings() throws IOException, InterruptedException {
        boolean old = mqttConnectivity.getAndSet(true);
        if (!old) {
            log.info("Automatically unblocking blocked MQTT connections");
            Platform.getInstance().getNetworkUtils().recoverMqtt();
        }
    }
}
