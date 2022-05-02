/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ServerCertificateGenerator extends CertificateGenerator {
    private static final Logger logger = LogManager.getLogger(ServerCertificateGenerator.class);
    private final Consumer<X509Certificate> callback;
    private final CertificatesConfig certificatesConfig;

    /**
     * Constructor.
     *
     * @param subject            X500 subject
     * @param publicKey          Public Key
     * @param callback           Callback that consumes generated certificate
     * @param certificateStore   CertificateStore instance
     * @param certificatesConfig Certificate configuration
     */
    public ServerCertificateGenerator(X500Name subject, PublicKey publicKey, Consumer<X509Certificate> callback,
                                      CertificateStore certificateStore, CertificatesConfig certificatesConfig) {
        super(subject, publicKey, certificateStore);
        this.callback = callback;
        this.certificatesConfig = certificatesConfig;
    }

    @Override
    public synchronized void generateCertificate(Supplier<List<String>> connectivityInfoSupplier, String reason)
            throws KeyStoreException {
        Instant now = Instant.now(clock);

        // Always include "localhost" in server certificates so that components can
        // authenticate servers without disabling peer verification. Duplicate hostnames
        // be removed, so we can blindly add it here
        // Create a new list since the provided one may be immutable
        List<String> connectivityInfo = new ArrayList<>(connectivityInfoSupplier.get());
        connectivityInfo.add("localhost");

        logger.atInfo()
                .kv("subject", subject)
                .kv("reason", reason)
                .kv("connectivityInfo", connectivityInfo)
                .kv("previousCertExpiry", certificate == null ? "N/A" : getExpiryTime())
                .kv("previousCertValiditySeconds", certificate == null ? "N/A" : getValidity().getSeconds())
                .log("Generating new server certificate");

        try {
            certificate = CertificateHelper.signServerCertificateRequest(
                    certificateStore.getCACertificate(),
                    certificateStore.getCAPrivateKey(),
                    subject,
                    publicKey,
                    connectivityInfo,
                    Date.from(now),
                    Date.from(now.plusSeconds(certificatesConfig.getServerCertValiditySeconds())));
        } catch (NoSuchAlgorithmException | OperatorCreationException | CertificateException | IOException e) {
            logger.atError().cause(e).log("Failed to generate new server certificate");
            throw new CertificateGenerationException(e);
        }

        logger.atInfo()
                .kv("subject", subject)
                .kv("newCertExpiry", getExpiryTime())
                .kv("newCertValiditySeconds", getValidity().getSeconds())
                .log("Server certificate generation complete");

        callback.accept(certificate);
    }
}
