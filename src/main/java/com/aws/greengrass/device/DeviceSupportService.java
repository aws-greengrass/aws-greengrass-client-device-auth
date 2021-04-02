/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.certificatemanager.CertificateManager;
import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.config.Node;
import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.config.WhatHappened;
import com.aws.greengrass.dependency.ImplementsService;
import com.aws.greengrass.device.configuration.GroupConfiguration;
import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.Thing;
import com.aws.greengrass.lifecyclemanager.PluginService;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import java.util.List;
import javax.inject.Inject;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;

@ImplementsService(name = DeviceSupportService.DEVICE_SUPPORT_SERVICE_NAME)
public class DeviceSupportService extends PluginService {
    public static final String DEVICE_SUPPORT_SERVICE_NAME = "aws.greengrass.DeviceSupport";
    public static final String DEVICE_GROUPS_TOPICS = "deviceGroups";
    public static final String CA_TYPE_TOPIC = "ca_type";
    public static final String CA_PASSPHRASE = "ca_passphrase";
    public static final String CERTIFICATES_KEY = "certificates";
    public static final String AUTHORITIES_TOPIC = "authorities";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);

    private final GroupManager groupManager;

    private final SessionManager sessionManager;

    private final CertificateManager certificateManager;

    private Topics deviceGroupsTopics;

    /**
     * Constructor.
     *
     * @param topics         Root Configuration topic for this service
     * @param groupManager   Group configuration management
     * @param sessionManager Session management
     * @param certificateManager Certificate management
     */
    @Inject
    public DeviceSupportService(Topics topics, GroupManager groupManager, SessionManager sessionManager,
                                CertificateManager certificateManager) {
        super(topics);
        this.groupManager = groupManager;
        this.sessionManager = sessionManager;
        this.certificateManager = certificateManager;
    }

    /**
     * Certificate Manager Service uses the following topic structure:
     *  |---- configuration
     *  |    |---- deviceGroups:
     *  |         |---- definitions : {}
     *  |         |---- policies : {}
     *  |    |---- ca_type: [...]
     *  |---- runtime
     *  |    |---- ca_passphrase: "..."
     *  |    |---- certificates
     *  |         |---- authorities: [...]
     */
    @Override
    protected void install() throws InterruptedException {
        super.install();
        //handleConfiguration
        this.deviceGroupsTopics = this.config.lookupTopics(CONFIGURATION_CONFIG_KEY, DEVICE_GROUPS_TOPICS);
        this.deviceGroupsTopics.subscribe(this::handleConfigurationChange);
        this.config.lookup(CONFIGURATION_CONFIG_KEY, CA_TYPE_TOPIC).subscribe(this::updateCAType);
    }

    public CertificateManager getCertificateManager() {
        return certificateManager;
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void handleConfigurationChange(WhatHappened whatHappened, Node childNode) {
        try {
            groupManager.setGroupConfiguration(
                    OBJECT_MAPPER.convertValue(this.deviceGroupsTopics.toPOJO(), GroupConfiguration.class));
        } catch (IllegalArgumentException e) {
            logger.atError().kv("service", DEVICE_SUPPORT_SERVICE_NAME).kv("event", whatHappened)
                    .kv("node", this.deviceGroupsTopics.getFullName()).kv("value", this.deviceGroupsTopics)
                    .setCause(e).log("Unable to parse group configuration");
        }
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void updateCAType(WhatHappened what, Topic topic) {
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
            updateCACertificateConfig(caCerts);
        } catch (KeyStoreException | IOException | CertificateEncodingException | IllegalArgumentException e) {
            serviceErrored(e);
        }
    }

    void updateCACertificateConfig(List<String> caCerts) {
        Topic caCertsTopic = getRuntimeConfig().lookup(CERTIFICATES_KEY, AUTHORITIES_TOPIC);
        caCertsTopic.withValue(caCerts);
    }

    private String getPassphrase() {
        // TODO: This passphrase needs to be encrypted prior to storing in TLOG
        Topic caPassphrase = getRuntimeConfig().lookup(CA_PASSPHRASE)
                .dflt(CertificateStore.generateRandomPassphrase());
        return Coerce.toString(caPassphrase);
    }

    /**
     * determine device operation authorization.
     *
     * @param request authorization request including operation, resource, sessionId, clientId
     * @return if device is authorized
     * @throws AuthorizationException if session not existed or expired
     */
    public boolean canDevicePerform(AuthorizationRequest request) throws AuthorizationException {
        Session session = sessionManager.findSession(request.getSessionId());
        if (session == null) {
            throw new AuthorizationException(
                    String.format("session %s isn't existed or expired", request.getSessionId()));
        }

        Certificate certificate = (Certificate) session.get(Certificate.NAMESPACE);
        Thing thing = new Thing(request.getClientId());
        // if thing name is already cached, proceed;
        // otherwise validate thing name with certificate, then cache thing name
        session.computeIfAbsent(thing.getNamespace(), (k) -> thing.isCertificateAttached(certificate) ? thing : null);
        return PermissionEvaluationUtils.isAuthorized(request.getOperation(), request.getResource(),
                groupManager.getApplicablePolicyPermissions(session));
    }
}
