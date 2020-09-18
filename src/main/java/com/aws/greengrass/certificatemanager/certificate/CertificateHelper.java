/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import lombok.NonNull;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;


public final class CertificateHelper {
    private static final String X500_DISTINGUISHED_NAME_COUNTRY_NAME = "US";
    private static final String X500_DISTINGUISHED_NAME_STATE_OR_PROVINCE_NAME = "Washington";
    private static final String X500_DISTINGUISHED_NAME_LOCALITY_NAME = "Seattle";
    private static final String X500_DISTINGUISHED_NAME_ORGANIZATION_NAME = "Amazon.com Inc.";
    private static final String X500_DISTINGUISHED_NAME_ORGANIZATION_UNIT_NAME = "Amazon Web Services";

    private CertificateHelper() {
    }

    static {
        // If not added "BC" is not recognized as the security provider
        Security.addProvider(new BouncyCastleProvider());
    }

    private static BigInteger newSerialNumber() {
        return new BigInteger(160, new SecureRandom());
    }

    /**
     * Create CA certificate.
     *
     * @param keyPair           CA Keypair
     * @param caNotBefore       CA NotBefore Date
     * @param caNotAfter        CA NotAfter Date
     * @param caCommonName      CA Common Name
     * @return                  X509 certificate
     * @throws NoSuchAlgorithmException  Unsupported signing algorithm
     * @throws CertIOException           CertIOException
     * @throws OperatorCreationException OperatorCreationException
     * @throws CertificateException      CertificateException
     */
    public static X509Certificate createCACertificate(@NonNull KeyPair keyPair,
                                                      @NonNull Date caNotBefore,
                                                      @NonNull Date caNotAfter,
                                                      @NonNull String caCommonName)
            throws NoSuchAlgorithmException, CertIOException, OperatorCreationException, CertificateException {

        final X500Name issuer = getIssuer(caCommonName);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, newSerialNumber(),
                caNotBefore, caNotAfter, issuer, keyPair.getPublic()
        );

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                .addExtension(Extension.subjectKeyIdentifier, false,
                        extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));

        final ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(keyPair.getPrivate());

        return new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(builder.build(contentSigner));
    }

    /**
     * Generate server certificate from CSR.
     *
     * @param caCert        CA Certificate
     * @param caPrivateKey  CA Private Key
     * @param csr           PKCS10 Certificate Signing Request
     * @param notBefore     Certificate notBefore Date
     * @param notAfter      Certificate notAfter Date
     * @return              X509 certificate
     * @throws NoSuchAlgorithmException   NoSuchAlgorithmException
     * @throws InvalidKeyException        InvalidKeyException
     * @throws OperatorCreationException  OperatorCreationException
     * @throws CertificateException       CertificateException
     * @throws CertIOException            CertIOException
     */
    public static X509Certificate signServerCertificateRequest(@NonNull X509Certificate caCert,
                                                         @NonNull PrivateKey caPrivateKey,
                                                         @NonNull PKCS10CertificationRequest csr,
                                                         @NonNull Date notBefore,
                                                         @NonNull Date notAfter) throws
            NoSuchAlgorithmException, InvalidKeyException, OperatorCreationException,
            CertificateException, CertIOException {
        return signCertificateRequest(caCert, caPrivateKey, csr, notBefore, notAfter,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
    }

    /**
     * Generate client certificate from CSR.
     *
     * @param caCert        CA Certificate
     * @param caPrivateKey  CA Private Key
     * @param csr           PKCS10 Certificate Signing Request
     * @param notBefore     Certificate notBefore Date
     * @param notAfter      Certificate notAfter Date
     * @return              X509 certificate
     * @throws NoSuchAlgorithmException   NoSuchAlgorithmException
     * @throws InvalidKeyException        InvalidKeyException
     * @throws OperatorCreationException  OperatorCreationException
     * @throws CertificateException       CertificateException
     * @throws CertIOException            CertIOException
     */
    public static X509Certificate signClientCertificateRequest(@NonNull X509Certificate caCert,
                                                         @NonNull PrivateKey caPrivateKey,
                                                         @NonNull PKCS10CertificationRequest csr,
                                                         @NonNull Date notBefore,
                                                         @NonNull Date notAfter) throws
            NoSuchAlgorithmException, InvalidKeyException, OperatorCreationException,
            CertificateException, CertIOException {
        return signCertificateRequest(caCert, caPrivateKey, csr, notBefore, notAfter,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
    }

    private static X509Certificate signCertificateRequest(@NonNull X509Certificate caCert,
                                                          @NonNull PrivateKey caPrivateKey,
                                                          @NonNull PKCS10CertificationRequest csr,
                                                          @NonNull Date notBefore,
                                                          @NonNull Date notAfter,
                                                          @NonNull ExtendedKeyUsage keyUsage) throws
            NoSuchAlgorithmException, InvalidKeyException, OperatorCreationException,
            CertificateException, CertIOException {
        JcaPKCS10CertificationRequest jcaRequest = new JcaPKCS10CertificationRequest(csr);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                caCert, newSerialNumber(), notBefore,
                notAfter, jcaRequest.getSubject(), jcaRequest.getPublicKey()
        );

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(caCert))
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
                .addExtension(Extension.subjectKeyIdentifier, false,
                        extUtils.createSubjectKeyIdentifier(jcaRequest.getPublicKey()))
                .addExtension(Extension.extendedKeyUsage, true, keyUsage);

        addSANFromCSRToCertificate(csr, builder);

        final ContentSigner contentSigner = new JcaContentSignerBuilder(
                caCert.getSigAlgName()).setProvider("BC").build(caPrivateKey);

        return new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(builder.build(contentSigner));
    }

    /**
     * Parse CSR string into PKCS10CertificationRequest.
     *
     * @param csrString PEM encoded CSR
     * @return PKCS10CertificationRequest
     * @throws IOException If unable to read CSR
     */
    public static PKCS10CertificationRequest getPKCS10CertificationRequestFromPem(String csrString) throws IOException {
        ByteArrayInputStream pemStream = new ByteArrayInputStream(csrString.getBytes(StandardCharsets.UTF_8));
        Reader pemReader = new BufferedReader(new InputStreamReader(pemStream, StandardCharsets.UTF_8));
        try (PEMParser pemParser = new PEMParser(pemReader)) {
            return (PKCS10CertificationRequest)pemParser.readObject();
        }
    }

    /**
     * Convert an X509Certificate into a PEM encoded string.
     *
     * @param certificate Certificate to encode
     * @return PEM encoded X509 certificate
     * @throws IOException If unable to read certificate
     * @throws CertificateEncodingException If unable to get certificate encoding
     */
    public static String toPem(X509Certificate certificate) throws IOException, CertificateEncodingException {
        PemObject pemObject = new PemObject("CERTIFICATE", certificate.getEncoded());

        try (StringWriter str = new StringWriter();
             JcaPEMWriter pemWriter = new JcaPEMWriter(str)) {
            pemWriter.writeObject(pemObject);
            pemWriter.close(); // Need to explicitly close this since JcaPEMWriter is a buffered writer
            return str.toString();
        }
    }

    private static void addSANFromCSRToCertificate(final PKCS10CertificationRequest csr,
                                                   final X509v3CertificateBuilder builder) throws CertIOException {
        Attribute[] attributes = csr.getAttributes();

        for (Attribute attr : attributes) {
            if (attr.getAttrType().equals(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)) {
                Extensions extensions = Extensions.getInstance(attr.getAttrValues().getObjectAt(0));
                Enumeration e = extensions.oids();
                while (e.hasMoreElements()) {
                    ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) e.nextElement();
                    if (Extension.subjectAlternativeName.equals(oid)) {
                        Extension ext = extensions.getExtension(oid);
                        builder.addExtension(oid, ext.isCritical(), ext.getParsedValue());
                        break;
                    }
                }
            }
        }
    }

    private static X500Name getIssuer(String commonName) {
        X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());
        nameBuilder.addRDN(BCStyle.C, X500_DISTINGUISHED_NAME_COUNTRY_NAME);
        nameBuilder.addRDN(BCStyle.O, X500_DISTINGUISHED_NAME_ORGANIZATION_NAME);
        nameBuilder.addRDN(BCStyle.OU, X500_DISTINGUISHED_NAME_ORGANIZATION_UNIT_NAME);
        nameBuilder.addRDN(BCStyle.ST, X500_DISTINGUISHED_NAME_STATE_OR_PROVINCE_NAME);
        nameBuilder.addRDN(BCStyle.L, X500_DISTINGUISHED_NAME_LOCALITY_NAME);
        nameBuilder.addRDN(BCStyle.CN, commonName);

        return nameBuilder.build();
    }
}
