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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.inject.Inject;

@Log4j2
public class LocalIpcSubscriber implements Consumer<String[]> {

    private static final int DEFAULT_PORT = 8033;
    private static final String TOPICS_SEPARATOR = ",";

    private static final String DEFAULT_CONTEXT = "ReceivedPubsubMessage";

    private String assertionServerUrl = "http://localhost:8080";

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Inject
    public LocalIpcSubscriber() {
    }

    /**
     * Start LocalIpcSubscriber execution.
     *
     * @param args command line args
     */
    @Override
    public void accept(String[] args) {
        log.info("Args: {}", args);
        List<String> topics = Arrays.asList(args[0].split(TOPICS_SEPARATOR));
        log.info("Subscribe to topics: {}", topics);
        topics.forEach(topic -> executor.execute(() -> subscribe(topic)));
        final String url = Optional.ofNullable(args[1])
                                   .orElse(assertionServerUrl);
        log.info("Assertion server URL: {}", url);
        assertionServerUrl = url;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void subscribe(String topic) {
        try (GreengrassCoreIPCClientV2 ipcClient = GreengrassCoreIPCClientV2.builder()
                                                                            .withPort(DEFAULT_PORT)
                                                                            .build()) {
            SubscribeToTopicRequest request = new SubscribeToTopicRequest().withTopic(topic);
            GreengrassCoreIPCClientV2.StreamingResponse<SubscribeToTopicResponse, SubscribeToTopicResponseHandler>
                    response = ipcClient.subscribeToTopic(request,
                                                          this::onStreamEvent,
                                                          Optional.of(this::onStreamError),
                                                          Optional.of(this::onStreamClosed));
            SubscribeToTopicResponseHandler responseHandler = response.getHandler();
            log.info("Successfully subscribed to topic: {}", topic);

            try {
                while (true) {
                    Thread.sleep(10_000);
                }
            } catch (InterruptedException e) {
                log.info("Subscribe interrupted.");
            }

            responseHandler.closeStream();
        } catch (Exception e) {
            if (e.getCause() instanceof UnauthorizedError) {
                log.error("Unauthorized error while publishing to topic: " + topic);
            } else {
                log.error("Exception occurred when using IPC.", e);
            }
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void onStreamEvent(SubscriptionResponseMessage subscriptionResponseMessage) {
        try {
            BinaryMessage binaryMessage = subscriptionResponseMessage.getBinaryMessage();
            String message = new String(binaryMessage.getMessage(), StandardCharsets.UTF_8);
            String topic = binaryMessage.getContext()
                                        .getTopic();
            log.info("RECEIVED TOPIC={}, MESSAGE: {}", topic, message);
            MessageDto dto = MessageDto.builder()
                                       .topic(topic)
                                       .message(message)
                                       .context(DEFAULT_CONTEXT)
                                       .build();
            postToAssertionServer(dto);
        } catch (Exception e) {
            log.error(e);
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
        log.error("Received a stream error.", error);
        return false;
    }

    private void onStreamClosed() {
        log.info("Subscribe to topic stream closed.");
    }


}
