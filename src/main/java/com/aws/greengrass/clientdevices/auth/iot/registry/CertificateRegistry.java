/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import org.bouncycastle.util.encoders.Hex;
import software.amazon.awssdk.utils.StringInputStream;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;


public class CertificateRegistry {
    private static final Logger logger = LogManager.getLogger(CertificateRegistry.class);
    // holds mapping of certificate ID -> last valid instant
    // size-bound by default cache size, evicts oldest written entry if the max size is reached
    private final Map<String, Instant> registry = Collections.synchronizedMap(
            new LinkedHashMap<String, Instant>(RegistryConfig.REGISTRY_CACHE_SIZE, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > RegistryConfig.REGISTRY_CACHE_SIZE;
                }
            });

    /**
     * Retrieve certificate by certificate pem.
     * @param certificatePem cert pem
     * @return certificate object
     */
    public Certificate getCertificateByPem(String certificatePem) {
        return getCertificateById(getCertificateId(certificatePem));
    }

    /**
     * Retrieve certificate by certificate id.
     * @param certificateId cert id
     * @return certificate object
     */
    public Certificate getCertificateById(String certificateId) {
        Instant lastValid = registry.get(certificateId);
        if (lastValid != null) {
            return new Certificate(certificateId, Certificate.Status.ACTIVE, lastValid);
        }
        return new Certificate(certificateId, Certificate.Status.INACTIVE);
    }

    /**
     * Update certificate.
     * @param certificate certificate object
     */
    public void updateCertificate(Certificate certificate) {
        // NOTE: The storage aspect of this will change soon. But for now
        //  only store ACTIVE certificates in the registry
        if (certificate.getStatus() == Certificate.Status.ACTIVE) {
            registry.put(certificate.getIotCertificateId(), certificate.getLastUpdated());
        } else {
            registry.remove(certificate.getIotCertificateId());
        }
    }

    private String getCertificateId(String certificatePem) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new StringInputStream(certificatePem));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            return new String(Hex.encode(digest.digest(cert.getEncoded())), "UTF-8");
        } catch (NoSuchAlgorithmException | CertificateException | UnsupportedEncodingException e) {
            logger.atWarn().cause(e).log("CertificatePem to CertificateId mapping could not be computed");
        }
        return null;
    }
}
