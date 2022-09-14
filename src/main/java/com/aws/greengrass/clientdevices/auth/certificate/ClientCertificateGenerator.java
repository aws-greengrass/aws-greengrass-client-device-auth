/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate;

import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ClientCertificateGenerator extends CertificateGenerator {
    private static final Logger logger = LogManager.getLogger(ClientCertificateGenerator.class);

    private final Consumer<X509Certificate[]> callback;

    /**
     * Constructor.
     *
     * @param subject            X500 subject
     * @param publicKey          Public Key
     * @param callback           Callback that consumes generated certificate
     * @param certificateStore   CertificateStore instance
     * @param certificatesConfig Certificate configuration
     * @param clock              clock
     */
    public ClientCertificateGenerator(X500Name subject,
                                      PublicKey publicKey,
                                      Consumer<X509Certificate[]> callback,
                                      CertificateStore certificateStore,
                                      CertificatesConfig certificatesConfig,
                                      Clock clock) {
        super(subject, publicKey, certificateStore, certificatesConfig, clock);
        this.callback = callback;
    }

    @Override
    public synchronized void generateCertificate(Supplier<List<String>> connectivityInfoSupplier, String reason)
            throws CertificateGenerationException {
        if (certificatesConfig.isCertificateRotationDisabled() && certificate != null) {
            logger.atWarn()
                    .kv("subject", subject)
                    .kv("certExpiry", getExpiryTime())
                    .log("Certificate rotation is disabled, current certificate will NOT be rotated");
            return;
        }

        Instant now = Instant.now(clock);

        try {
            certificate = CertificateHelper.issueClientCertificate(
                    certificateStore.getCACertificate(),
                    certificateStore.getCAPrivateKey(),
                    subject,
                    publicKey,
                    Date.from(now),
                    Date.from(now.plusSeconds(certificatesConfig.getClientCertValiditySeconds())));

            logger.atInfo()
                    .kv("subject", subject)
                    .kv("reason", reason)
                    .kv("certExpiry", getExpiryTime())
                    .log("New client certificate generated");

            X509Certificate caCertificate = certificateStore.getCACertificate();
            X509Certificate[] chain = {certificate, caCertificate};
            callback.accept(chain);
        } catch (NoSuchAlgorithmException | OperatorCreationException | CertificateException | IOException
                | KeyStoreException e) {
            throw new CertificateGenerationException(e);
        }
    }
}
