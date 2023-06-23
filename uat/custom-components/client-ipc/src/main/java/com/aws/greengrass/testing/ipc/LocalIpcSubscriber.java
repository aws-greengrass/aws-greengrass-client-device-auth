/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import com.aws.greengrass.testing.ipc.dto.MessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClientV2;
import software.amazon.awssdk.aws.greengrass.SubscribeToTopicResponseHandler;
import software.amazon.awssdk.aws.greengrass.model.BinaryMessage;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToTopicRequest;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToTopicResponse;
import software.amazon.awssdk.aws.greengrass.model.SubscriptionResponseMessage;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClientV2.StreamingResponse;

@Log4j2
public class LocalIpcSubscriber {

    private static final int DEFAULT_PORT = 8033;
    private static final String TOPICS_SEPARATOR = ",";

    private static final String DEFAULT_CONTEXT = "ReceivedPubsubMessage";

    private String assertionServerUrl = "http://localhost:8080";

    @Inject
    public LocalIpcSubscriber() {
    }

    /**
     * Start LocalIpcSubscriber execution.
     *
     * @param args command line args
     * @throws Exception on errors
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public void accept(String... args) throws Exception {
        log.info("Args {}", args);

        final String url = Optional.ofNullable(args[1])
                                   .orElse(assertionServerUrl);
        log.info("Assertion server URL {}", url);
        assertionServerUrl = url;

        List<String> topics = Arrays.asList(args[0].split(TOPICS_SEPARATOR));
        subscribe(topics);
    }

    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.SignatureDeclareThrowsException"})
    private void subscribe(final List<String> topics) throws Exception {
        log.info("Subscribe to topics {}", topics);

        List<SubscribeToTopicResponseHandler> responseHandlers = new ArrayList<>();

        String topic = null;
        try (GreengrassCoreIPCClientV2 ipcClient = GreengrassCoreIPCClientV2.builder()
                                                                            .withPort(DEFAULT_PORT)
                                                                            .build()) {
            for (String t : topics) {
                topic = t;
                SubscribeToTopicRequest request = new SubscribeToTopicRequest().withTopic(topic);

                StreamingResponse<SubscribeToTopicResponse, SubscribeToTopicResponseHandler> response = 
                            ipcClient.subscribeToTopic(request,
                                                        this::onStreamEvent,
                                                        Optional.of(this::onStreamError),
                                                        Optional.of(this::onStreamClosed));
                SubscribeToTopicResponseHandler responseHandler = response.getHandler();
                responseHandlers.add(responseHandler);
                log.info("Successfully subscribed to topic {}", topic);
            }

            try {
                while (true) {
                    Thread.sleep(10_000);
                }
            } catch (InterruptedException e) {
                log.info("LocalIpcSubscriber interrupted");
            }

            responseHandlers.forEach(responseHandler -> responseHandler.closeStream());

        } catch (Exception e) {
            if (e.getCause() instanceof UnauthorizedError) {
                log.error("Unauthorized error while subscribing to topic " + topic, e);
            } else {
                log.error("Exception occurred when using IPC while subscribing to topic " + topic, e);
            }
            throw e;
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void onStreamEvent(SubscriptionResponseMessage subscriptionResponseMessage) {
        String message = null;
        String topic = null;
        try {
            BinaryMessage binaryMessage = subscriptionResponseMessage.getBinaryMessage();
            message = new String(binaryMessage.getMessage(), StandardCharsets.UTF_8);
            topic = binaryMessage.getContext()
                                        .getTopic();
            log.info("Received IPC event topic {} message {}", topic, message);
            MessageDto dto = MessageDto.builder()
                                       .topic(topic)
                                       .message(message)
                                       .context(DEFAULT_CONTEXT)
                                       .build();
            postToAssertionServer(dto);
        } catch (Exception e) {
            log.error("Exception occurred when forwarding IPC message " + message + " on topic " + topic
                        + " to assertion server", e);
        }
    }

    private void postToAssertionServer(MessageDto message) throws IOException {
        URL url = new URL(assertionServerUrl + "/localIpcSubscriber");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = new ObjectMapper().writeValueAsString(message)
                                             .getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = br.readLine();
            while (responseLine != null) {
                response.append(responseLine.trim());
                responseLine = br.readLine();
            }
            log.debug("Assertion server response: {}", response.toString());
        }
    }

    private boolean onStreamError(Throwable error) {
        log.error("Received a stream error", error);
        return false;
    }

    private void onStreamClosed() {
        log.info("Subscribe to topic stream closed");
    }
}
