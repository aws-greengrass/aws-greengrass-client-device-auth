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
import com.aws.greengrass.testing.mqtt.client.control.implementation.addon.EventStorageImpl;
import com.aws.greengrass.testing.mqtt.client.control.implementation.addon.MqttMessageEvent;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotCertificateSpec;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import com.google.protobuf.ByteString;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContextOptions;
import software.amazon.awssdk.iot.discovery.DiscoveryClient;
import software.amazon.awssdk.iot.discovery.DiscoveryClientConfig;
import software.amazon.awssdk.iot.discovery.model.ConnectivityInfo;
import software.amazon.awssdk.iot.discovery.model.DiscoverResponse;
import software.amazon.awssdk.iot.discovery.model.GGGroup;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

import static software.amazon.awssdk.iot.discovery.DiscoveryClient.TLS_EXT_ALPN;

@Log4j2
@ScenarioScoped
public class MqttControlSteps {
    private static final String DEFAULT_CLIENT_DEVICE_POLICY_CONFIG = "/configs/iot/basic_client_device_policy.yaml";

    private static final int DEFAULT_MQTT_TIMEOUT_SEC = 30;

    private static final int DEFAULT_CONTROL_GRPC_PORT = 47_619;

    private static final int DEFAULT_MQTT_KEEP_ALIVE = 60;

    private static final int MQTT5_REASON_SUCCESS = 0;
    private static final int MQTT5_GRANTED_QOS_2 = 2;

    private static final boolean CONNECT_CLEAR_SESSION = true;

    private static final boolean PUBLISH_RETAIN = false;

    private static final Integer SUBSCRIPTION_ID = null;                        // NOTE: do not set for IoT Core !!!
    private static final boolean SUBSCRIBE_NO_LOCAL = false;
    private static final boolean SUBSCRIBE_RETAIN_AS_PUBLISHED = false;
    private static final Mqtt5RetainHandling SUBSCRIBE_RETAIN_HANDLING
            = Mqtt5RetainHandling.MQTT5_RETAIN_DO_NOT_SEND_AT_SUBSCRIPTION;


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
     * Pass IoT Thing certificate for connection into specified client component.
     *
     * @param clientDeviceId string user defined client device id
     * @param componentId    componentId of MQTT client
     * @param brokerId       broker id
     */
    @And("I connect device {string} on {word} to {string}")
    public void connect(String clientDeviceId, String componentId, String brokerId) {
        // get agent control by componentId
        AgentControl agentControl = getAgentControl(componentId);

        // request for new MQTT connection
        final String clientDeviceThingName = getClientDeviceThingName(clientDeviceId);

        final MqttConnectRequest request = buildMqttConnectRequest(clientDeviceThingName, brokerId);
        log.info("Creating MQTT connection as Thing {} on {} to {} with request {}", clientDeviceThingName, componentId,
                    brokerId, request);
        ConnectionControl connectionControl = agentControl.createMqttConnection(request, connectionEvents);

        log.info("Connection with broker {} established", brokerId);

        setConnectionControl(connectionControl, clientDeviceThingName);
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
        final String region = resourcesContext.region()
                                              .toString();
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

    private IotPolicySpec createDefaultClientDevicePolicy(String policyNameOverride) throws IOException {
        return iotSteps.createPolicy(DEFAULT_CLIENT_DEVICE_POLICY_CONFIG, policyNameOverride);
    }

    private void startMqttControl() throws IOException {
        if (!engineControl.isEngineRunning()) {
            // TODO: use port autoselection and save actual bound port from getBoundPort() for future references
            engineControl.startEngine(DEFAULT_CONTROL_GRPC_PORT, engineEvents);
            final int boundPort = engineControl.getBoundPort();
            log.info("MQTT clients control started gRPC service on port {}", boundPort);
        }
    }

    private MqttConnectRequest buildMqttConnectRequest(String clientDeviceThingName, String brokerId) {
        final IotThingSpec thingSpec = getClientDeviceThingSpec(clientDeviceThingName);

        // TODO: use values from scenario instead of defaults
        return MqttConnectRequest.newBuilder()
                                 .setClientId(clientDeviceThingName)
                                 .setHost(getBrokerHost(brokerId))
                                 .setPort(getBrokerPort(brokerId))
                                 .setKeepalive(DEFAULT_MQTT_KEEP_ALIVE)
                                 .setCleanSession(CONNECT_CLEAR_SESSION)
                                 .setTls(buildTlsSettings(thingSpec, brokerId))
                                 .setProtocolVersion(MqttProtoVersion.MQTT_PROTOCOL_V50)
                                 .build();
    }

    private IotThingSpec getClientDeviceThingSpec(String clientDeviceThingName) {
        return resources.trackingSpecs(IotThingSpec.class)
                        .filter(t -> clientDeviceThingName.equals(t.resource().thingName()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Thing spec is not found"));
    }

    private TLSSettings buildTlsSettings(IotThingSpec thingSpec, String brokerId) {
        return TLSSettings.newBuilder()
                          .setCa(getBrokerCa(brokerId))
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
        if (response.getGGGroups() != null) {
            final Optional<GGGroup> groupOpt = response.getGGGroups()
                                                       .stream()
                                                       .findFirst();
            if (groupOpt.isPresent()) {
                final GGGroup group = groupOpt.get();
                final String ca = group.getCAs()
                                       .get(0);
                putBrokerCa(brokerId, ca);
                for (ConnectivityInfo info : group.getCores()
                                                  .get(0)
                                                  .getConnectivity()) {
                    final String host = info.getHostAddress();
                    final Integer port = info.getPortNumber();
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), mqttTimeoutSec * 1000);
                        putBrokerHost(brokerId, host);
                        putBrokerPort(brokerId, port);
                        log.info("Core Device ConnectivityInfo, endpoint {}:{} is reachable", host, port);
                        break;
                    } catch (IOException e) {
                        log.warn("Core Device ConnectivityInfo, endpoint {}:{} is not reachable", host, port);
                    }
                }

            }
        }
    }

    private String getBrokerHost(String brokerId) {
        return scenarioContext.get("host-" + brokerId);
    }

    private void putBrokerHost(String brokerId, String host) {
        scenarioContext.put("host-" + brokerId, host);
    }

    private int getBrokerPort(String brokerId) {
        return Integer.parseInt(scenarioContext.get("port-" + brokerId));
    }

    private void putBrokerPort(String brokerId, Integer port) {
        scenarioContext.put("port-" + brokerId, port.toString());
    }

    private String getAgentId(String componentName) {
        return componentName;
    }

    private String getBrokerCa(String brokerId) {
        return scenarioContext.get("ca-" + brokerId);
    }

    private void putBrokerCa(String brokerId, String ca) {
        scenarioContext.put("ca-" + brokerId, ca);
    }

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
}
