/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.util;

import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public final class SslUtil {
    private SslUtil() {
    }

    /**
     * generate SSL socket factory.
     *
     * @param connectionParams MQTT connection parameters
     * @throws IOException on errors
     * @throws GeneralSecurityException on errors
     */
    public static SSLSocketFactory getSocketFactory(MqttLib.ConnectionParams connectionParams)
            throws IOException, GeneralSecurityException {
        return getSocketFactory(connectionParams.getCa(), connectionParams.getCert(), connectionParams.getKey());
    }

    /**
     * generate SSL socket factory.
     *
     * @param caCrtFile certificate authority
     * @param crtFile certification
     * @param keyFile private key
     * @throws IOException on errors
     * @throws GeneralSecurityException on errors
     */
    public static SSLSocketFactory getSocketFactory(final String caCrtFile, final String crtFile, final String keyFile)
            throws IOException, GeneralSecurityException {
        Security.addProvider(new BouncyCastleProvider());

        // load CA certificate
        X509Certificate caCert = null;

        InputStream bis = new ByteArrayInputStream(caCrtFile.getBytes());
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        while (bis.available() > 0) {
            caCert = (X509Certificate) cf.generateCertificate(bis);
        }

        // load client certificate
        bis = new ByteArrayInputStream(crtFile.getBytes());
        X509Certificate cert = null;
        while (bis.available() > 0) {
            cert = (X509Certificate) cf.generateCertificate(bis);
        }

        // CA certificate is used to authenticate server
        KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
        caKs.load(null, null);
        caKs.setCertificateEntry("ca-certificate", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(caKs);

        // client key and certificates are sent to server so it can authenticate
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("certificate", cert);

        // load client private key
        Object object;
        KeyPair key;
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(keyFile.getBytes())))) {
            object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            key = converter.getKeyPair((PEMKeyPair) object);

        }
        ks.setKeyEntry("private-key", key.getPrivate(), "".toCharArray(),
                new java.security.cert.Certificate[]{cert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory
                .getDefaultAlgorithm());
        kmf.init(ks, "".toCharArray());

        // finally, create SSL socket factory
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return context.getSocketFactory();
    }

}
