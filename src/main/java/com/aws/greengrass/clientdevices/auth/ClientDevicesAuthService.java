/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.authorization.AuthorizationHandler;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.CertificatesConfig;
import com.aws.greengrass.clientdevices.auth.certificate.listeners.CACertificateChainChangedListener;
import com.aws.greengrass.clientdevices.auth.certificate.usecases.ConfigureCertificateAuthorityUseCase;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.configuration.GroupConfiguration;
import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.exception.UseCaseException;
import com.aws.greengrass.clientdevices.auth.session.MqttSessionFactory;
import com.aws.greengrass.clientdevices.auth.session.SessionConfig;
import com.aws.greengrass.clientdevices.auth.session.SessionCreator;
import com.aws.greengrass.clientdevices.auth.session.SessionManager;
import com.aws.greengrass.clientdevices.auth.util.ResizableLinkedBlockingQueue;
import com.aws.greengrass.config.Node;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.config.WhatHappened;
import com.aws.greengrass.dependency.ImplementsService;
import com.aws.greengrass.ipc.AuthorizeClientDeviceActionOperationHandler;
import com.aws.greengrass.ipc.GetClientDeviceAuthTokenOperationHandler;
import com.aws.greengrass.ipc.SubscribeToCertificateUpdatesOperationHandler;
import com.aws.greengrass.ipc.VerifyClientDeviceIdentityOperationHandler;
import com.aws.greengrass.lifecyclemanager.PluginService;
import com.aws.greengrass.util.Coerce;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService.AUTHORIZE_CLIENT_DEVICE_ACTION;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService.GET_CLIENT_DEVICE_AUTH_TOKEN;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService.SUBSCRIBE_TO_CERTIFICATE_UPDATES;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService.VERIFY_CLIENT_DEVICE_IDENTITY;

@SuppressWarnings("PMD.DataClass")
@ImplementsService(name = ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME)
public class ClientDevicesAuthService extends PluginService {
    public static final String CLIENT_DEVICES_AUTH_SERVICE_NAME = "aws.greengrass.clientdevices.Auth";
    public static final String DEVICE_GROUPS_TOPICS = "deviceGroups";
    public static final String PERFORMANCE_TOPIC = "performance";
    public static final String MAX_ACTIVE_AUTH_TOKENS_TOPIC = "maxActiveAuthTokens";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
    public static final String CLOUD_REQUEST_QUEUE_SIZE_TOPIC = "cloudRequestQueueSize";
    public static final String MAX_CONCURRENT_CLOUD_REQUESTS_TOPIC = "maxConcurrentCloudRequests";

    private final GroupManager groupManager;

    private final CertificateManager certificateManager;


    private final ClientDevicesAuthServiceApi clientDevicesAuthServiceApi;
    private final AuthorizationHandler authorizationHandler;
    private final GreengrassCoreIPCService greengrassCoreIPCService;
    // Limit the queue size before we start rejecting requests
    private static final int DEFAULT_CLOUD_CALL_QUEUE_SIZE = 100;
    private static final int DEFAULT_THREAD_POOL_SIZE = 1;
    public static final int DEFAULT_MAX_ACTIVE_AUTH_TOKENS = 2500;
    // Create a threadpool for calling the cloud. Single thread will be used by default.
    private final ThreadPoolExecutor cloudCallThreadPool;
    private final UseCases useCases;
    private int cloudCallQueueSize;
    private CDAConfiguration prevCdaConfiguration;
    private CDAConfiguration cdaConfiguration;


    /**
     * Constructor.
     *
     * @param topics                      Root Configuration topic for this service
     * @param groupManager                Group configuration management
     * @param certificateManager          Certificate management
     * @param authorizationHandler        authorization handler for IPC calls
     * @param greengrassCoreIPCService    core IPC service
     * @param mqttSessionFactory          session factory to handling mqtt credentials
     * @param sessionManager              session manager
     * @param clientDevicesAuthServiceApi client devices service api handle
     * @param useCases                    UseCases service
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    @Inject
    public ClientDevicesAuthService(Topics topics, GroupManager groupManager,
                                    CertificateManager certificateManager,
                                    AuthorizationHandler authorizationHandler,
                                    GreengrassCoreIPCService greengrassCoreIPCService,
                                    MqttSessionFactory mqttSessionFactory,
                                    SessionManager sessionManager,
                                    ClientDevicesAuthServiceApi clientDevicesAuthServiceApi,
                                    UseCases useCases) {
        super(topics);
        cloudCallQueueSize = DEFAULT_CLOUD_CALL_QUEUE_SIZE;
        cloudCallQueueSize = getValidCloudCallQueueSize(topics);
        cloudCallThreadPool = new ThreadPoolExecutor(1,
                DEFAULT_THREAD_POOL_SIZE, 60, TimeUnit.SECONDS,
                new ResizableLinkedBlockingQueue<>(cloudCallQueueSize));
        cloudCallThreadPool.allowCoreThreadTimeOut(true); // act as a cached threadpool
        this.clientDevicesAuthServiceApi = clientDevicesAuthServiceApi;
        this.groupManager = groupManager;
        this.certificateManager = certificateManager;
        this.authorizationHandler = authorizationHandler;
        this.greengrassCoreIPCService = greengrassCoreIPCService;
        SessionCreator.registerSessionFactory("mqtt", mqttSessionFactory);
        certificateManager.updateCertificatesConfiguration(new CertificatesConfig(getConfig()));
        sessionManager.setSessionConfig(new SessionConfig(getConfig()));

        // Initialize the use cases, so we can use them anywhere in the app
        this.useCases = useCases;
        this.useCases.init(getContext());
    }


    private int getValidCloudCallQueueSize(Topics topics) {
        int newSize = Coerce.toInt(
                topics.findOrDefault(DEFAULT_CLOUD_CALL_QUEUE_SIZE,
                        CONFIGURATION_CONFIG_KEY, PERFORMANCE_TOPIC, CLOUD_REQUEST_QUEUE_SIZE_TOPIC));
        if (newSize <= 0) {
            logger.atWarn().log("{} illegal size, will not change the queue size from {}",
                    CLOUD_REQUEST_QUEUE_SIZE_TOPIC, cloudCallQueueSize);
            return cloudCallQueueSize; // existing size
        }
        return newSize;
    }


    @Override
    protected void install() throws InterruptedException {
        super.install();
        registerEventListeners();
        subscribeToConfigChanges();
    }

    private void subscribeToConfigChanges() {
        onConfigurationChanged();
        config.lookupTopics(CONFIGURATION_CONFIG_KEY).subscribe(this::configChangeHandler);
    }

    private void onConfigurationChanged() {
        if (cdaConfiguration != null) {
            prevCdaConfiguration = cdaConfiguration;
        }

        try {
            cdaConfiguration = CDAConfiguration.from(getConfig());
            useCases.provide(CDAConfiguration.class, cdaConfiguration);
        } catch (URISyntaxException e) {
            serviceErrored(e);
        }
    }

    private void configChangeHandler(WhatHappened whatHappened, Node node) {
        if (whatHappened == WhatHappened.timestampUpdated || whatHappened == WhatHappened.interiorAdded) {
            return;
        }
        logger.atDebug().kv("why", whatHappened).kv("node", node).log();
        onConfigurationChanged();
        // NOTE: This should not live here. The service doesn't have to have knowledge about where/how
        // keys are stored
        Topics deviceGroupTopics = this.config.lookupTopics(CONFIGURATION_CONFIG_KEY, DEVICE_GROUPS_TOPICS);

        try {
            // NOTE: Extract this to a method these are infrastructure concerns.
            int threadPoolSize = Coerce.toInt(this.config.findOrDefault(DEFAULT_THREAD_POOL_SIZE,
                    CONFIGURATION_CONFIG_KEY, PERFORMANCE_TOPIC, MAX_CONCURRENT_CLOUD_REQUESTS_TOPIC));
            if (threadPoolSize >= cloudCallThreadPool.getCorePoolSize()) {
                cloudCallThreadPool.setMaximumPoolSize(threadPoolSize);
            }
        } catch (IllegalArgumentException e) {
            logger.atWarn().log("Unable to update CDA threadpool size due to {}", e.getMessage());
        }

        if (whatHappened != WhatHappened.initialized && node != null && node.childOf(CLOUD_REQUEST_QUEUE_SIZE_TOPIC)) {
            // NOTE: Extract this to a method these are infrastructure concerns.
            BlockingQueue<Runnable> q = cloudCallThreadPool.getQueue();
            if (q instanceof ResizableLinkedBlockingQueue) {
                cloudCallQueueSize = getValidCloudCallQueueSize(this.config);
                ((ResizableLinkedBlockingQueue) q).resize(cloudCallQueueSize);
            }
        }

        try {
            if (whatHappened == WhatHappened.initialized || node == null) {
                updateDeviceGroups(whatHappened, deviceGroupTopics);
                useCases.get(ConfigureCertificateAuthorityUseCase.class).apply(null);
            } else if (node.childOf(DEVICE_GROUPS_TOPICS)) {
                updateDeviceGroups(whatHappened, deviceGroupTopics);
            } else if (cdaConfiguration.hasCAConfigurationChanged(prevCdaConfiguration)) {
                useCases.get(ConfigureCertificateAuthorityUseCase.class).apply(null);
            }
        } catch (UseCaseException e) {
            serviceErrored(e);
        }
    }

    private void registerEventListeners() {
        context.get(CACertificateChainChangedListener.class).listen();
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

    @Override
    public void postInject() {
        super.postInject();
        try {
            authorizationHandler.registerComponent(this.getName(),
                    new HashSet<>(Arrays.asList(SUBSCRIBE_TO_CERTIFICATE_UPDATES,
                            VERIFY_CLIENT_DEVICE_IDENTITY,
                            GET_CLIENT_DEVICE_AUTH_TOKEN,
                            AUTHORIZE_CLIENT_DEVICE_ACTION)));
        } catch (com.aws.greengrass.authorization.exceptions.AuthorizationException e) {
            logger.atError("initialize-cda-service-authorization-error", e)
                    .log("Failed to initialize the client device auth service with the Authorization module.");
        }
        greengrassCoreIPCService.setSubscribeToCertificateUpdatesHandler(context ->
                new SubscribeToCertificateUpdatesOperationHandler(context, certificateManager, authorizationHandler));
        greengrassCoreIPCService.setVerifyClientDeviceIdentityHandler(context ->
                new VerifyClientDeviceIdentityOperationHandler(context, clientDevicesAuthServiceApi,
                        authorizationHandler, cloudCallThreadPool));
        greengrassCoreIPCService.setGetClientDeviceAuthTokenHandler(context ->
                new GetClientDeviceAuthTokenOperationHandler(context, clientDevicesAuthServiceApi, authorizationHandler,
                        cloudCallThreadPool));
        greengrassCoreIPCService.setAuthorizeClientDeviceActionHandler(context ->
                new AuthorizeClientDeviceActionOperationHandler(context, clientDevicesAuthServiceApi,
                        authorizationHandler));
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

    void updateCACertificateConfig(List<String> caCerts) {
        // NOTE: This shouldn't exist - This is just being exposed for test shouldn't be public API
        cdaConfiguration.updateCACertificates(caCerts);
    }

    @Override
    protected CompletableFuture<Void> close(boolean waitForDependers) {
        // shutdown the threadpool in close, not in shutdown() because it is created
        // and injected in the constructor and we won't be able to restart it after it stops.
        cloudCallThreadPool.shutdown();
        return super.close(waitForDependers);
    }
}
