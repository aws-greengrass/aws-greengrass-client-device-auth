/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.exception.AuthorizationException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidSessionException;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.SessionManager;
import com.aws.greengrass.clientdevices.auth.session.attribute.Attribute;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;

public class DeviceAuthClient {
    private static final String ALLOW_ALL_SESSION = "ALLOW_ALL";
    private static final Logger logger = LogManager.getLogger(DeviceAuthClient.class);

    private final SessionManager sessionManager;
    private final CertificateStore certificateStore;
    private final PermissionEvaluationUtils permissionEvaluationUtils;

    /**
     * Constructor.
     *
     * @param sessionManager            Session manager
     * @param certificateStore          Certificate store
     * @param permissionEvaluationUtils Permission Evaluation Utils
     */
    @Inject
    public DeviceAuthClient(SessionManager sessionManager, CertificateStore certificateStore,
                            PermissionEvaluationUtils permissionEvaluationUtils) {
        this.sessionManager = sessionManager;
        this.certificateStore = certificateStore;
        this.permissionEvaluationUtils = permissionEvaluationUtils;
    }

    /**
     * Check if a given certificate belongs to an internal Greengrass component such as the MQTT Bridge.
     *
     * @param certificatePem certificate in PEM form.
     * @return true if the certificate was provided to a Greengrass component.
     */
    public boolean isGreengrassComponent(String certificatePem) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream is = new StringInputStream(certificatePem)) {
                List<X509Certificate> leafCertificate;

                // Note: We are just reading the leaf certificate (the one that CDA signed and provided to the
                // client/server component) and checking that one against out leaf level core CA certificate.
                if (is.available() > 0) {
                    try {
                        leafCertificate = Arrays.asList((X509Certificate) cf.generateCertificate(is));
                        CertPath leafCertPath = cf.generateCertPath(leafCertificate);
                        return isGreengrassComponent(leafCertPath);
                    } catch (CertificateException e) {
                        // This doesn't necessarily mean there's a bad certificate.
                        // It could be that the string just has some extra newlines
                        // characters. Log warning and continue. If this is a meaningful
                        // failure, then let chain validation catch it.
                        logger.atWarn().log("Unable to parse entire certificate chain");
                    }
                }
            }
        } catch (CertificateException | IOException e) {
            logger.atError().cause(e).kv("pem", certificatePem).log("Unable to parse certificate");
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

    /**
     * Determine whether the requested device operation is allowed.
     *
     * @param request authorization request including operation, resource, sessionId, clientId
     * @return if device is authorized
     * @throws AuthorizationException if session is invalid
     */
    public boolean canDevicePerform(AuthorizationRequest request) throws AuthorizationException {
        logger.atDebug().kv("sessionId", request.getSessionId()).kv("action", request.getOperation())
                .kv("resource", request.getResource()).log("Processing authorization request");

        // TODO: Remove this workaround
        if (request.getSessionId().equals(ALLOW_ALL_SESSION)) {
            return true;
        }

        Session session = sessionManager.findSession(request.getSessionId());
        if (session == null) {
            throw new InvalidSessionException(String.format("Invalid session ID (%s)", request.getSessionId()));
        }
        // Allow all operations from internal components
        // Keep the workaround above (ALLOW_ALL_SESSION) for Moquette since it is using the older session management
        if (session.getSessionAttribute(Attribute.COMPONENT) != null) {
            return true;
        }

        return permissionEvaluationUtils.isAuthorized(request, session);
    }
}
