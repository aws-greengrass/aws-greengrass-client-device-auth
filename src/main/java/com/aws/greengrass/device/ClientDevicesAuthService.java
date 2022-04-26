/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.authorization.AuthorizationHandler;
import com.aws.greengrass.authorization.exceptions.AuthorizationException;
import com.aws.greengrass.certificatemanager.CertificateManager;
import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.certificatemanager.certificate.CertificatesConfig;
import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.config.WhatHappened;
import com.aws.greengrass.dependency.ImplementsService;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.device.configuration.GroupConfiguration;
import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.session.MqttSessionFactory;
import com.aws.greengrass.device.session.SessionCreator;
import com.aws.greengrass.device.session.SessionManager;
import com.aws.greengrass.ipc.GetClientDeviceAuthTokenOperationHandler;
import com.aws.greengrass.ipc.SubscribeToCertificateUpdatesOperationHandler;
import com.aws.greengrass.ipc.VerifyClientDeviceIdentityOperationHandler;
import com.aws.greengrass.lifecyclemanager.PluginService;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.RetryUtils;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.PutCertificateAuthoritiesRequest;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService.SUBSCRIBE_TO_CERTIFICATE_UPDATES;

@SuppressWarnings("PMD.DataClass")
@ImplementsService(name = ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME)
public class ClientDevicesAuthService extends PluginService {
    public static final String CLIENT_DEVICES_AUTH_SERVICE_NAME = "aws.greengrass.clientdevices.Auth";
    public static final String DEVICE_GROUPS_TOPICS = "deviceGroups";
    public static final String CA_TYPE_TOPIC = "ca_type";
    public static final String CA_PASSPHRASE = "ca_passphrase";
    public static final String CERTIFICATES_KEY = "certificates";
    public static final String AUTHORITIES_TOPIC = "authorities";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
    private static final RetryUtils.RetryConfig SERVICE_EXCEPTION_RETRY_CONFIG =
            RetryUtils.RetryConfig.builder().initialRetryInterval(Duration.ofSeconds(3)).maxAttempt(Integer.MAX_VALUE)
                    .retryableExceptions(Arrays.asList(ThrottlingException.class, InternalServerException.class))
                    .build();

    private final GroupManager groupManager;

    private final CertificateManager certificateManager;

    private final GreengrassServiceClientFactory clientFactory;

    private final DeviceConfiguration deviceConfiguration;
    private final AuthorizationHandler authorizationHandler;
    private final GreengrassCoreIPCService greengrassCoreIPCService;
    private final IotAuthClient iotAuthClient;
    private final SessionManager sessionManager;

    /**
     * Constructor.
     *
     * @param topics                   Root Configuration topic for this service
     * @param groupManager             Group configuration management
     * @param certificateManager       Certificate management
     * @param clientFactory            Greengrass cloud service client factory
     * @param deviceConfiguration      core device configuration
     * @param authorizationHandler     authorization handler for IPC calls
     * @param greengrassCoreIPCService core IPC service
     * @param mqttSessionFactory       session factory to handling mqtt credentials
     * @param iotAuthClient            iot auth client
     * @param sessionManager session manager
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    @Inject
    public ClientDevicesAuthService(Topics topics, GroupManager groupManager, CertificateManager certificateManager,
                                    GreengrassServiceClientFactory clientFactory,
                                    DeviceConfiguration deviceConfiguration,
                                    AuthorizationHandler authorizationHandler,
                                    GreengrassCoreIPCService greengrassCoreIPCService,
                                    MqttSessionFactory mqttSessionFactory,
                                    IotAuthClient iotAuthClient,
                                    SessionManager sessionManager) {
        super(topics);
        this.groupManager = groupManager;
        this.certificateManager = certificateManager;
        this.clientFactory = clientFactory;
        this.deviceConfiguration = deviceConfiguration;
        this.authorizationHandler = authorizationHandler;
        this.greengrassCoreIPCService = greengrassCoreIPCService;
        this.iotAuthClient = iotAuthClient;
        this.sessionManager = sessionManager;
        SessionCreator.registerSessionFactory("mqtt", mqttSessionFactory);
        certificateManager.updateCertificatesConfiguration(new CertificatesConfig(this.getConfig()));
    }

    /**
     * Certificate Manager Service uses the following topic structure:
     * |---- configuration
     * |    |---- deviceGroups:
     * |         |---- definitions : {}
     * |         |---- policies : {}
     * |    |---- ca_type: [...]
     * |    |---- certificates: {}
     * |---- runtime
     * |    |---- ca_passphrase: "..."
     * |    |---- certificates:
     * |         |---- authorities: [...]
     */
    @Override
    protected void install() throws InterruptedException {
        super.install();
        this.config.lookupTopics(CONFIGURATION_CONFIG_KEY).subscribe((whatHappened, node) -> {
            if (whatHappened == WhatHappened.timestampUpdated || whatHappened == WhatHappened.interiorAdded) {
                return;
            }
            logger.atDebug().kv("why", whatHappened).kv("node", node).log();
            Topics deviceGroupTopics = this.config.lookupTopics(CONFIGURATION_CONFIG_KEY, DEVICE_GROUPS_TOPICS);
            Topic caTypeTopic = this.config.lookup(CONFIGURATION_CONFIG_KEY, CA_TYPE_TOPIC);

            if (whatHappened == WhatHappened.initialized) {
                updateDeviceGroups(whatHappened, deviceGroupTopics);
                updateCAType(caTypeTopic);
            } else if (node.childOf(DEVICE_GROUPS_TOPICS)) {
                updateDeviceGroups(whatHappened, deviceGroupTopics);
            } else if (node.childOf(CA_TYPE_TOPIC)) {
                if (caTypeTopic.getOnce() == null) {
                    return;
                }
                updateCAType(caTypeTopic);
            }
        });

    }

    @Override
    protected void startup() throws InterruptedException {
        certificateManager.startMonitors();
        super.startup();
    }

    @Override
    protected void shutdown() throws InterruptedException {
        super.shutdown();
        certificateManager.stopMonitors();
    }

    public CertificateManager getCertificateManager() {
        return certificateManager;
    }

    private void updateDeviceGroups(WhatHappened whatHappened, Topics deviceGroupsTopics) {
        try {
            groupManager.setGroupConfiguration(
                    OBJECT_MAPPER.convertValue(deviceGroupsTopics.toPOJO(), GroupConfiguration.class));
        } catch (IllegalArgumentException e) {
            logger.atError().kv("event", whatHappened)
                    .kv("node", deviceGroupsTopics.getFullName())
                    .setCause(e)
                    .log("Unable to parse group configuration");
            serviceErrored(e);
        }
    }

    private void updateCAType(Topic topic) {
        try {
            List<String> caTypeList = Coerce.toStringList(topic);
            logger.atDebug().kv("CA type", caTypeList).log("CA type list updated");

            if (Utils.isEmpty(caTypeList)) {
                logger.atDebug().log("CA type list null or empty. Defaulting to RSA");
                certificateManager.update(getPassphrase(), CertificateStore.CAType.RSA_2048);
            } else {
                if (caTypeList.size() > 1) {
                    logger.atWarn().log("Only one CA type is supported. Ignoring subsequent CAs in the list.");
                }
                String caType = caTypeList.get(0);
                certificateManager.update(getPassphrase(), CertificateStore.CAType.valueOf(caType));
            }

            List<String> caCerts = certificateManager.getCACertificates();
            uploadCoreDeviceCAs(caCerts);
            updateCACertificateConfig(caCerts);
            updateCaPassphraseConfig(certificateManager.getCaPassPhrase());
        } catch (KeyStoreException | IOException | CertificateEncodingException | IllegalArgumentException
                | CloudServiceInteractionException e) {
            serviceErrored(e);
        }
    }

    void updateCACertificateConfig(List<String> caCerts) {
        Topic caCertsTopic = getRuntimeConfig().lookup(CERTIFICATES_KEY, AUTHORITIES_TOPIC);
        caCertsTopic.withValue(caCerts);
    }

    void updateCaPassphraseConfig(String passphrase) {
        Topic caPassphrase = getRuntimeConfig().lookup(CA_PASSPHRASE);
        // TODO: This passphrase needs to be encrypted prior to storing in TLOG
        caPassphrase.withValue(passphrase);
    }

    private String getPassphrase() {
        Topic caPassphrase = getRuntimeConfig().lookup(CA_PASSPHRASE).dflt("");
        return Coerce.toString(caPassphrase);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void uploadCoreDeviceCAs(List<String> certificatePemList) {
        String thingName = Coerce.toString(deviceConfiguration.getThingName());
        PutCertificateAuthoritiesRequest request =
                PutCertificateAuthoritiesRequest.builder().coreDeviceThingName(thingName)
                        .coreDeviceCertificates(certificatePemList).build();
        try {
            RetryUtils.runWithRetry(SERVICE_EXCEPTION_RETRY_CONFIG,
                    () -> clientFactory.getGreengrassV2DataClient().putCertificateAuthorities(request),
                    "put-core-ca-certificate", logger);
        } catch (InterruptedException e) {
            logger.atError().cause(e).log("Put core CA certificates got interrupted");
            // interrupt the current thread so that higher-level interrupt handlers can take care of it
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.atError().cause(e)
                    .kv("coreThingName", thingName)
                    .log("Failed to put core CA certificates to cloud. Check that the core device's IoT policy grants"
                            + " the greengrass:PutCertificateAuthorities permission.");
            throw new CloudServiceInteractionException(
                    String.format("Failed to put core %s CA certificates to cloud", thingName), e);
        }
    }

    @Override
    public void postInject() {
        super.postInject();
        try {
            authorizationHandler.registerComponent(this.getName(),
                    new HashSet<>(Collections.singletonList(SUBSCRIBE_TO_CERTIFICATE_UPDATES)));
        } catch (AuthorizationException e) {
            logger.atError("initialize-cda-service-authorization-error", e)
                    .log("Failed to initialize the client device auth service with the Authorization module.");
        }
        greengrassCoreIPCService.setSubscribeToCertificateUpdatesHandler(context ->
                new SubscribeToCertificateUpdatesOperationHandler(context, certificateManager, authorizationHandler));
        greengrassCoreIPCService.setVerifyClientDeviceIdentityHandler(context ->
                new VerifyClientDeviceIdentityOperationHandler(context, iotAuthClient,
                        authorizationHandler));
        greengrassCoreIPCService.setGetClientDeviceAuthTokenHandler(context ->
                new GetClientDeviceAuthTokenOperationHandler(context, sessionManager, authorizationHandler));
    }
}
