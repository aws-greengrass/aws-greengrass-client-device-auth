/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.util;

import lombok.experimental.UtilityClass;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

@UtilityClass
public final class SslUtil {
    private static final char[] PASSWORD = "".toCharArray();

    // PKCS#8 format
    private static final String PEM_PRIVATE_START = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIVATE_END = "-----END PRIVATE KEY-----";

    // PKCS#1 format
    private static final String PEM_RSA_PRIVATE_START = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PEM_RSA_PRIVATE_END = "-----END RSA PRIVATE KEY-----";


    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Generates SSL socket factory.
     *
     * @param caList the list of certificate authority
     * @param crt the certificate
     * @param key the private key
     * @return instance of ssl socket factory with attached client credential and CA list
     * @throws IOException on errors
     * @throws GeneralSecurityException on errors
     */
    public static SSLSocketFactory getSocketFactory(final List<String> caList, final String crt, final String key)
            throws IOException, GeneralSecurityException {


        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");

        // CA certificates are used to authenticate server
        KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        caKeyStore.load(null, null);

        // load CA certificates
        int certNo = 0;
        for (String ca : caList) {
            Certificate caCertificate = getCerificate(ca);
            caKeyStore.setCertificateEntry(String.format("ca-certificate-%d", ++certNo), caCertificate);
        }
        trustManagerFactory.init(caKeyStore);

        // client key and certificates are sent to server so it can authenticate
        KeyStore credKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        credKeyStore.load(null, null);
        Certificate certificate = getCerificate(crt);
        credKeyStore.setCertificateEntry("certificate", certificate);

        // load client private key
        PrivateKey privateKey = loadPrivateKey(key);
        credKeyStore.setKeyEntry("private-key", privateKey, PASSWORD,  new Certificate[]{certificate});

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(credKeyStore, PASSWORD);

        // finally, create SSL socket factory
        // FIXME: probably that force to use only TLS 1.2
        // SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

        return sslContext.getSocketFactory();
    }

    private static Certificate getCerificate(final String certificatePem)
            throws IOException, GeneralSecurityException {

        try (PEMParser pemParser = new PEMParser(
                                    new InputStreamReader(
                                     new ByteArrayInputStream(certificatePem.getBytes())))) {
            X509CertificateHolder certHolder = (X509CertificateHolder) pemParser.readObject();
            JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter().setProvider("BC");
            return certificateConverter.getCertificate(certHolder);
        }
    }

    private static PrivateKey loadPrivateKey(String privateKeyPem) throws GeneralSecurityException, IOException {
        if (privateKeyPem.contains(PEM_PRIVATE_START)) {
            // PKCS#8 format
            privateKeyPem = privateKeyPem.replace(PEM_PRIVATE_START, "").replace(PEM_PRIVATE_END, "");
            privateKeyPem = privateKeyPem.replaceAll("\\s", "");

            byte[] pkcs8EncodedKey = Base64.getDecoder().decode(privateKeyPem);

            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8EncodedKey));

        } else if (privateKeyPem.contains(PEM_RSA_PRIVATE_START)) {
            // PKCS#1 format
            try (PEMParser pemParser = new PEMParser(
                                        new InputStreamReader(
                                         new ByteArrayInputStream(privateKeyPem.getBytes())))) {
                Object object = pemParser.readObject();
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
                return converter.getKeyPair((PEMKeyPair) object).getPrivate();
            }
        }

        throw new GeneralSecurityException("Not supported format of a private key");
    }
}
