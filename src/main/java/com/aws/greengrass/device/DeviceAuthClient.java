/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.iot.Thing;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import software.amazon.awssdk.utils.StringInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

public class DeviceAuthClient {
    private static final String ALLOW_ALL_SESSION = "ALLOW_ALL";
    private static final Logger logger = LogManager.getLogger(DeviceAuthClient.class);

    private final SessionManager sessionManager;
    private final GroupManager groupManager;
    private final IotAuthClient iotAuthClient;
    private final CertificateStore certificateStore;

    /**
     * Constructor.
     *
     * @param sessionManager Session manager
     * @param groupManager   Group manager
     * @param iotAuthClient  Iot auth client
     * @param certificateStore Certificate store
     */
    @Inject
    public DeviceAuthClient(SessionManager sessionManager, GroupManager groupManager,
                            IotAuthClient iotAuthClient, CertificateStore certificateStore) {
        this.sessionManager = sessionManager;
        this.groupManager = groupManager;
        this.iotAuthClient = iotAuthClient;
        this.certificateStore = certificateStore;
    }

    /**
     * Create session from certificate PEM.
     *
     * @param certificatePem Client device/component certificate PEM
     * @return Session ID
     * @throws AuthenticationException if failed to authenticate
     */
    @SuppressWarnings("PMD.AvoidUncheckedExceptionsInSignatures")
    public String createSession(String certificatePem) throws AuthenticationException {
        logger.atInfo().log("Creating new session");
        if (isGreengrassComponent(certificatePem)) {
            return ALLOW_ALL_SESSION;
        }
        return createSessionForClientDevice(certificatePem);
    }

    private boolean isGreengrassComponent(String certificatePem) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream is = new StringInputStream(certificatePem)) {
                List<X509Certificate> certificateList = new ArrayList<>();
                while (is.available() > 0) {
                    try {
                        certificateList.add((X509Certificate) cf.generateCertificate(is));
                    } catch (CertificateException e) {
                        // This doesn't necessarily mean there's a bad certificate.
                        // It could be that the string just has some extra newlines
                        // characters. Log warning and continue. If this is a meaningful
                        // failure, then let chain validation catch it.
                        logger.atWarn().log("Unable to parse entire certificate chain");
                        break;
                    }
                }
                return isGreengrassComponent(cf.generateCertPath(certificateList));
            }
        } catch (CertificateException | IOException e) {
            logger.atError().cause(e).kv("pem", certificatePem)
                    .log("Unable to parse certificate");
        }
        return false;
    }

    private boolean isGreengrassComponent(CertPath certPath) {
        try {
            X509Certificate caCertificate = certificateStore.getCACertificate();
            if (certPath.getCertificates() == null || certPath.getCertificates().isEmpty() || caCertificate == null) {
                return false;
            }
            CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
            TrustAnchor trustAnchor = new TrustAnchor(caCertificate, null);
            PKIXParameters validationParams = new PKIXParameters(new HashSet<>(Collections.singletonList(trustAnchor)));
            validationParams.setRevocationEnabled(false);
            cpv.validate(certPath, validationParams);
            return true;
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            logger.atError().cause(e).log("Unable to load certificate validator");
        } catch (CertPathValidatorException e) {
            logger.atDebug().log("Certificate was not issued by local CA");
        } catch (KeyStoreException e) {
            logger.atError().cause(e).log("Unable to load CA keystore");
        }

        return false;
    }

    @SuppressWarnings("PMD.AvoidUncheckedExceptionsInSignatures")
    private String createSessionForClientDevice(String certificatePem) throws AuthenticationException {
        Optional<String> certificateId;
        try {
            certificateId = iotAuthClient.getActiveCertificateId(certificatePem);
        } catch (CloudServiceInteractionException e) {
            throw new AuthenticationException("Failed to verify certificate with cloud", e);
        }
        if (!certificateId.isPresent()) {
            throw new AuthenticationException("Certificate isn't active");
        }

        String certificateHash = CertificateStore.computeCertificatePemHash(certificatePem);
        // for simplicity, synchronously store the PEM on disk.
        try {
            certificateStore.storeDeviceCertificateIfNotPresent(certificateHash, certificatePem);
        } catch (IOException e) {
            // allow to continue even failed to store, session health check will invalid the session later
            logger.atError().cause(e).kv("certificatePem", certificatePem)
                    .log("Failed to store certificate on disk");
        }
        return sessionManager.createSession(new Certificate(certificateHash, certificateId.get()));
    }

    public void closeSession(String sessionId) throws AuthorizationException {
        sessionManager.closeSession(sessionId);
    }

    /**
     * Attach thing to session in order to authorize.
     *
     * @param sessionId Session ID
     * @param thingName IoT Thing Name
     * @throws AuthenticationException if session not valid or verify thing identity error out
     */
    @SuppressWarnings("PMD.AvoidUncheckedExceptionsInSignatures")
    public void attachThing(String sessionId, String thingName) throws AuthenticationException {
        logger.atDebug()
                .kv("sessionId", sessionId)
                .kv("thingName", thingName)
                .log("Attaching thing to session");

        // Workaround for bridge component
        if (ALLOW_ALL_SESSION.equals(sessionId)) {
            return;
        }

        Session session = sessionManager.findSession(sessionId);
        if (session == null) {
            throw new AuthenticationException(
                    String.format("Invalid session ID (%s)", sessionId));
        }

        Certificate certificate = (Certificate) session.getAttributeProvider(Certificate.NAMESPACE);
        try {
            session.computeAttributeProviderIfAbsent(Thing.NAMESPACE, k -> getValidThing(thingName, certificate));
        } catch (CloudServiceInteractionException e) {
            throw new AuthenticationException("Failed to verify thing identity with cloud", e);
        }
    }

    private Thing getValidThing(String thingName, Certificate certificate) {
        Thing thing = new Thing(thingName);
        if (iotAuthClient.isThingAttachedToCertificate(thing, certificate)) {
            return thing;
        }
        logger.atWarn().kv("iotCertificateId", certificate.getIotCertificateId()).kv("thing", thingName)
                .log("Unable to validate thing");
        return null;
    }

    /**
     * Determine whether the requested device operation is allowed.
     *
     * @param request authorization request including operation, resource, sessionId, clientId
     * @return if device is authorized
     * @throws AuthorizationException if session is invalid
     */
    public boolean canDevicePerform(AuthorizationRequest request) throws AuthorizationException {
        logger.atDebug()
                .kv("sessionId", request.getSessionId())
                .kv("action", request.getOperation())
                .kv("resource", request.getResource())
                .log("Processing authorization request");

        // TODO: Remove this workaround
        if (request.getSessionId().equals(ALLOW_ALL_SESSION)) {
            return true;
        }

        Session session = sessionManager.findSession(request.getSessionId());
        if (session == null) {
            throw new AuthorizationException(
                    String.format("Invalid session ID (%s)", request.getSessionId()));
        }

        return PermissionEvaluationUtils.isAuthorized(request.getOperation(), request.getResource(),
                groupManager.getApplicablePolicyPermissions(session));
    }
}
