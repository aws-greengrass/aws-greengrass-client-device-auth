/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm;

import com.aws.iot.evergreen.dcm.shadow.ShadowClient;
import com.aws.iot.evergreen.deployment.exceptions.AWSIotException;
import com.aws.iot.evergreen.iot.IotCloudHelper;
import com.aws.iot.evergreen.iot.IotConnectionManager;
import com.aws.iot.evergreen.logging.api.Logger;
import com.aws.iot.evergreen.logging.impl.LogManager;
import com.aws.iot.evergreen.mqtt.MqttClient;
import com.aws.iot.evergreen.tes.CredentialRequestHandler;
import com.aws.iot.evergreen.util.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.crt.mqtt.MqttMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Listens to network changes (connection to the shadow) and version updates. In particular listens to the GCM and CSI
 * version updates.
 */
public class VersionAndNetworkUpdateManager {

    private final IotConnectionManager iotConnectionManager;
    private final IotCloudHelper iotCloudHelper;
    private final String httpEndpoint;
    private final String thingName;
    private final UpdateHandler updateHandler;
    private final ShadowClient shadowClient;
    private final Map<String, String> latestProcessedShadowVersions = new HashMap<>();
    private ShadowClient.ShadowCallbacks shadowCallbacks;

    private static final String GET_SHADOW_HTTP_VERB = "GET";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LogManager.getLogger(CredentialRequestHandler.class);

    /**
     * Constructor to create VersionAndNetworkChangeListener.
     *
     * @param iotConnectionManager See {@link IotConnectionManager}
     * @param iotCloudHelper       See {@link IotCloudHelper}
     * @param httpEndpoint         host to talk to for getting shadow
     * @param thingName            thing name of the core
     * @param updateHandler        callbacks to be called when there are changes in versions or network connection
     * @param mqttClient           mqtt client to use for shadow updates
     */
    public VersionAndNetworkUpdateManager(IotConnectionManager iotConnectionManager, IotCloudHelper iotCloudHelper,
                                          String httpEndpoint, String thingName, UpdateHandler updateHandler,
                                          MqttClient mqttClient) {
        this.iotConnectionManager = iotConnectionManager;
        this.iotCloudHelper = iotCloudHelper;
        this.httpEndpoint = httpEndpoint;
        this.thingName = thingName;
        this.updateHandler = updateHandler;
        this.shadowCallbacks = createShadowCallbacks();
        this.shadowClient = new ShadowClient(mqttClient, shadowCallbacks);
    }

    // For unit tests
    VersionAndNetworkUpdateManager(IotConnectionManager iotConnectionManager, IotCloudHelper iotCloudHelper,
                                   String httpEndpoint, String thingName, UpdateHandler updateHandler,
                                   ShadowClient shadowClient) {
        this.iotConnectionManager = iotConnectionManager;
        this.iotCloudHelper = iotCloudHelper;
        this.httpEndpoint = httpEndpoint;
        this.thingName = thingName;
        this.updateHandler = updateHandler;
        this.shadowClient = shadowClient;
    }

    /**
     * Start listening to the version and network updates.
     *
     * @throws ExecutionException   if an error occurs
     * @throws InterruptedException if the thread is interrupted while subscribing
     * @throws TimeoutException     if the request times out
     */
    public void start() throws InterruptedException, ExecutionException, TimeoutException {
        final List<String> shadowTopics = new ArrayList<>();
        Constants.SERVICE_SUFFIXES.keySet().forEach(s -> shadowTopics.add(getShadowDeltaTopic(s)));
        shadowClient.subscribeToShadows(shadowTopics);
    }

    private ShadowClient.ShadowCallbacks createShadowCallbacks() {
        return new ShadowClient.ShadowCallbacks() {
            @Override
            public void onConnect() {
                updateHandler.handleNetworkReconnect();
            }

            @Override
            public void onSubscribe(String topic) {
                LOGGER.atDebug().kv("topic", topic).log("Topic subscribed");
                for (String service : Constants.SERVICE_SUFFIXES.keySet()) {
                    if (getShadowDeltaTopic(service).equals(topic)) {
                        try {
                            checkForNewVersion(service);
                        } catch (InterruptedException ignored) {
                            // TODO: Don't ignore - revisit this
                        }
                    }
                }
            }

            @Override
            public void onMessage(MqttMessage message) {
                for (String service : Constants.SERVICE_SUFFIXES.keySet()) {
                    if (getShadowDeltaTopic(service).equals(message.getTopic())) {
                        try {
                            handleNewCloudVersion(service, getVersionFromShadowDeltaPayload(message.getPayload()));
                        } catch (IOException e) {
                            LOGGER.atError().kv("payload", message.getPayload())
                                    .log("Unable to parse the shadow " + "payload", e);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        };
    }

    private void checkForNewVersion(String service) throws InterruptedException {
        try {
            handleNewCloudVersion(service, getLatestVersion(service));
        } catch (JsonProcessingException | AWSIotException e) {
            // TODO Add retries
        }
    }

    private void handleNewCloudVersion(String service, String newVersion) throws InterruptedException {
        if (Utils.isEmpty(newVersion)) {
            LOGGER.atInfo().log("{} version did not change", service);
            return;
        }

        if (newVersion.equals(latestProcessedShadowVersions.getOrDefault(service, ""))) {
            LOGGER.atInfo().kv("version", newVersion)
                    .log("Already processed version. Skipping cert re-generation flow");
            reportVersion(service, newVersion);
            return;
        }

        LOGGER.atInfo().log("New {} version: {}", service, newVersion);

        updateHandler.handleServiceVersionUpdate(service).exceptionally(throwable -> {
            // Check for type of throwable (retryable vs non-retryable) and retry if required
            throw new CompletionException(throwable);
        }).thenRun(() -> {
            latestProcessedShadowVersions.put(service, newVersion);
            try {
                reportVersion(service, newVersion);
            } catch (InterruptedException ignored) {
            }
        });
    }

    private void reportVersion(String service, String version) throws InterruptedException {
        LOGGER.atInfo().kv("service", service).addKeyValue("version", version).log("Reporting version");
        String shadowUpdate = String.format("{\"state\": {\"reported\" : {\"version\": \"%s\"}}}", version);
        String topic = getShadowUpdateTopic(service);
        try {
            shadowClient.updateShadow(topic, shadowUpdate.getBytes(StandardCharsets.UTF_8));
        } catch (TimeoutException | ExecutionException e) {
            LOGGER.atError().log("Unable to publish", e);
            // TODO: Add retry for publish
        }
    }

    private String getLatestVersion(String service) throws AWSIotException, JsonProcessingException {
        LOGGER.atDebug().kv("service", service).log("Retrieving latest shadow version");
        String response;
        String url = getShadowUrl(service);
        response = iotCloudHelper.sendHttpRequest(iotConnectionManager, url, GET_SHADOW_HTTP_VERB, null);

        GetShadowVersionResponse getShadowVersionResponse;
        getShadowVersionResponse = OBJECT_MAPPER.readValue(response, GetShadowVersionResponse.class);
        return getShadowVersionResponse.getState().getDelta().getVersion();
    }

    String getShadowUrl(String service) {
        return String.format("https://%s/things/%s/shadow", httpEndpoint, getShadowName(service));
    }

    private String getShadowName(String service) {
        return String.format("%s%s", thingName, Constants.SERVICE_SUFFIXES.get(service));
    }

    String getShadowDeltaTopic(String service) {
        return String.format("$aws/things/%s/shadow/update/delta", getShadowName(service));
    }

    private String getShadowUpdateTopic(String service) {
        return String.format("$aws/things/%s/shadow/update", getShadowName(service));
    }

    private String getVersionFromShadowDeltaPayload(byte[] payload) throws IOException {
        ShadowDeltaMessage message = OBJECT_MAPPER.readValue(payload, ShadowDeltaMessage.class);
        return message.getState().getVersion();
    }

    public interface UpdateHandler {
        CompletableFuture<Void> handleServiceVersionUpdate(String service);

        void handleNetworkReconnect();
    }

    // For unit tests
    public ShadowClient.ShadowCallbacks getShadowCallbacks() {
        return shadowCallbacks;
    }
}
