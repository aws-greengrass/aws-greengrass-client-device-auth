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
import com.aws.greengrass.testing.mqtt.client.control.implementation.DisconnectReasonCode;
import com.aws.greengrass.testing.mqtt.client.control.implementation.addon.EventStorageImpl;
import com.aws.greengrass.testing.mqtt.client.control.implementation.addon.MqttMessageEvent;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotCertificateSpec;
import com.aws.greengrass.testing.resources.iot.IotLifecycle;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import com.google.protobuf.ByteString;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.inject.Inject;

import static software.amazon.awssdk.iot.discovery.DiscoveryClient.TLS_EXT_ALPN;

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

    private static final int MQTT5_REASON_SUCCESS = 0;
    private static final int MQTT5_GRANTED_QOS_2 = 2;

    // TODO: use scenario supplied values instead of defaults
    private static final int DEFAULT_MQTT_KEEP_ALIVE = 60;
    private static final boolean CONNECT_CLEAR_SESSION = true;

    private static final boolean PUBLISH_RETAIN = false;

    private static final Integer SUBSCRIPTION_ID = null;                        // NOTE: do not set for IoT Core !!!
    private static final boolean SUBSCRIBE_NO_LOCAL = false;
    private static final boolean SUBSCRIBE_RETAIN_AS_PUBLISHED = false;
    private static final Mqtt5RetainHandling SUBSCRIBE_RETAIN_HANDLING
            = Mqtt5RetainHandling.MQTT5_RETAIN_DO_NOT_SEND_AT_SUBSCRIPTION;

    private static final int IOT_CORE_PORT = 443;


    private final TestContext testContext;

    private final ScenarioContext scenarioContext;
    private final RegistrationContext registrationContext;
    private final AWSResources resources;
    private final AWSResourcesContext resourcesContext;

    private final IotSteps iotSteps;
    private final EngineControl engineControl;
    private final EventStorageImpl eventStorage;

    private final GreengrassV2Client greengrassClient;
    private int mqttTimeoutSec = DEFAULT_MQTT_TIMEOUT_SEC;
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
            log.info("Creating MQTT connection with broker {} to address {}:{} as Thing {} on {} MQTT {}",
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
     */
    @And("I disconnect device {string}")
    public void disconnect(String clientDeviceId) {
        // getting connectionControl by clientDeviceId
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        ConnectionControl connectionControl = getConnectionControl(clientDeviceThingName);

        //do disconnect
        connectionControl.closeMqttConnection(DisconnectReasonCode.NORMAL_DISCONNECTION.getValue());
        log.info("Thing {} was disconnected", clientDeviceId);
    }

    /**
     * Set MQTT operations timeout value.
     *
     * @param mqttTimeoutSec MQTT operations timeout in seconds
     */
    @And("I set MQTT timeout {int}")
    public void setMqttTimeoutSec(int mqttTimeoutSec) {
        this.mqttTimeoutSec = mqttTimeoutSec;
    }

    /**
     * Subscribe the MQTT topics by filter.
     *
     * @param clientDeviceId the user defined client device id
     * @param filter the topics filter to subscribe
     * @param qos the max value of MQTT QoS for subscribe
     * @throws StatusRuntimeException thrown on gRPC errors
     * @throws IllegalArgumentException on invalid QoS argument
     */
    @When("I subscribe {string} to {string} with qos {int}")
    public void subscribe(String clientDeviceId, String filter, int qos) {
        // getting connectionControl by clientDeviceId
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        ConnectionControl connectionControl = getConnectionControl(clientDeviceThingName);

        // do subscription
        log.info("Create MQTT subscription for Thing {} to topics filter {} with QoS {}", clientDeviceThingName,
                    filter, qos);

        // TODO: use non default settings here
        Mqtt5Subscription mqtt5Subscription = buildMqtt5Subscription(filter,
                                                                        qos,
                                                                        SUBSCRIBE_NO_LOCAL,
                                                                        SUBSCRIBE_RETAIN_AS_PUBLISHED,
                                                                        SUBSCRIBE_RETAIN_HANDLING);
        MqttSubscribeReply mqttSubscribeReply = connectionControl.subscribeMqtt(SUBSCRIPTION_ID, mqtt5Subscription);
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

        int reason = reasons.get(0);
        if (reason > MQTT5_GRANTED_QOS_2) {
            throw new RuntimeException("Receive reply to MQTT subscribe request with unsuccessful reason code "
                                        + reason);
        }

        if (reason != qos) {
            throw new RuntimeException("Receive reply to MQTT subscribe request with unexpected reason code should be "
                                        + qos + " but has " + reason);
        }
        log.info("MQTT subscription has on topics filter {} been created", filter);
    }

    /**
     * Publish the MQTT message.
     *
     * @param clientDeviceId user defined client device id
     * @param topic the topic to publish message
     * @param qos the value of MQTT QoS for publishing
     * @param message the the content of message to publish
     * @throws StatusRuntimeException on gRPC errors
     * @throws IllegalArgumentException on invalid QoS argument
     */
    @When("I publish from {string} to {string} with qos {int} and message {string}")
    public void publish(String clientDeviceId, String topic, int qos, String message) {
        // getting connectionControl by clientDeviceId
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        ConnectionControl connectionControl = getConnectionControl(clientDeviceThingName);

        // do publishing
        log.info("Publishing MQTT message {} as Thing {} to topics filter {} with QoS {}", message,
                    clientDeviceThingName, topic, qos);
        // TODO: replace default retain value to passed from scenario
        Mqtt5Message mqtt5Message = buildMqtt5Message(qos, PUBLISH_RETAIN, topic, message);
        MqttPublishReply mqttPublishReply = connectionControl.publishMqtt(mqtt5Message);
        if (mqttPublishReply == null) {
            throw new RuntimeException("Do not receive reply to MQTT publish request");
        }

        // TODO: compare with expected reason code passed from scenario
        final int reasonCode = mqttPublishReply.getReasonCode();
        if (reasonCode != MQTT5_REASON_SUCCESS) {
            throw new RuntimeException("MQTT publish completed with negative reason code " + reasonCode);
        }

        log.info("MQTT message {} has been succesfully published", message);
    }

    /**
     * Verify is MQTT message is received in limited duration of time.
     *
     * @param message content of message to receive
     * @param clientDeviceId the user defined client device id
     * @param topic the topic (not a filter) which message has been sent
     * @param value the duration of time to wait for message
     * @param unit the time unit to wait
     * @throws TimeoutException when matched message was not received in specified duration of time
     * @throws RuntimeException on internal errors
     * @throws InterruptedException then thread has been interrupted
     */
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    @And("message {string} received on {string} from {string} topic within {int} {word}")
    public void receive(String message, String clientDeviceId, String topic, int value, String unit)
                            throws TimeoutException, InterruptedException {
        // getting connectionControl by clientDeviceId
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);
        ConnectionControl connectionControl = getConnectionControl(clientDeviceThingName);

        // build filter
        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withConnectionControl(connectionControl)
                                        .withTopic(topic)
                                        .withContent(message)
                                        .build();
        // convert time units
        TimeUnit timeUnit = TimeUnit.valueOf(unit.toUpperCase());

        // awaiting for message
        log.info("Awaiting for MQTT message {} on topic {} on Thing {} for {} {}", message, topic,
                    getClientDeviceThingName(clientDeviceId), value, unit);
        List<Event> events = eventStorage.awaitEvents(eventFilter, value, timeUnit);

        // check events is not empty, actually never happens due ro TimeoutException
        if (events.isEmpty()) {
            // no message(s) were received
            throw new RuntimeException("No matched MQTT messages have been received");
        }
    }

    /**
     * Discover IoT core device broker.
     *
     * @param brokerId       broker name in tests
     * @param clientDeviceId user defined client device id
     * @throws ExecutionException   thrown when future completed exceptionally
     * @throws InterruptedException thrown when the current thread was interrupted while waiting
     */
    @And("I discover core device broker as {string} from {string}")
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
    @And("I label IoT core broker as {string}")
    public void discoverCoreDeviceBroker(String brokerId) {
        final String endpoint = resources.lifecycle(IotLifecycle.class)
                                         .dataEndpoint();
        final String ca = registrationContext.rootCA();
        MqttBrokerConnectionInfo broker = new MqttBrokerConnectionInfo(
                endpoint, IOT_CORE_PORT, Collections.singletonList(ca));
        brokers.put(brokerId, Collections.singletonList(broker));
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

        MqttSubscribeReply mqttUnsubscribeReply = connectionControl.unsubscribeMqtt(filter);

        List<Integer> reasons = mqttUnsubscribeReply.getReasonCodesList();
        if (reasons == null) {
            throw new RuntimeException("Receive reply to MQTT unsubscribe request with missing reason codes");
        }

        if (reasons.size() != 1 || reasons.get(0) == null) {
            throw new RuntimeException("Receive reply to MQTT unsubscribe request with unexpected number "
                    + "of reason codes should be 1 but has " + reasons.size());
        }

        int reason = reasons.get(0);
        if (reason != MQTT5_REASON_SUCCESS) {
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

            final int boundPort = engineControl.getBoundPort();
            String[] addresses = engineControl.getIPs();
            log.info("MQTT clients control started gRPC service on port {} adrresses {}", boundPort, addresses);

            if (addresses == null || addresses.length == 0) {
                addresses = new String[] { DEFAULT_CONTROL_GRPC_IP };
            }
            scenarioContext.put(MQTT_CONTROL_ADDRESSES_KEY, String.join(" ", addresses));
            scenarioContext.put(MQTT_CONTROL_PORT_KEY, String.valueOf(boundPort));
        }
    }

    private MqttConnectRequest buildMqttConnectRequest(String clientDeviceThingName, List<String> caList, String host,
                                                        int port, MqttProtoVersion version) {
        final IotThingSpec thingSpec = getClientDeviceThingSpec(clientDeviceThingName);

        // TODO: use values from scenario instead of defaults for keepAlive, cleanSession
        return MqttConnectRequest.newBuilder()
                                 .setClientId(clientDeviceThingName)
                                 .setHost(host)
                                 .setPort(port)
                                 .setKeepalive(DEFAULT_MQTT_KEEP_ALIVE)
                                 .setCleanSession(CONNECT_CLEAR_SESSION)
                                 .setTls(buildTlsSettings(thingSpec, caList))
                                 .setProtocolVersion(version)
                                 .build();
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

        log.info("Discovered data for broker {}: ", brokerId);
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

    private Mqtt5Message buildMqtt5Message(int qos, boolean retain, @NonNull String topic, @NonNull String content) {
        MqttQoS mqttQoS = getMqttQoS(qos);
        return Mqtt5Message.newBuilder()
                            .setTopic(topic)
                            .setPayload(ByteString.copyFromUtf8(content))
                            .setQos(mqttQoS)
                            .setRetain(retain)
                            .build();
    }

    private void initMqttVersions() {
        mqttVersions.put(MQTT_VERSION_311, MqttProtoVersion.MQTT_PROTOCOL_V311);
        mqttVersions.put(MQTT_VERSION_50, MqttProtoVersion.MQTT_PROTOCOL_V50);
    }

    private MqttProtoVersion convertMqttVersion(String mqttVersion) {
        MqttProtoVersion version = mqttVersions.get(mqttVersion);

        if (version == null) {
            throw new IllegalArgumentException("Unknown MQTT version " + mqttVersion);
        }

        return version;
    }


    @Data
    @AllArgsConstructor
    private class MqttBrokerConnectionInfo {

        private String host;
        private Integer port;
        private List<String> caList;

    }
}


