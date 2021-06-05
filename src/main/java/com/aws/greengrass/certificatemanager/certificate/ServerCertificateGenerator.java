/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

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

    private final Consumer<X509Certificate> callback;

    /**
     * Constructor.
     *
     * @param subject          X500 subject
     * @param publicKey        Public Key
     * @param callback         Callback that consumes generated certificate
     * @param certificateStore CertificateStore instance
     */
    public ServerCertificateGenerator(X500Name subject, PublicKey publicKey, Consumer<X509Certificate> callback,
                                      CertificateStore certificateStore) {
        super(subject, publicKey, certificateStore);
        this.callback = callback;
    }

    /**
     * Regenerates certificate.
     *
     * @param connectivityInfoSupplier ConnectivityInfo Supplier
     * @throws KeyStoreException if unable to retrieve CA key/cert
     */
    @Override
    public synchronized void generateCertificate(Supplier<List<String>> connectivityInfoSupplier)
            throws KeyStoreException {
        Instant now = Instant.now(clock);

        // Always include "localhost" in server certificates so that components can
        // authenticate servers without disabling peer verification. Duplicate hostnames
        // be removed, so we can blindly add it here
        // Create a new list since the provided one may be immutable
        List<String> connectivityInfo = new ArrayList<>(connectivityInfoSupplier.get());
        connectivityInfo.add("localhost");

        try {
            certificate = CertificateHelper.signServerCertificateRequest(
                    certificateStore.getCACertificate(),
                    certificateStore.getCAPrivateKey(),
                    subject,
                    publicKey,
                    connectivityInfo,
                    Date.from(now),
                    Date.from(now.plusSeconds(DEFAULT_CERT_EXPIRY_SECONDS)));
        } catch (NoSuchAlgorithmException | OperatorCreationException | CertificateException | IOException e) {
            throw new CertificateGenerationException(e);
        }

        callback.accept(certificate);
    }
}
