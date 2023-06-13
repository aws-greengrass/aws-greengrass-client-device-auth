/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.ipc;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClientV2;
import software.amazon.awssdk.aws.greengrass.model.BinaryMessage;
import software.amazon.awssdk.aws.greengrass.model.PublishMessage;
import software.amazon.awssdk.aws.greengrass.model.PublishToTopicRequest;
import software.amazon.awssdk.aws.greengrass.model.PublishToTopicResponse;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.inject.Inject;

@Log4j2
public class LocalIpcPublisher implements Consumer<String[]> {
    private static final String TOPICS_SEPARATOR = ",";
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Inject
    public LocalIpcPublisher() {

    }

    /**
     * Start LocalIpcPublisher execution.
     *
     * @param args command line args
     */
    @Override
    public void accept(String[] args) {
        log.info("Args: {}", args);
        List<String> topics = Arrays.asList(args[0].split(TOPICS_SEPARATOR));
        log.info("Publish to topics: {}", topics);
        String messageText = args[1];
        topics.forEach(topic -> executor.execute(() -> publish(topic, messageText)));
    }

    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.DoNotTerminateVM"})
    private void publish(String topic, String message) {
        try (GreengrassCoreIPCClientV2 ipcClient = GreengrassCoreIPCClientV2.builder().build()) {
            publishBinaryMessageToTopic(ipcClient, topic, message);
            log.info("Successfully published to topic: {}", topic);
        } catch (Exception e) {
            if (e.getCause() instanceof UnauthorizedError) {
                log.error("Unauthorized error while publishing to topic: {}", topic);
            } else {
                log.error("Exception occurred when using IPC.");
            }
            System.exit(2);
        }
    }

    private static PublishToTopicResponse publishBinaryMessageToTopic(
            GreengrassCoreIPCClientV2 ipcClient, String topic, String message) throws InterruptedException {
        BinaryMessage binaryMessage =
                new BinaryMessage().withMessage(message.getBytes(StandardCharsets.UTF_8));
        PublishMessage publishMessage = new PublishMessage().withBinaryMessage(binaryMessage);
        PublishToTopicRequest publishToTopicRequest =
                new PublishToTopicRequest().withTopic(topic).withPublishMessage(publishMessage);
        return ipcClient.publishToTopic(publishToTopicRequest);
    }
}
