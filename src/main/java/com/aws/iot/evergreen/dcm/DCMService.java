/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.dcm;

import com.aws.iot.evergreen.config.Topics;
import com.aws.iot.evergreen.dependency.ImplementsService;
import com.aws.iot.evergreen.dependency.State;
import com.aws.iot.evergreen.deployment.DeviceConfiguration;
import com.aws.iot.evergreen.iot.IotCloudHelper;
import com.aws.iot.evergreen.iot.IotConnectionManager;
import com.aws.iot.evergreen.kernel.EvergreenService;
import com.aws.iot.evergreen.mqtt.MqttClient;
import com.aws.iot.evergreen.util.Coerce;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Singleton;

@ImplementsService(name = "aws.greengrass.deviceCertificateManager")
@Singleton
public class DCMService extends EvergreenService {
    private final IotConnectionManager iotConnectionManager;
    private final ExecutorService executorService;
    private final VersionAndNetworkUpdateManager versionAndNetworkUpdateManager;
    private final VersionAndNetworkUpdateHandler versionAndNetworkChangeHandler;
    private final ExecutorService executorServiceForCertGenWorkFlow;
    private static final String httpEndpoint = ""; // TODO: Where will this come from?

    /**
     * Constructor for EvergreenService.
     *
     * @param topics               root Configuration topic for this service
     * @param iotConnectionManager {@link IotConnectionManager}
     * @param executorService      the shared executor service
     * @param deviceConfiguration  See {@link DeviceConfiguration}
     * @param mqttClient           the shared mqtt client
     */
    @Inject
    public DCMService(Topics topics, IotConnectionManager iotConnectionManager, ExecutorService executorService,
                      DeviceConfiguration deviceConfiguration, MqttClient mqttClient) {
        // General TODO: Revisit executors. We should avoid creating new threads if we can help it.
        // Need to make sure we start/stop threads as appropriate.
        super(topics);
        this.iotConnectionManager = iotConnectionManager;
        this.executorService = executorService;
        IotCloudHelper iotCloudHelper = new IotCloudHelper();
        this.executorServiceForCertGenWorkFlow = Executors.newSingleThreadExecutor();
        this.versionAndNetworkChangeHandler = new VersionAndNetworkUpdateHandler(executorServiceForCertGenWorkFlow);
        // TODO: Subscribe to ThingName configuration updates instead of retrieving once
        this.versionAndNetworkUpdateManager =
                new VersionAndNetworkUpdateManager(this.iotConnectionManager, iotCloudHelper, httpEndpoint,
                        Coerce.toString(deviceConfiguration.getThingName()), versionAndNetworkChangeHandler,
                        mqttClient);
    }

    /**
     * Starts DCM.
     */
    @Override
    public void startup() {
        // Start listening to the version updates
        // We do this in a asynchronous way, since it can fail and we don't want to get blocked on it.
        executorService.submit(this::startVersionAndNetworkListener);
        reportState(State.RUNNING);
    }

    /**
     * Shutdowns DCM.
     */
    @Override
    public void shutdown() {
        // TODO: Shutdown executor services
    }

    private void startVersionAndNetworkListener() {
        try {
            versionAndNetworkUpdateManager.start();
        } catch (ExecutionException | TimeoutException e) {
            logger.atError().log("Unable to start listening to version updates. Will retry", e);
            // TODO: Retry indefinitely
        } catch (InterruptedException ignored) {
            // TODO: Don't ignore this
        }
    }
}
