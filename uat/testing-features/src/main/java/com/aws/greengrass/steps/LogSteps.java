/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.steps;

import com.aws.greengrass.testing.features.FileSteps;
import com.aws.greengrass.testing.features.WaitSteps;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.platform.Platform;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Then;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

// should be joined with FileSteps
@ScenarioScoped
public class LogSteps {

    private final Platform platform;
    private final TestContext testContext;
    private final WaitSteps waits;
    private final FileSteps fileSteps;

    @Inject
    @SuppressWarnings("MissingJavadocMethod")
    public LogSteps(Platform platform, TestContext testContext, WaitSteps waits, FileSteps fileSteps) {
        this.platform = platform;
        this.testContext = testContext;
        this.waits = waits;
        this.fileSteps = fileSteps;
    }

    /**
     * File contains content at least N times after a duration.
     *
     * @param file file on a {@link Device}
     * @param contents file contents
     * @param times how many times contents should be present in file
     * @param value integer value for a duration
     * @param unit {@link TimeUnit} duration
     * @throws InterruptedException thread was interrupted while waiting
     */
    @Then("the file {word} on device contains {string} at least {int} times within {int} {word}")
    public void containsTimeout(String file, String contents, int times, int value, String unit)
                    throws InterruptedException {
        fileSteps.checkFileExists(file);
        TimeUnit timeUnit = TimeUnit.valueOf(unit.toUpperCase());
        boolean found = waits.untilTrue(() ->
                StringUtils.countMatches(platform.files().readString(testContext.installRoot().resolve(file)),
                                            contents) >= times, value, timeUnit);
        if (!found) {
            throw new IllegalStateException("file " + file + " did not contain " + contents);
        }
    }

    /**
     * Verifies that a component log file contains the contents within an interval.
     *
     * @param component name of the component log
     * @param line contents to validate
     * @param times how many times contents should be present in file
     * @param value number of units
     * @param unit specific {@link TimeUnit}
     * @throws InterruptedException throws when thread is interrupted
     */
    @Then("the {word} log on the device contains the line {string} at least {int} times within {int} {word}")
    public void logContains(String component, String line, int times, int value, String unit)
                    throws InterruptedException {
        String componentPath = "logs/" + component + ".log";
        containsTimeout(componentPath, line, times, value, unit);
    }
}
