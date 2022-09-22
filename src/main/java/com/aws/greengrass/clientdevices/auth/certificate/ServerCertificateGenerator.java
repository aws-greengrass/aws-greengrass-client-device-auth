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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ServerCertificateGenerator extends CertificateGenerator {
    private static final Logger logger = LogManager.getLogger(ServerCertificateGenerator.class);
    private final BiConsumer<X509Certificate, X509Certificate[]> callback;

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
    public ServerCertificateGenerator(X500Name subject,
                                      PublicKey publicKey,
                                      BiConsumer<X509Certificate, X509Certificate[]> callback,
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

        // Always include "localhost" in server certificates so that components can
        // authenticate servers without disabling peer verification. Duplicate hostnames
        // be removed, so we can blindly add it here
        // Create a new list since the provided one may be immutable
        List<String> connectivityInfo = new ArrayList<>(connectivityInfoSupplier.get());
        connectivityInfo.add("localhost");

        try {
            certificate = CertificateHelper.issueServerCertificate(
                    certificateStore.getCACertificate(),
                    certificateStore.getCAPrivateKey(),
                    subject,
                    publicKey,
                    connectivityInfo,
                    Date.from(now),
                    Date.from(now.plusSeconds(certificatesConfig.getServerCertValiditySeconds())));
        } catch (NoSuchAlgorithmException | OperatorCreationException | CertificateException | IOException
                | KeyStoreException e) {
            logger.atError().cause(e).log("Failed to generate new server certificate");
            throw new CertificateGenerationException(e);
        }

        logger.atInfo()
                .kv("subject", subject)
                .kv("reason", reason)
                .kv("connectivityInfo", connectivityInfo)
                .kv("certExpiry", getExpiryTime())
                .log("New server certificate generated");

        callback.accept(certificate, certificateStore.getCaCertificateChain());
    }
}
