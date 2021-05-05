/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.util.ParseIPAddress;
import com.aws.greengrass.util.Utils;
import lombok.NonNull;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
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
import org.bouncycastle.util.io.pem.PemObject;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;
import software.amazon.awssdk.utils.ImmutableMap;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public final class CertificateHelper {
    public static final String KEY_TYPE_RSA = "RSA";
    public static final String RSA_SIGNING_ALGORITHM = "SHA256withRSA";
    public static final String KEY_TYPE_EC = "EC";
    public static final String ECDSA_SIGNING_ALGORITHM = "SHA256withECDSA";
    public static final ImmutableMap<String, String> CERTIFICATE_SIGNING_ALGORITHM =
            ImmutableMap.of(KEY_TYPE_RSA, RSA_SIGNING_ALGORITHM, KEY_TYPE_EC, ECDSA_SIGNING_ALGORITHM);

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

        String signingAlgorithm = CERTIFICATE_SIGNING_ALGORITHM.get(keyPair.getPrivate().getAlgorithm());
        final ContentSigner contentSigner = new JcaContentSignerBuilder(signingAlgorithm).setProvider("BC")
                .build(keyPair.getPrivate());

        return new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(builder.build(contentSigner));
    }

    /**
     * Generate server certificate from CSR.
     *
     * @param caCert                CA Certificate
     * @param caPrivateKey          CA Private Key
     * @param subject               server's subject
     * @param publicKey             server's public key
     * @param connectivityInfoItems GGC's connectivity info
     * @param notBefore             Certificate notBefore Date
     * @param notAfter              Certificate notAfter Date
     * @return                      X509 certificate
     * @throws NoSuchAlgorithmException   NoSuchAlgorithmException
     * @throws OperatorCreationException  OperatorCreationException
     * @throws CertificateException       CertificateException
     * @throws IOException                IOException
     */
    public static X509Certificate signServerCertificateRequest(@NonNull X509Certificate caCert,
                                                               @NonNull PrivateKey caPrivateKey,
                                                               @NonNull X500Name subject,
                                                               @NonNull PublicKey publicKey,
                                                               @NonNull List<ConnectivityInfo> connectivityInfoItems,
                                                               @NonNull Date notBefore,
                                                               @NonNull Date notAfter)
                        throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, IOException {
        return signCertificateRequest(caCert, caPrivateKey, subject, publicKey, connectivityInfoItems, notBefore,
                notAfter, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
    }

    /**
     * Generate client certificate from CSR.
     *
     * @param caCert        CA Certificate
     * @param caPrivateKey  CA Private Key
     * @param subject       client's subject
     * @param publicKey     client's public key
     * @param notBefore     Certificate notBefore Date
     * @param notAfter      Certificate notAfter Date
     * @return              X509 certificate
     * @throws NoSuchAlgorithmException   NoSuchAlgorithmException
     * @throws OperatorCreationException  OperatorCreationException
     * @throws CertificateException       CertificateException
     * @throws IOException                IOException
     */
    public static X509Certificate signClientCertificateRequest(@NonNull X509Certificate caCert,
                                                               @NonNull PrivateKey caPrivateKey,
                                                               @NonNull X500Name subject,
                                                               @NonNull PublicKey publicKey,
                                                               @NonNull Date notBefore,
                                                               @NonNull Date notAfter)
            throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, IOException {
        return signCertificateRequest(caCert, caPrivateKey, subject, publicKey, null, notBefore,
                notAfter, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
    }

    private static X509Certificate signCertificateRequest(@NonNull X509Certificate caCert,
                                                          @NonNull PrivateKey caPrivateKey,
                                                          @NonNull X500Name subject,
                                                          @NonNull PublicKey publicKey,
                                                          List<ConnectivityInfo> connectivityInfoItems,
                                                          @NonNull Date notBefore,
                                                          @NonNull Date notAfter,
                                                          @NonNull ExtendedKeyUsage keyUsage)
            throws NoSuchAlgorithmException, CertificateException, IOException, OperatorCreationException {
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                caCert, newSerialNumber(), notBefore, notAfter, subject, publicKey
        );

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(caCert))
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
                .addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(publicKey))
                .addExtension(Extension.extendedKeyUsage, true, keyUsage);

        if (!Utils.isEmpty(connectivityInfoItems)) {
            addSANFromConnectivityInfoToCertificate(connectivityInfoItems, builder);
        }

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

    private static void addSANFromConnectivityInfoToCertificate(List<ConnectivityInfo> connectivityInfoItems,
                                                                X509v3CertificateBuilder builder) throws IOException {
        final List<GeneralName> generalNamesArray = new ArrayList<>();
        for (ConnectivityInfo connectivityInfo : connectivityInfoItems) {
            String host = connectivityInfo.hostAddress();
            if (ParseIPAddress.isValidIP(host)) {
                generalNamesArray.add(new GeneralName(GeneralName.iPAddress, host));
            } else {
                generalNamesArray.add(new GeneralName(GeneralName.dNSName, host));
            }
        }
        final GeneralNames generalNames = new GeneralNames(generalNamesArray.toArray(new GeneralName[0]));
        builder.addExtension(Extension.subjectAlternativeName, false, generalNames);
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
