/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.helpers;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper.CERTIFICATE_SIGNING_ALGORITHM;

public final class CertificateTestHelpers {

    private CertificateTestHelpers() {
    }

    static {
        // If not added "BC" is not recognized as the security provider
        Security.addProvider(new BouncyCastleProvider());
    }



    /**
     * Generates X509 certificates that could be CAs or leaf certs. This is intended to be used for testing in scenarios
     * where we want to create intermediate CAs
     *
     * @param issuer             Issuer of the  certificate
     * @param subject            Subject for the certificate
     * @param certificateKeyPair Pair of keys for the certificates. The public key of this pair will get added to the
     *                           certificate
     * @param issuerKeyPair      Keypair of the issuer (or who signs) the certificate. The private key from this pair
     *                           will be
     *                           used to sign the certificate.
     * @param isCA               Whether the cert can be used as a CA to sign more certificates
     *
     */
    public static X509Certificate issueCertificate(X500Name issuer, X500Name subject, KeyPair certificateKeyPair,
                                                   KeyPair issuerKeyPair, Boolean isCA)
            throws CertificateException, OperatorCreationException, CertIOException {
        String signingAlgorithm = CERTIFICATE_SIGNING_ALGORITHM.get(certificateKeyPair.getPrivate().getAlgorithm());
        Instant now = Instant.now();

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer,
                new BigInteger(160, new SecureRandom()),
                Date.from(now),
                Date.from(now.plusSeconds(10)),
                subject,
                certificateKeyPair.getPublic()
        );

        if (isCA) {
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        }

        ContentSigner signer = new JcaContentSignerBuilder(signingAlgorithm).build(issuerKeyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    /**
     * Verifies if one certificate was signed by another.
     *
     * @param issuerCA  X509Certificate issuer cert
     * @param certificate X509Certificate signed cert
     */
    public static boolean wasCertificateIssuedBy(X509Certificate issuerCA, X509Certificate certificate) throws
            CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509Certificate> leafCertificate = Arrays.asList(certificate);
        CertPath leafCertPath = cf.generateCertPath(leafCertificate);

        try {
            CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
            TrustAnchor trustAnchor = new TrustAnchor(issuerCA, null);
            PKIXParameters validationParams = new PKIXParameters(new HashSet<>(Collections.singletonList(trustAnchor)));
            validationParams.setRevocationEnabled(false);
            cpv.validate(leafCertPath, validationParams);
            return true;
        } catch (CertPathValidatorException | InvalidAlgorithmParameterException | NoSuchAlgorithmException  e) {
            return false;
        }
    }
}
