/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.steps;

import com.aws.greengrass.testing.features.IotSteps;
import com.aws.greengrass.testing.model.RegistrationContext;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.modules.model.AWSResourcesContext;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Properties;
import com.aws.greengrass.testing.mqtt.client.Mqtt5RetainHandling;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Subscription;
import com.aws.greengrass.testing.mqtt.client.MqttConnectRequest;
import com.aws.greengrass.testing.mqtt.client.MqttProtoVersion;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttQoS;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import com.aws.greengrass.testing.mqtt.client.TLSSettings;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import com.aws.greengrass.testing.mqtt.client.control.api.EngineControl;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.Event;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventFilter;
import com.aws.greengrass.testing.mqtt.client.control.implementation.PublishReasonCode;
import com.aws.greengrass.testing.mqtt.client.control.implementation.SubscribeReasonCode;
import com.aws.greengrass.testing.mqtt.client.control.implementation.addon.EventStorageImpl;
import com.aws.greengrass.testing.mqtt.client.control.implementation.addon.MqttMessageEvent;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotCertificateSpec;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import com.google.protobuf.ByteString;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.grpc.StatusRuntimeException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContextOptions;
import software.amazon.awssdk.iot.discovery.DiscoveryClient;
import software.amazon.awssdk.iot.discovery.DiscoveryClientConfig;
import software.amazon.awssdk.iot.discovery.model.DiscoverResponse;
import software.amazon.awssdk.iot.discovery.model.GGCore;
import software.amazon.awssdk.iot.discovery.model.GGGroup;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.utils.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Inject;

import static software.amazon.awssdk.iot.discovery.DiscoveryClient.TLS_EXT_ALPN;

@SuppressWarnings("PMD.ExcessivePublicCount")
@Log4j2
@ScenarioScoped
public class MqttControlSteps {
    private static final String DEFAULT_CLIENT_DEVICE_POLICY_CONFIG = "/configs/iot/basic_client_device_policy.yaml";

    private static final int DEFAULT_MQTT_TIMEOUT_SEC = 30;

    private static final String MQTT_VERSION_311 = "v3";
    private static final String MQTT_VERSION_50 = "v5";
    private static final String DEFAULT_MQTT_VERSION = MQTT_VERSION_50;

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final String DEFAULT_CONTROL_GRPC_IP = "127.0.0.1";
    private static final String MQTT_CONTROL_ADDRESSES_KEY = "mqttControlAddresses";

    private static final int DEFAULT_CONTROL_GRPC_PORT = 0;
    private static final String MQTT_CONTROL_PORT_KEY = "mqttControlPort";

    private static final int MIN_QOS = 0;
    private static final int MAX_QOS = 2;

    // TODO: use scenario supplied values instead of defaults
    private static final int IOT_CORE_MQTT_PORT = 8883;

    // connect default properties
    // please keep order the same as in 3.1.2 CONNECT Variable Header of MQTT v5.0 spec
    private static final boolean DEFAULT_CONNECT_CLEAN_SESSION = true;
    private static final int DEFAULT_CONNECT_KEEP_ALIVE = 60;

    // please keep order the same as in 3.1.2.11 CONNECT Properties of MQTT v5.0 spec
    private static final Boolean DEFAULT_REQUEST_RESPONSE_INFORMATION = null;

    // TODO: CONNACK Response Information

    // publish default properties

    // please keep order the same as in 3.3.1 PUBLISH Fixed Header of MQTT v5.0 spec
    private static final boolean DEFAULT_PUBLISH_RETAIN = false;

    // please keep order the same as in 3.3.2.3 PUBLISH Properties of MQTT v5.0 spec
    private static final Boolean DEFAULT_PUBLISH_PAYLOAD_FORMAT_INDICATOR = null;
    private static final Integer DEFAULT_MESSAGE_EXPIRY_INTERVAL = null;
    private static final String DEFAULT_RESPONSE_TOPIC = null;
    private static final String DEFAULT_CORRELATION_DATA = null;
    private static final String DEFAULT_CONTENT_TYPE = null;

    // subscribe efault properties
    private static final Integer DEFAULT_SUBSCRIPTION_ID = null;        // NOTE: do not set for IoT Core broker !!!
    private static final boolean DEFAULT_SUBSCRIBE_NO_LOCAL = false;
    private static final boolean DEFAULT_SUBSCRIBE_RETAIN_AS_PUBLISHED = false;
    private static final Mqtt5RetainHandling DEFAULT_SUBSCRIBE_RETAIN_HANDLING
            = Mqtt5RetainHandling.MQTT5_RETAIN_SEND_AT_SUBSCRIPTION;


    // received messages default properties
    private static final Boolean DEFAULT_RECEIVED_RETAIN = null;
    private static final Boolean DEFAULT_RECEIVED_PAYLOAD_FORMAT_INDICATOR = null;

    private final TestContext testContext;

    private final ScenarioContext scenarioContext;
    private final RegistrationContext registrationContext;
    private final AWSResources resources;
    private final AWSResourcesContext resourcesContext;

    private final IotSteps iotSteps;
    private final EngineControl engineControl;
    private final EventStorageImpl eventStorage;

    private final GreengrassV2Client greengrassClient;

    /** Actual value of timeout in seconds used in all MQTT opetations. */
    private int mqttTimeoutSec = DEFAULT_MQTT_TIMEOUT_SEC;

    // please keep order the same as in 3.1.2 CONNECT Variable Header of MQTT v5.0 spec
    /** Actual value of CONNECT clean session. */
    private boolean connectCleanSession = DEFAULT_CONNECT_CLEAN_SESSION;

    /** Actual value of CONNECT keep alive. */
    private int connectKeepAlive = DEFAULT_CONNECT_KEEP_ALIVE;

    private Boolean connectRequestResponseInformation = DEFAULT_REQUEST_RESPONSE_INFORMATION;

    // TODO: CONNACK response information

    // please keep order the same as in 3.8.3.1 Subscription Options of MQTT v5.0
    /** Actual value of subscribe no local option. */
    private boolean subscribeNoLocal = DEFAULT_SUBSCRIBE_NO_LOCAL;

    /** Actual value of subscribe retain handling option. */
    private Mqtt5RetainHandling subscribeRetainHandling = DEFAULT_SUBSCRIBE_RETAIN_HANDLING;

    /** Actual value of subscribe retain handling option. */
    private boolean subscribeRetainAsPublished = DEFAULT_SUBSCRIBE_RETAIN_AS_PUBLISHED;


    /** Actual value of publish retain option. */
    private boolean txRetain = DEFAULT_PUBLISH_RETAIN;

    /** Actual expected value of retain flag in received messages. */
    private Boolean rxRetain = DEFAULT_RECEIVED_RETAIN;


    // please keep order the same as in 3.3.2.3 PUBLISH Properties of MQTT v5.0 spec
    /** Actual value of payload format indicator to publish. */
    private Boolean txPayloadFormatIndicator = DEFAULT_PUBLISH_PAYLOAD_FORMAT_INDICATOR;

    /** Actual expected value of payload format indicator in received messages. */
    private Boolean rxPayloadFormatIndicator = DEFAULT_RECEIVED_PAYLOAD_FORMAT_INDICATOR;


    /** Actual value of message expiry interval to publishing. */
    private Integer txMessageExpiryInterval = DEFAULT_MESSAGE_EXPIRY_INTERVAL;

    /** Actual expected value of message expiry interval in received messages. */
    private Integer rxMessageExpiryInterval = DEFAULT_MESSAGE_EXPIRY_INTERVAL;


    /** Actual value of response topic to publishing. */
    private String txResponseTopic = DEFAULT_RESPONSE_TOPIC;

    /** Actual expected value of response topic in received messages. */
    private String rxResponseTopic = DEFAULT_RESPONSE_TOPIC;


    /** Actual value of correlation data to publishing. */
    private String txCorrelationData = DEFAULT_CORRELATION_DATA;

    /** Actual expected value of correlation data in received messages. */
    private String rxCorrelationData = DEFAULT_CORRELATION_DATA;


    /** Actual list of user properties to transmit. */
    private static List<Mqtt5Properties> txUserProperties = null;

    /** Actual expected list of user properties in received messages. */
    private static List<Mqtt5Properties> rxUserProperties = null;


    /** Actual value of content type to transmit. */
    private String txContentType = DEFAULT_CONTENT_TYPE;

    /** Actual value of content type to receive in received messages. */
    private String rxContentType = DEFAULT_CONTENT_TYPE;


    private final Map<String, List<MqttBrokerConnectionInfo>> brokers = new HashMap<>();
    private final Map<String, MqttProtoVersion> mqttVersions = new HashMap<>();

    private final EngineControl.EngineEvents engineEvents = new EngineControl.EngineEvents() {
        @Override
        public void onAgentAttached(AgentControl agentControl) {
            log.info("Agent {} is connected", agentControl.getAgentId());
        }

        @Override
        public void onAgentDeattached(AgentControl agentControl) {
            log.info("Agent {} is disconnected", agentControl.getAgentId());
        }
    };

    private final AgentControl.ConnectionEvents connectionEvents = new AgentControl.ConnectionEvents() {
        @Override
        public void onMessageReceived(ConnectionControl connectionControl, Mqtt5Message message) {
            eventStorage.addEvent(new MqttMessageEvent(connectionControl, message));
            log.info("Message received on connection with name {}: {}", connectionControl.getConnectionName(), message);
        }

        @Override
        public void onMqttDisconnect(ConnectionControl connectionControl, Mqtt5Disconnect disconnect, String error) {
            // TODO: also add to eventStore
            log.info("MQTT client disconnected. Error: {}", error);
        }
    };

    /**
     * Creates instance of MqttControlSteps.
     *
     * @param testContext the instance of TestContext
     * @param scenarioContext the instance of ScenarioContext
     * @param registrationContext the instance of RegistrationContext
     * @param resources the instance of AWSResources
     * @param resourcesContext the instance of AWSResourcesContext
     * @param iotSteps the instance of IotSteps
     * @param engineControl the MQTT clients control
     * @param eventStorage the MQTT event storage
     * @param greengrassClient the GreengrassV2Client instance
     * @throws IOException on IO errors
     */
    @Inject
    public MqttControlSteps(
            TestContext testContext,
            ScenarioContext scenarioContext,
            RegistrationContext registrationContext,
            AWSResources resources,
            AWSResourcesContext resourcesContext,
            IotSteps iotSteps,
            EngineControl engineControl,
            EventStorageImpl eventStorage,
            GreengrassV2Client greengrassClient) throws IOException {
        this.testContext = testContext;
        this.scenarioContext = scenarioContext;
        this.registrationContext = registrationContext;
        this.resources = resources;
        this.resourcesContext = resourcesContext;
        this.iotSteps = iotSteps;
        this.engineControl = engineControl;
        this.eventStorage = eventStorage;
        this.greengrassClient = greengrassClient;

        initMqttVersions();
        startMqttControl();
    }

    /**
     * Convert boolean string to value.
     *
     * @param value the string value of boolean
     */
    @SuppressWarnings("PMD.UnnecessaryAnnotationValueElement")
    @ParameterType(value = "true|True|TRUE|false|False|FALSE")
    public Boolean booleanValue(String value) {
        return Boolean.valueOf(value);
    }

    /**
     * Convert boolean or null string to nullable value.
     *
     * @param value the string value of boolean or null
     */
    @SuppressWarnings("PMD.UnnecessaryAnnotationValueElement")
    @ParameterType(value = "true|True|TRUE|false|False|FALSE|null|NULL")
    public Boolean booleanOrNullValue(String value) {
        if ("null".equals(value) || "NULL".equals(value)) {
            return null;
        }

        return Boolean.valueOf(value);
    }

    /**
     * Sets MQTT operations timeout value.
     *
     * @param mqttTimeoutSec MQTT operations timeout in seconds
     */
    @And("I set MQTT timeout to {int} second(s)")
    public void setMqttTimeoutSec(int mqttTimeoutSec) {
        this.mqttTimeoutSec = mqttTimeoutSec;
        log.info("MQTT timeout set to {} second(s)", mqttTimeoutSec);
    }


    /**
     * Sets or resets CONNECT clean session boolean value.
     *
     * @param connectCleanSession the new boolean value of clean session or null
     */
    @And("I set 'clean session' to {booleanValue}")
    public void setCleanSessions(Boolean connectCleanSession) {
        this.connectCleanSession = connectCleanSession;
        log.info("CONNECT 'clean session' set to {}", connectCleanSession);
    }


    /**
     * Sets CONNECT 'keep alive' value.
     *
     * @param connectKeepAlive the value of CONNECT 'keep alive' property
     */
    @And("I set CONNECT 'keep alive' to {int} second(s)")
    public void setConnectKeepAlive(int connectKeepAlive) {
        this.connectKeepAlive = connectKeepAlive;
        log.info("CONNECT 'keep alive' set to {}", connectKeepAlive);
    }


    /**
     * Sets or resets CONNECT 'request response information' flag.
     *
     * @param connectRequestResponseInformation the value of CONNECT 'request response information' flag or null
     */
    @And("I set 'request response information' to {booleanOrNullValue}")
    public void setConnectRequestResponseInformation(Boolean connectRequestResponseInformation) {
        this.connectRequestResponseInformation = connectRequestResponseInformation;
        log.info("CONNECT 'request response information' set to {}", connectRequestResponseInformation);
    }

    // TODO: CONNACK response information

    /**
     * Sets MQTT subscribe 'no local' flag.
     *
     * @param subscribeNoLocal the new values of 'no local' flag.
     */
    @And("I set MQTT subscribe 'no local' flag to {booleanValue}")
    public void setSubscribeNoLocal(Boolean subscribeNoLocal) {
        this.subscribeNoLocal = subscribeNoLocal;
        log.info("Subscribe 'no local' flag set to {}", subscribeNoLocal);
    }

    /**
     * Sets MQTT subscribe 'retain handling' property.
     *
     * @param subscribeRetainHandling the new values of 'retain handling' property.
     */
    @And("I set MQTT subscribe 'retain handling' property to {string}")
    public void setSubscribeRetainHandling(String subscribeRetainHandling) {
        this.subscribeRetainHandling = Mqtt5RetainHandling.valueOf(subscribeRetainHandling);
        log.info("Subscribe 'retain handling' property set to {}", subscribeRetainHandling);
    }

    /**
     * Sets MQTT subscribe 'retain as published' flag.
     *
     * @param subscribeRetainAsPublished the new values of 'retain as published' flag.
     */
    @And("I set MQTT subscribe 'retain as published' flag to {booleanValue}")
    public void setSubscribeRetainAsPublished(Boolean subscribeRetainAsPublished) {
        this.subscribeRetainAsPublished = subscribeRetainAsPublished;
        log.info("Subscribe 'retain as published' flag set to {}", subscribeRetainAsPublished);
    }


    /**
     * Sets MQTT publish 'retain' flag.
     *
     * @param retain the new value of publish 'retain' flag
     */
    @And("I set MQTT publish 'retain' flag to {booleanValue}")
    public void setTxRetain(Boolean retain) {
        this.txRetain = retain;
        log.info("Publish 'retain' flag set to {}", retain);
    }

    /**
     * Sets MQTT receive 'retain' flag.
     *
     * @param retain the boolean new value of receive 'retain' flag or null
     */
    @And("I set the 'retain' flag in expected received messages to {booleanOrNullValue}")
    public void setRxRetain(Boolean retain) {
        this.rxRetain = retain;
        log.info("Expected 'retain' flag in received messages set to {}", retain);
    }

    /**
     * Sets MQTT publish 'payload format indicator' flag.
     *
     * @param payloadFormatIndicator the new boolean value of publish 'payload format indicator' flag or null to reset
     */
    @And("I set MQTT publish 'payload format indicator' flag to {booleanOrNullValue}")
    public void setTxPayloadFormatIndicator(Boolean payloadFormatIndicator) {
        this.txPayloadFormatIndicator = payloadFormatIndicator;
        log.info("Publish 'payload format indicator' flag set to {}", txPayloadFormatIndicator);
    }

    /**
     * Sets MQTT receive 'payload format indicator' flag.
     *
     * @param payloadFormatIndicator the new boolean value of receive 'payload format indicator' flag or null
     */
    @And("I set the 'payload format indicator' flag in expected received messages to {booleanOrNullValue}")
    public void setRxPayloadFormatIndicator(Boolean payloadFormatIndicator) {
        this.rxPayloadFormatIndicator = payloadFormatIndicator;
        log.info("Expected 'payload format indicator' flag in received messages set to {}", payloadFormatIndicator);
    }

    /**
     * Sets MQTT 'message expiry interval' value for publishing.
     *
     * @param messageExpiryInterval the new value of message expiry interval for publishing
     */
    @And("I set MQTT publish 'message expiry interval' to {int}")
    public void setTxMessageExpiryInterval(Integer messageExpiryInterval) {
        this.txMessageExpiryInterval = messageExpiryInterval;
        log.info("Publish 'message expiry interval' set to {}", messageExpiryInterval);
    }

    /**
     * Reset MQTT 'message expiry interval' value for publishing.
     *
     */
    @SuppressWarnings("PMD.NullAssignment")
    @And("I reset MQTT publish 'message expiry interval'")
    public void resetTxMessageExpiryInterval() {
        this.txMessageExpiryInterval = null;
        log.info("Publish 'message expiry interval' is reset");
    }

    /**
     * Sets value of expected MQTT 'message expiry interval' in received messages.
     *
     * @param messageExpiryInterval the new expected value of 'message expiry interval' in received messages
     */
    @And("I set the 'message expiry interval' in expected received messages to {int}")
    public void setRxMessageExpiryInterval(Integer messageExpiryInterval) {
        this.rxMessageExpiryInterval = messageExpiryInterval;
        log.info("Expected 'message expiry interval' in received messages set to {}", messageExpiryInterval);
    }

    /**
     * Reset expected value 'message expiry interval' in received messages.
     *
     */
    @SuppressWarnings("PMD.NullAssignment")
    @And("I reset expected 'message expiry interval'")
    public void resetRxMessageExpiryInterval() {
        this.rxMessageExpiryInterval = null;
        log.info("Expected 'message expiry interval' is reset");
    }

    /**
     * Sets MQTT 'response topic' value for publishing.
     *
     * @param responseTopic the new value of response topic for publishing
     */
    @And("I set MQTT publish 'response topic' to {string}")
    public void setTxResponseTopic(String responseTopic) {
        this.txResponseTopic = responseTopic;
        log.info("Publish 'response topic' set to {}", responseTopic);
    }

    /**
     * Reset MQTT 'response topic' value for publishing.
     *
     */
    @SuppressWarnings("PMD.NullAssignment")
    @And("I reset MQTT publish 'response topic'")
    public void resetTxResponseTopic() {
        this.txResponseTopic = null;
        log.info("Publish 'response topic' is reset");
    }

    /**
     * Sets value of expected MQTT 'response topic' in received messages.
     *
     * @param responseTopic the new expected value of 'response topic' in received messages
     */
    @And("I set the 'response topic' in expected received messages to {string}")
    public void setRxResponseTopic(String responseTopic) {
        this.rxResponseTopic = responseTopic;
        log.info("Expected 'response topic' in received messages set to {}", responseTopic);
    }

    /**
     * Reset expected value 'response topic' in received messages.
     *
     */
    @SuppressWarnings("PMD.NullAssignment")
    @And("I reset expected 'response topic'")
    public void resetRxResponseTopic() {
        this.rxResponseTopic = null;
        log.info("Expected 'response topic' is reset");
    }


    /**
     * Sets MQTT 'correlation data' value for publishing.
     *
     * @param correlationData the new value of correlation data for publishing
     */
    @And("I set MQTT publish 'correlation data' to {string}")
    public void setTxCorrelationData(String correlationData) {
        this.txCorrelationData = correlationData;
        log.info("Publish 'correlation data' set to {}", correlationData);
    }

    /**
     * Reset MQTT 'correlation data' value for publishing.
     *
     */
    @SuppressWarnings("PMD.NullAssignment")
    @And("I reset MQTT publish 'correlation data'")
    public void resetTxCorrelationData() {
        this.txCorrelationData = null;
        log.info("Publish 'correlation data' is reset");
    }


    /**
     * Sets value of expected MQTT 'correlation data' in received messages.
     *
     * @param correlationData the new expected value of 'correlation data' in received messages
     */
    @And("I set the 'correlation data' in expected received messages to {string}")
    public void setRxCorrelationData(String correlationData) {
        this.rxCorrelationData = correlationData;
        log.info("Expected 'correlation data' in received messages set to {}", correlationData);
    }

    /**
     * Reset expected value 'correlation data' in received messages.
     *
     */
    @SuppressWarnings("PMD.NullAssignment")
    @And("I reset expected 'correlation data'")
    public void resetRxCorrelationData() {
        this.rxCorrelationData = null;
        log.info("Expected 'correlation data' is reset");
    }


    /**
     * Sets MQTT user Properties to transmit.
     *
     * @param key the key of userProperties property.
     * @param value the value of userProperties property.
     */
    @And("I add MQTT 'user property' with key {string} and value {string} to transmit")
    public void addTxUserProperty(String key, String value) {
        if (txUserProperties == null) {
            txUserProperties = new ArrayList<>();
        }
        Mqtt5Properties userProperty = Mqtt5Properties.newBuilder()
                .setKey(key)
                .setValue(value)
                .build();
        txUserProperties.add(userProperty);
    }

    /**
     * Clear MQTT user properties to transmit.
     *
     */
    @And("I clear MQTT 'user properties' to transmit")
    @SuppressWarnings("PMD.NullAssignment")
    public void clearTxUserProperties() {
        txUserProperties = null;
    }

    /**
     * Sets MQTT user Properties to receive.
     *
     * @param key the key of userProperties property.
     * @param value the value of userProperties property.
     */
    @And("I add MQTT 'user property' with key {string} and value {string} to receive")
    public void addRxUserProperty(String key, String value) {
        if (rxUserProperties == null) {
            rxUserProperties = new ArrayList<>();
        }
        Mqtt5Properties userProperty = Mqtt5Properties.newBuilder()
                .setKey(key)
                .setValue(value)
                .build();
        rxUserProperties.add(userProperty);
    }

    /**
     * Clear MQTT user properties to receive.
     *
     */
    @And("I clear MQTT 'user properties' to receive")
    @SuppressWarnings("PMD.NullAssignment")
    public void clearRxUserProperties() {
        rxUserProperties = null;
    }

    /**
     * Sets MQTT content type to transmit.
     *
     * @param contentType MQTT content type to transmit
     */
    @And("I set MQTT publish 'content type' to {string}")
    public void setMqttTxContentType(String contentType) {
        this.txContentType = contentType;
        log.info("MQTT content type set to {} to transmit", contentType);
    }

    /**
     * Sets MQTT content type to receive.
     *
     * @param contentType MQTT content type to receive
     */
    @And("I set MQTT 'content type' in expected received messages to {string}")
    public void setMqttRxContentType(String contentType) {
        this.rxContentType = contentType;
        log.info("MQTT content type set to {} to receive", contentType);
    }

    /**
     * Reset MQTT content type to transmit.
     */
    @SuppressWarnings("PMD.NullAssignment")
    @And("I reset MQTT publish 'content type'")
    public void resetMqttTxContentType() {
        this.txContentType = null;
        log.info("MQTT content type reset to transmit");
    }

    /**
     * Reset MQTT content type to receive.
     */
    @SuppressWarnings("PMD.NullAssignment")
    @And("I reset MQTT 'content type' in expected received messages")
    public void resetMqttRxContentType() {
        this.rxContentType = null;
        log.info("MQTT content type reset to receive");
    }


    /**
     * Associate client device with a core device .
     *
     * @param clientDeviceId string user defined client device id
     */
    @When("I associate {string} with ggc")
    public void associateClient(String clientDeviceId) {
        final String coreName = testContext.coreThingName();
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        greengrassClient.batchAssociateClientDeviceWithCoreDevice(b -> b.coreDeviceThingName(coreName)
                                                                    .entries(d -> d.thingName(clientDeviceThingName)));
    }

    /**
     * Creates IoT Thing with IoT certificate and IoT policy.
     *
     * @param clientDeviceId string user defined client device id
     * @throws IOException thrown when default device policy is not found
     */
    @And("I create client device {string}")
    public void createClientDevice(String clientDeviceId) throws IOException {
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        scenarioContext.put(clientDeviceId, clientDeviceThingName);

        IotPolicySpec iotPolicySpec = createDefaultClientDevicePolicy(clientDeviceId);
        IotCertificateSpec iotCertificateSpec = IotCertificateSpec.builder()
                                                                  .thingName(clientDeviceThingName)
                                                                  .build();
        IotThingSpec iotThingSpec = IotThingSpec.builder()
                                                .thingName(clientDeviceThingName)
                                                .policySpec(iotPolicySpec)
                                                .createCertificate(true)
                                                .certificateSpec(iotCertificateSpec)
                                                .build();
        IotThingSpec iotThing = resources.create(iotThingSpec);
        log.debug("IoT Thing for client device {} is: {}", clientDeviceId, iotThing);
    }

    /**
     * Creates MQTT connection.
     *
     * @param clientDeviceId the id of the device (thing name) as defined by user in scenario
     * @param componentId  the componentId of MQTT client
     * @param brokerId the id of broker, before must be discovered or added by default
     */
    @And("I connect device {string} on {word} to {string}")
    public void connect(String clientDeviceId, String componentId, String brokerId) {
        connect(clientDeviceId, componentId, brokerId, DEFAULT_MQTT_VERSION);
    }

    /**
     * Creates MQTT connection.
     *
     * @param clientDeviceId the id of the device (thing name) as defined by user in scenario
     * @param componentId  the componentId of MQTT client
     * @param brokerId the id of broker, before must be discovered or added by default
     * @param mqttVersion the MQTT version string
     */
    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.UseObjectForClearerAPI"})
    @And("I connect device {string} on {word} to {string} using mqtt {string}")
    public void connect(String clientDeviceId, String componentId, String brokerId, String mqttVersion) {

        // get address information about broker
        final List<MqttBrokerConnectionInfo> bc = brokers.get(brokerId);
        if (CollectionUtils.isNullOrEmpty(bc)) {
            throw new RuntimeException("There is no address information about broker, "
                                        + "probably discovery step missing in scenario");
        }


        // get agent control by componentId
        AgentControl agentControl = getAgentControl(componentId);

        // request for new MQTT connection
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);

        // resolve MQTT version string to gRPC enum
        MqttProtoVersion version = convertMqttVersion(mqttVersion);

        RuntimeException lastException = null;
        for (final MqttBrokerConnectionInfo broker : bc) {
            final List<String> caList = broker.getCaList();
            final String host = broker.getHost();
            final Integer port = broker.getPort();
            log.info("Creating MQTT connection with broker {} to address {}:{} as Thing {} on {} using MQTT {}",
                     brokerId, host, port, clientDeviceThingName, componentId, mqttVersion);
            MqttConnectRequest request = buildMqttConnectRequest(
                    clientDeviceThingName, caList, host, port, version);
            try {
                ConnectionControl connectionControl
                        = agentControl.createMqttConnection(request, connectionEvents);
                log.info("Connection with broker {} established to address {}:{} as Thing {} on {}",
                         brokerId, host, port, clientDeviceThingName, componentId);
                setConnectionControl(connectionControl, clientDeviceThingName);
                return;
            } catch (RuntimeException ex) {
                lastException = ex;
            }
        }

        if (lastException == null) {
            throw new RuntimeException("No addresses to connect");
        }
        throw lastException;
    }

    /**
     * Disconnect IoT Thing.
     *
     * @param clientDeviceId string user defined client device id
     * @param reasonCode int disconnect reason code
     */
    @And("I disconnect device {string} with reason code {int}")
    public void disconnect(String clientDeviceId, int reasonCode) {
        // getting connectionControl by clientDeviceId
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        ConnectionControl connectionControl = getConnectionControl(clientDeviceThingName);

        // do disconnect
        connectionControl.closeMqttConnection(reasonCode, txUserProperties);
        log.info("Thing {} was disconnected with reason code {}", clientDeviceId, reasonCode);
    }

    /**
     * Subscribe the MQTT topics by filter.
     *
     * @param clientDeviceId the user defined client device id
     * @param topicFilterString the topics filter to subscribe
     * @param qos the max value of MQTT QoS for subscribe
     * @throws StatusRuntimeException thrown on gRPC errors
     * @throws IllegalArgumentException on invalid QoS argument
     */
    @When("I subscribe {string} to {string} with qos {int}")
    public void subscribe(@NonNull String clientDeviceId, @NonNull String topicFilterString, int qos) {
        final Set<Integer> expectedSet = IntStream.rangeClosed(0, qos).boxed().collect(Collectors.toSet());
        subscribe(clientDeviceId, topicFilterString, qos, expectedSet);
    }

    /**
     * Subscribe the MQTT topics by filter.
     *
     * @param clientDeviceId the user defined client device id
     * @param topicFilterString the topics filter to subscribe
     * @param qos the max value of MQTT QoS for subscribe
     * @param expectedStatus the status of MQTT QoS for subscribe reply
     * @throws StatusRuntimeException thrown on gRPC errors
     * @throws IllegalArgumentException on invalid QoS argument
     */
    @When("I subscribe {string} to {string} with qos {int} and expect status {string}")
    public void subscribe(@NonNull String clientDeviceId, @NonNull String topicFilterString,
                          int qos, String expectedStatus) {
        subscribe(clientDeviceId, topicFilterString, qos,
                  Collections.singleton(SubscribeReasonCode.valueOf(expectedStatus).getValue()));
    }

    /**
     * Subscribe the MQTT topics by filter.
     *
     * @param clientDeviceId the user defined client device id
     * @param topicFilterString the topics filter to subscribe
     * @param qos the max value of MQTT QoS for subscribe
     * @param expectedStatuses the set of expected statuses of MQTT subscribe reply
     * @throws StatusRuntimeException thrown on gRPC errors
     * @throws IllegalArgumentException on invalid QoS argument
     */
    private void subscribe(@NonNull String clientDeviceId, @NonNull String topicFilterString,
                          int qos, final Set<Integer> expectedStatuses) {
        if (qos < MIN_QOS || qos > MAX_QOS) {
            throw new IllegalArgumentException("Invalid QoS value " + qos + " requested");
        }

        // getting connectionControl by clientDeviceId
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        ConnectionControl connectionControl = getConnectionControl(clientDeviceThingName);

        final String filter = scenarioContext.applyInline(topicFilterString);

        // do subscription
        log.info("Create MQTT subscription for Thing {} to topics filter {} with QoS {} no local {} "
                    + "retain handling {}", clientDeviceThingName, filter, qos, subscribeNoLocal,
                    subscribeRetainHandling);

        Mqtt5Subscription mqtt5Subscription = buildMqtt5Subscription(filter,
                qos,
                subscribeNoLocal,
                subscribeRetainAsPublished,
                subscribeRetainHandling);
        MqttSubscribeReply mqttSubscribeReply = connectionControl.subscribeMqtt(DEFAULT_SUBSCRIPTION_ID,
                                                                                txUserProperties, mqtt5Subscription);
        if (mqttSubscribeReply == null) {
            throw new RuntimeException("Do not receive reply to MQTT subscribe request");
        }

        List<Integer> reasons = mqttSubscribeReply.getReasonCodesList();
        if (reasons == null) {
            throw new RuntimeException("Receive reply to MQTT subscribe request with missing reason codes");
        }

        if (reasons.size() != 1 || reasons.get(0) == null) {
            throw new RuntimeException("Receive reply to MQTT subscribe request with unexpected number "
                    + "of reason codes should be 1 but has " + reasons.size());
        }

        final int reason = reasons.get(0);
        if (expectedStatuses.contains(reason)) {
            log.info("MQTT subscription has on topics filter {} been created with reason code {}", filter, reason);
        } else {
            if (expectedStatuses.size() == 1) {
                log.error("MQTT subscription has on topics filter {} been failed. Expected reason code was {},"
                    + " but returned {}", filter, expectedStatuses.iterator().next(), reason);
            } else {
                log.error("MQTT subscription has on topics filter {} been failed. Unexpected reason code {}", filter,
                          reason);
            }
            throw new RuntimeException("Receive reply to MQTT subscribe request with missing reason codes");
        }
    }

    /**
     * Publish the MQTT message.
     *
     * @param clientDeviceId user defined client device id
     * @param topicString the topic to publish message
     * @param qos the value of MQTT QoS for publishing
     * @param message the the content of message to publish
     * @throws StatusRuntimeException on gRPC errors
     * @throws IllegalArgumentException on invalid QoS argument
     */
    @When("I publish from {string} to {string} with qos {int} and message {string}")
    public void publish(String clientDeviceId, String topicString, int qos, String message) {
        publish(clientDeviceId, topicString, qos, message, PublishReasonCode.SUCCESS.getValue());
    }

    /**
     * Publish the MQTT message.
     *
     * @param clientDeviceId user defined client device id
     * @param topicString the topic to publish message
     * @param qos the value of MQTT QoS for publishing
     * @param message the the content of message to publish
     * @param expectedStatus the status of MQTT QoS for publish reply
     * @throws StatusRuntimeException on gRPC errors
     * @throws IllegalArgumentException on invalid QoS argument
     */
    @When("I publish from {string} to {string} with qos {int} and message {string} and expect status {int}")
    public void publish(String clientDeviceId, String topicString, int qos, String message, int expectedStatus) {
        // getting connectionControl by clientDeviceId
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        ConnectionControl connectionControl = getConnectionControl(clientDeviceThingName);

        final String topic = scenarioContext.applyInline(topicString);

        // do publishing
        log.info("Publishing MQTT message '{}' as Thing {} to topic {} with QoS {} retain {}"
                        + " payload format indicator {} message expire interval {} response topic {} "
                        + "correlation data {}", message, clientDeviceThingName, topic, qos, txRetain,
                    txPayloadFormatIndicator, txMessageExpiryInterval, txResponseTopic, txCorrelationData);

        Mqtt5Message mqtt5Message = buildMqtt5Message(qos, topic, message);
        MqttPublishReply mqttPublishReply = connectionControl.publishMqtt(mqtt5Message);
        if (mqttPublishReply == null) {
            throw new RuntimeException("Do not receive reply to MQTT publish request");
        }

        final int reasonCode = mqttPublishReply.getReasonCode();
        if (reasonCode != expectedStatus) {
            throw new RuntimeException("MQTT publish completed with negative reason code " + reasonCode);
        }

        log.info("MQTT message '{}' has been succesfully published", message);
    }

    /**
     * Verify is MQTT message is received in limited duration of time.
     *
     * @param message content of message to receive
     * @param clientDeviceId the user defined client device id
     * @param topicString the topic (not a filter) which message has been sent
     * @param value the duration of time to wait for message
     * @param unit the time unit to wait
     * @throws TimeoutException when matched message was not received in specified duration of time
     * @throws RuntimeException on internal errors
     * @throws InterruptedException then thread has been interrupted
     */
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    @And("message {string} is not received on {string} from {string} topic within {int} {word}")
    public void notReceivedMessage(String message, String clientDeviceId, String topicString, int value, String unit)
            throws TimeoutException, InterruptedException {
        receive(message, clientDeviceId, topicString, value, unit, false);
    }

    /**
     * Verify is MQTT message is received in limited duration of time.
     *
     * @param message content of message to receive
     * @param clientDeviceId the user defined client device id
     * @param topicString the topic (not a filter) which message has been sent
     * @param value the duration of time to wait for message
     * @param unit the time unit to wait
     * @throws TimeoutException when matched message was not received in specified duration of time
     * @throws RuntimeException on internal errors
     * @throws InterruptedException then thread has been interrupted
     */
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    @And("message {string} received on {string} from {string} topic within {int} {word}")
    public void receivedMessage(String message, String clientDeviceId, String topicString, int value, String unit)
                            throws TimeoutException, InterruptedException {
        receive(message, clientDeviceId, topicString, value, unit, true);
    }

    /**
     * Verify is MQTT message is received in limited duration of time.
     *
     * @param message content of message to receive
     * @param clientDeviceId the user defined client device id
     * @param topicString the topic (not a filter) which message has been sent
     * @param value the duration of time to wait for message
     * @param unit the time unit to wait
     * @param isExpectedMessage used for setting message expectation
     * @throws TimeoutException when matched message was not received in specified duration of time
     * @throws RuntimeException on internal errors
     * @throws InterruptedException then thread has been interrupted
     */
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public void receive(String message, String clientDeviceId, String topicString, int value,
                        String unit, boolean isExpectedMessage)
                            throws TimeoutException, InterruptedException {
        // getting connectionControl by clientDeviceId
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        ConnectionControl connectionControl = getConnectionControl(clientDeviceThingName);

        final String topic = scenarioContext.applyInline(topicString);

        // build filter
        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withConnectionControl(connectionControl)
                                        .withTopic(topic)
                                        .withContentType(rxContentType)
                                        .withContent(message)
                                        .withRetain(rxRetain)
                                        .withUserProperties(rxUserProperties)
                                        .withPayloadFormatIndicator(rxPayloadFormatIndicator)
                                        .withMessageExpiryInterval(rxMessageExpiryInterval)
                                        .withResponseTopic(rxResponseTopic)
                                        .withCorrelationData(rxCorrelationData)
                                        .build();
        // convert time units
        TimeUnit timeUnit = TimeUnit.valueOf(unit.toUpperCase());

        // awaiting for message
        log.info("Awaiting for MQTT message '{}' with retain {} payload format indicator {} message expiry interval {} "
                    + "response topic {} correlation data {} on topic '{}' on Thing '{}' for {} {}", message, rxRetain,
                    rxPayloadFormatIndicator, rxMessageExpiryInterval, rxResponseTopic, rxCorrelationData, topic,
                    clientDeviceThingName, value, unit);

        List<Event> events = new ArrayList<>();
        try {
            events = eventStorage.awaitEvents(eventFilter, value, timeUnit);
        } catch (TimeoutException e) {
            if (isExpectedMessage) {
                log.error("No matched MQTT messages have been received, ex: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }
        if (!isExpectedMessage && !events.isEmpty()) {
            throw new RuntimeException("MQTT unexpected messages have been received");
        }
    }

    /**
     * Clear message storage.
     *
     */
    @And("I clear message storage")
    public void clearStorage() {
        eventStorage.clear();
        log.info("Storage was cleared");
    }

    /**
     * Clear message storage.
     *
     */
    @And("I clear message storage and reset all MQTT settings to default")
    public void clearAnything() {
        clearStorage();
        setMqttTimeoutSec(DEFAULT_MQTT_TIMEOUT_SEC);
        setCleanSessions(DEFAULT_CONNECT_CLEAN_SESSION);
        setConnectKeepAlive(DEFAULT_CONNECT_KEEP_ALIVE);
        setConnectRequestResponseInformation(DEFAULT_REQUEST_RESPONSE_INFORMATION);
        // TODO: reset CONNACK ResponseInformation

        setTxRetain(DEFAULT_PUBLISH_RETAIN);
        setRxRetain(DEFAULT_RECEIVED_RETAIN);

        setTxPayloadFormatIndicator(DEFAULT_PUBLISH_PAYLOAD_FORMAT_INDICATOR);
        setRxPayloadFormatIndicator(DEFAULT_RECEIVED_PAYLOAD_FORMAT_INDICATOR);

        setTxMessageExpiryInterval(DEFAULT_MESSAGE_EXPIRY_INTERVAL);
        setRxMessageExpiryInterval(DEFAULT_MESSAGE_EXPIRY_INTERVAL);

        setTxResponseTopic(DEFAULT_RESPONSE_TOPIC);
        setRxResponseTopic(DEFAULT_RESPONSE_TOPIC);

        setTxCorrelationData(DEFAULT_CORRELATION_DATA);
        setRxCorrelationData(DEFAULT_CORRELATION_DATA);

        clearTxUserProperties();
        clearRxUserProperties();

        setMqttTxContentType(DEFAULT_CONTENT_TYPE);
        setMqttRxContentType(DEFAULT_CONTENT_TYPE);

        setSubscribeNoLocal(DEFAULT_SUBSCRIBE_NO_LOCAL);
        setSubscribeRetainHandling(DEFAULT_SUBSCRIBE_RETAIN_HANDLING.name());
        setSubscribeRetainAsPublished(DEFAULT_SUBSCRIBE_RETAIN_AS_PUBLISHED);
    }

    /**
     * Discover IoT core device broker directly in OTF.
     *
     * @param brokerId       broker name in tests
     * @param clientDeviceId user defined client device id
     * @throws ExecutionException   thrown when future completed exceptionally
     * @throws InterruptedException thrown when the current thread was interrupted while waiting
     */
    @And("I discover core device broker as {string} from {string} in OTF")
    public void discoverCoreDeviceBroker(String brokerId, String clientDeviceId)
            throws ExecutionException, InterruptedException {
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);

        final IotThingSpec thingSpec = getClientDeviceThingSpec(clientDeviceThingName);
        final String crt = thingSpec.resource()
                                    .certificate()
                                    .certificatePem();
        final String key = thingSpec.resource()
                                    .certificate()
                                    .keyPair()
                                    .privateKey();
        final String region = resourcesContext.region().toString();
        final String ca = registrationContext.rootCA();
        try (SocketOptions socketOptions = new SocketOptions();
                TlsContextOptions tlsOptions = TlsContextOptions.createWithMtls(crt, key)
                                                                 .withCertificateAuthority(ca)
                                                                 .withAlpnList(TLS_EXT_ALPN);
                DiscoveryClientConfig config = new DiscoveryClientConfig(tlsOptions, socketOptions, region, 1, null);
                DiscoveryClient client = new DiscoveryClient(config)) {
            processDiscoveryResponse(brokerId, client.discover(clientDeviceThingName).get());
        }
    }

    /**
     * Set up IoT core broker.
     *
     * @param brokerId broker name in tests
     */
    @And("I label IoT Core broker as {string}")
    public void discoverCoreDeviceBroker(String brokerId) {
        final String endpoint = resources.lifecycle(IotLifecycle.class)
                                         .dataEndpoint();
        final String ca = registrationContext.rootCA();
        MqttBrokerConnectionInfo broker = new MqttBrokerConnectionInfo(
                endpoint, IOT_CORE_MQTT_PORT, Collections.singletonList(ca));
        brokers.put(brokerId, Collections.singletonList(broker));
        log.info("Added IoT Core broker as {} with endpoint {}:{}", brokerId, endpoint, IOT_CORE_MQTT_PORT);
    }

    /**
     * Unsubscribe the MQTT topics by filter.
     *
     * @param clientDeviceId the user defined client device id
     * @param filter the topics filter to unsubscribe
     */
    @Then("I unsubscribe core device broker as {string} from {string}")
    public void unsubscribe(String clientDeviceId, String filter) {
        // getting connectionControl by clientDeviceId
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        ConnectionControl connectionControl = getConnectionControl(clientDeviceThingName);

        // do unsubscribe
        log.info("Create MQTT unsubscription for Thing {} to topics filter {}", clientDeviceThingName, filter);

        MqttSubscribeReply mqttUnsubscribeReply = connectionControl.unsubscribeMqtt(txUserProperties, filter);

        List<Integer> reasons = mqttUnsubscribeReply.getReasonCodesList();
        if (reasons == null) {
            throw new RuntimeException("Receive reply to MQTT unsubscribe request with missing reason codes");
        }

        if (reasons.size() != 1 || reasons.get(0) == null) {
            throw new RuntimeException("Receive reply to MQTT unsubscribe request with unexpected number "
                    + "of reason codes should be 1 but has " + reasons.size());
        }

        int reason = reasons.get(0);
        if (reason != PublishReasonCode.SUCCESS.getValue()) {
            throw new RuntimeException("Receive reply to MQTT unsubscribe request with unsuccessful reason code "
                    + reason);
        }
        log.info("MQTT topics filter {} has been unsubscribed", filter);
    }

    private IotPolicySpec createDefaultClientDevicePolicy(String policyNameOverride) throws IOException {
        return iotSteps.createPolicy(DEFAULT_CLIENT_DEVICE_POLICY_CONFIG, policyNameOverride);
    }

    private void startMqttControl() throws IOException {
        if (!engineControl.isEngineRunning()) {
            engineControl.startEngine(DEFAULT_CONTROL_GRPC_PORT, engineEvents);
        }

        final int boundPort = engineControl.getBoundPort();
        String[] addresses = engineControl.getIPs();
        log.info("MQTT clients control started gRPC service on port {} addresses {}", boundPort, addresses);

        if (addresses == null || addresses.length == 0) {
            addresses = new String[] { DEFAULT_CONTROL_GRPC_IP };
        }
        scenarioContext.put(MQTT_CONTROL_ADDRESSES_KEY, String.join(" ", addresses));
        scenarioContext.put(MQTT_CONTROL_PORT_KEY, String.valueOf(boundPort));
    }

    private MqttConnectRequest buildMqttConnectRequest(String clientDeviceThingName, List<String> caList, String host,
                                                        int port, MqttProtoVersion version) {
        final IotThingSpec thingSpec = getClientDeviceThingSpec(clientDeviceThingName);

        MqttConnectRequest.Builder builder = MqttConnectRequest.newBuilder()
                                 .setClientId(clientDeviceThingName)
                                 .setHost(host)
                                 .setPort(port)
                                 .setKeepalive(connectKeepAlive)
                                 .setCleanSession(connectCleanSession)
                                 .setTls(buildTlsSettings(thingSpec, caList))
                                 .setProtocolVersion(version);

        if (connectRequestResponseInformation != null) {
            builder.setRequestResponseInformation(connectRequestResponseInformation);
        }

        if (txUserProperties != null) {
             builder.addAllProperties(txUserProperties);
        }

        return builder.build();
    }

    private IotThingSpec getClientDeviceThingSpec(String clientDeviceThingName) {
        return resources.trackingSpecs(IotThingSpec.class)
                        .filter(t -> clientDeviceThingName.equals(t.resource().thingName()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Thing spec is not found"));
    }

    private TLSSettings buildTlsSettings(IotThingSpec thingSpec, List<String> caList) {
        return TLSSettings.newBuilder()
                          .addAllCaList(caList)
                          .setCert(thingSpec.resource()
                                            .certificate()
                                            .certificatePem())

                          .setKey(thingSpec.resource()
                                           .certificate()
                                           .keyPair()
                                           .privateKey())
                          .build();
    }

    private void processDiscoveryResponse(String brokerId, DiscoverResponse response) {
        if (response == null) {
            throw new IllegalStateException("Discovery response is missing");
        }

        final List<GGGroup> groups = response.getGGGroups();
        if (groups == null || groups.isEmpty() || groups.get(0) == null) {
            throw new IllegalStateException("Groups are missing in discovery response");
        }

        log.info("Discovered data for broker {}:", brokerId);
        groups.stream().forEach(group -> {
            log.info("groupId {} with {} CA", group.getGGGroupId(), group.getCAs().size());
            group.getCores().stream().forEach(core -> {
                log.info("Core with thing Arn {}", core.getThingArn());
                core.getConnectivity().stream().forEach(ci -> {
                    log.info("Connectivity info: id {} host {} port {}",
                                ci. getId(),
                                ci.getHostAddress(),
                                ci.getPortNumber());
                });
            });
        });

        List<MqttBrokerConnectionInfo> mqttBrokerConnectionInfos = new ArrayList<>();
        groups.forEach(group -> {
            group.getCores()
                 .stream()
                 .map(GGCore::getConnectivity)
                 .flatMap(Collection::stream)
                 .map(ci -> new MqttBrokerConnectionInfo(
                    ci.getHostAddress(),
                    ci.getPortNumber(),
                    group.getCAs()))
                 .collect(Collectors.toCollection(() -> mqttBrokerConnectionInfos));
        });

        brokers.put(brokerId, mqttBrokerConnectionInfos);
    }

    private String getAgentId(String componentName) {
        return componentName;
    }

    /**
     * Randomize thing name provided by user in scenario to unique value.
     * That allow to run the same scenario multiple times in the same time without conflicts on backend.\
     *
     * @param clientDeviceId the thing name as provided by user in scenario
     * @return randomized thing name
     */
    private String getClientDeviceThingName(@NonNull String clientDeviceId) {
        return testContext.testId().idFor(clientDeviceId);
    }

    private AgentControl getAgentControl(@NonNull String componentId) {
        final String agentId = getAgentId(componentId);

        AgentControl agentControl = engineControl.getAgent(agentId);
        if (agentControl == null) {
            throw new IllegalStateException("Agent (MQTT client) with agentId '" + agentId
                                                + "' does not registered in the MQTT Clients Control");
        }

        agentControl.setTimeout(mqttTimeoutSec);
        return agentControl;
    }

    /*
     * FIXME: usage of clientDeviceThingName as a name of connection control
     *  does not allow to name multiple connection with the same clientDeviceId
     */
    private static String getConnectionName(@NonNull String clientDeviceThingName) {
        return clientDeviceThingName;
    }

    private void setConnectionControl(@NonNull ConnectionControl connectionControl,
                                        @NonNull String clientDeviceThingName) {
        final String connectionName = getConnectionName(clientDeviceThingName);
        connectionControl.setConnectionName(connectionName);
    }

    private ConnectionControl getConnectionControl(@NonNull String clientDeviceThingName) {
        final String connectionName = getConnectionName(clientDeviceThingName);
        ConnectionControl connectionControl = engineControl.getConnectionControl(connectionName);
        if (connectionControl == null) {
            throw new IllegalStateException("MQTT connection with name '" + connectionName
                                            + "' does not exist in the MQTT Clients Control");
        }

        connectionControl.setTimeout(mqttTimeoutSec);
        return connectionControl;
    }

    private MqttQoS getMqttQoS(int qos) {
        MqttQoS mqttQoS = MqttQoS.forNumber(qos);
        if (mqttQoS == null) {
            throw new IllegalArgumentException("Invalid MQTT QoS value " + qos);
        }
        return mqttQoS;
    }

    private Mqtt5Subscription buildMqtt5Subscription(@NonNull String filter, int qos, boolean noLocal,
                                                boolean retainAsPublished, 
                                                @NonNull Mqtt5RetainHandling retainHandling) {
        MqttQoS mqttQoS = getMqttQoS(qos);
        return Mqtt5Subscription.newBuilder()
                    .setFilter(filter)
                    .setQos(mqttQoS)
                    .setNoLocal(noLocal)
                    .setRetainAsPublished(retainAsPublished)
                    .setRetainHandling(retainHandling)
                    .build();
    }

    private Mqtt5Message buildMqtt5Message(int qos, @NonNull String topic, @NonNull String content) {
        MqttQoS mqttQoS = getMqttQoS(qos);
        Mqtt5Message.Builder builder = Mqtt5Message.newBuilder()
                            .setTopic(topic)
                            .setPayload(ByteString.copyFromUtf8(content))
                            .setQos(mqttQoS)
                            .setRetain(txRetain);

        // please order the same as in 3.3.2.3 PUBLISH Properties of MQTT v5.0 spec
        if (txPayloadFormatIndicator != null) {
            builder.setPayloadFormatIndicator(txPayloadFormatIndicator);
        }

        if (txUserProperties != null) {
             builder.addAllProperties(txUserProperties);
        }

        if (txMessageExpiryInterval != null) {
            builder.setMessageExpiryInterval(txMessageExpiryInterval);
        }

        if (txResponseTopic != null) {
            builder.setResponseTopic(txResponseTopic);
        }

        if (txCorrelationData != null) {
            builder.setCorrelationData(ByteString.copyFromUtf8(txCorrelationData));
        }

        if (txContentType != null) {
            builder.setContentType(txContentType);
        }

        return builder.build();
    }

    private void initMqttVersions() {
        mqttVersions.put(MQTT_VERSION_311, MqttProtoVersion.MQTT_PROTOCOL_V_311);
        mqttVersions.put(MQTT_VERSION_50, MqttProtoVersion.MQTT_PROTOCOL_V_50);
    }

    private MqttProtoVersion convertMqttVersion(String mqttVersion) {
        MqttProtoVersion version = mqttVersions.get(mqttVersion);

        if (version == null) {
            throw new IllegalArgumentException("Unknown MQTT version " + mqttVersion);
        }

        return version;
    }

    /**
     * Stop MQTT Control Engine.
     *
     * @throws InterruptedException thrown when the current thread was interrupted while waiting
     */
    @After
    public void stopMqttControlEngine() throws InterruptedException {
        try {
            engineControl.stopEngine(false);
            engineControl.awaitTermination();
        } catch (StatusRuntimeException ex) {
            log.warn("Exception during stop clients control", ex);
        }
    }

    @Data
    @AllArgsConstructor
    private class MqttBrokerConnectionInfo {
        private String host;
        private Integer port;
        private List<String> caList;
    }
}
