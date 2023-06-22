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
import javax.inject.Inject;

@Log4j2
public class LocalIpcPublisher {
    private static final String TOPICS_SEPARATOR = ",";

    @Inject
    public LocalIpcPublisher() {
    }

    /**
     * Start LocalIpcPublisher execution.
     *
     * @param args command line args
     * @throws Exception on errors
     */
    public void accept(String... args) throws Exception {
        log.info("Args {}", args);
        String messageText = args[1];

        List<String> topics = Arrays.asList(args[0].split(TOPICS_SEPARATOR));
        publish(topics, messageText);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void publish(List<String> topics, String message) throws Exception {
        log.info("Publish to topics {}", topics);

        String topic = null;
        try (GreengrassCoreIPCClientV2 ipcClient = GreengrassCoreIPCClientV2.builder().build()) {
            for (String t : topics) {
                topic = t;
                publishBinaryMessageToTopic(ipcClient, topic, message);
                log.info("Successfully published to topic {}", topic);
            }
        } catch (Exception e) {
            if (e.getCause() instanceof UnauthorizedError) {
                log.error("Unauthorized error while publishing to topic " + topic, e);
            } else {
                log.error("Exception occurred when using IPC while publishing to topic " + topic, e);
            }
            throw e;
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
