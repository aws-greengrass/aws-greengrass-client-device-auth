/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.exception.AuthorizationException;
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
import javax.inject.Inject;

public class DeviceAuthClient {
    private static final String ALLOW_ALL_SESSION = "ALLOW_ALL";

    private final Logger logger = LogManager.getLogger(DeviceAuthClient.class);

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
     * @param certificatePem Certificate PEM
     * @return Session ID
     */
    public String createSession(String certificatePem) {
        logger.atInfo().log("Creating new session");
        if (isGreengrassComponent(certificatePem)) {
            return ALLOW_ALL_SESSION;
        }
        return sessionManager.createSession(new Certificate(certificatePem, iotAuthClient));
    }

    private boolean isGreengrassComponent(String certificatePem) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream is = new StringInputStream(certificatePem)) {
                List<X509Certificate> certificateList = new ArrayList();
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
            if (caCertificate == null) {
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

    public void closeSession(String sessionId) throws AuthorizationException {
        sessionManager.closeSession(sessionId);
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
                    String.format("invalid session id (%s)", request.getSessionId()));
        }

        Certificate certificate = (Certificate) session.get(Certificate.NAMESPACE);
        Thing thing = new Thing(request.getClientId());
        // if thing name is already cached, proceed;
        // otherwise validate thing name with certificate, then cache thing name
        session.computeIfAbsent(thing.getNamespace(), (k) -> {
            if (iotAuthClient.isThingAttachedToCertificate(thing, certificate)) {
                return thing;
            }
            logger.atWarn().kv("sessionId", request.getSessionId()).kv("thing", request.getClientId())
                    .log("unable to validate Thing");
            return null;
        });
        return PermissionEvaluationUtils.isAuthorized(request.getOperation(), request.getResource(),
                groupManager.getApplicablePolicyPermissions(session));
    }
}
