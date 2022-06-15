/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.security.KeyPair;
import java.util.List;
import javax.security.auth.x500.X500Principal;

/**
 * Certificate Request Generator that creates a CSR for given key pair, connectivity info and common name.
 */
public final class CertificateRequestGenerator {
    private static final String CSR_COUNTRY = "US";
    private static final String CSR_PROVINCE = "Washington";
    private static final String CSR_LOCALITY = "Seattle";
    private static final String CSR_ORGANIZATION = "Amazon.com Inc.";
    private static final String CSR_ORGANIZATIONAL_UNIT = "Amazon Web Services";

    private CertificateRequestGenerator() {
    }

    /**
     * Returns a PEM encoded Certificate Signing request string for given connectivity info.
     *
     * @param keyPair     public/private key pair to generate cert
     * @param thingName   common name for cert subject
     * @param ipAddresses ip addresses for SAN extensions
     * @param dnsNames    dnsNames for SAN extensions
     * @return String
     * @throws OperatorCreationException fails to build CSR content signer with the given private key
     * @throws IOException fails to add SAN Extension to CSR builder
     */
    public static String createCSR(KeyPair keyPair,
                                   String thingName,
                                   List<InetAddress> ipAddresses,
                                   List<String> dnsNames) throws OperatorCreationException, IOException {
        // TODO: Remove once Moquette and Bridge no longer call this API
        return createCSR(keyPair, thingName);
    }

    /**
     * Returns a PEM encoded Certificate Signing request string for given connectivity info.
     *
     * @param keyPair     public/private key pair to generate cert
     * @param thingName   common name for cert subject
     * @return String
     * @throws OperatorCreationException fails to build CSR content signer with the given private key
     * @throws IOException fails to add SAN Extension to CSR builder
     */
    public static String createCSR(KeyPair keyPair, String thingName) throws OperatorCreationException, IOException {
        // Create PKCS10 certificate request
        X500Principal x500Principal = getx500PrincipalForThing(thingName);
        PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(
                x500Principal, keyPair.getPublic());

        // Create signature and sign the certificate Request
        String keyType = CertificateHelper.CERTIFICATE_SIGNING_ALGORITHM.get(keyPair.getPrivate().getAlgorithm());
        JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder(keyType);
        ContentSigner signer = csBuilder.build(keyPair.getPrivate());

        PKCS10CertificationRequest csr = p10Builder.build(signer);
        return getEncodedString(csr);
    }

    /**
     * Generates X500 Distinguished Name (DN) sequence with given common name.
     *
     * @param commonName Certificate common name
     * @return RDN sequence.
     */
    private static X500Principal getx500PrincipalForThing(String commonName) {
        String distinguishedName = String.format("CN=%s,C=%s,ST=%s,L=%s,O=%s,OU=%s",
                commonName,
                CSR_COUNTRY,
                CSR_PROVINCE,
                CSR_LOCALITY,
                CSR_ORGANIZATION,
                CSR_ORGANIZATIONAL_UNIT);

        return new X500Principal(distinguishedName);
    }

    /**
     * Gets PEM Encoded Certificate String from given CSR.
     *
     * @param request PKCS 10 Certificate Request (CSR)
     * @return String -  PEM Encoded Certificate String
     * @throws IOException Fails to read PEM encoded string from CSR
     */
    private static String getEncodedString(PKCS10CertificationRequest request) throws IOException {
        PemObject pemObject = new PemObject("CERTIFICATE REQUEST", request.getEncoded());

        try (StringWriter str = new StringWriter();
             JcaPEMWriter pemWriter = new JcaPEMWriter(str)) {
            pemWriter.writeObject(pemObject);
            pemWriter.close();
            return str.toString();
        }
    }
}
