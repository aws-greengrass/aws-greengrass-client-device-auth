/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager;

import com.aws.greengrass.certificatemanager.certificate.CAHelper;
import com.aws.greengrass.certificatemanager.certificate.CertificateDownloader;
import com.aws.greengrass.certificatemanager.certificate.CertificateHelper;
import com.aws.greengrass.certificatemanager.certificate.CsrProcessingException;
import com.aws.greengrass.certificatemanager.model.DeviceConfig;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import lombok.NonNull;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class CertificateManager {
    private static final long DEFAULT_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 7; // 1 week

    private final CertificateDownloader certificateDownloader;
    private final Logger logger = LogManager.getLogger(CertificateManager.class);
    private final Map<DeviceConfig, String> deviceCertificateMap = new ConcurrentHashMap<>();

    private final CAHelper caHelper;

    /**
     * Constructor for Certificate Manager.
     *
     * @param certificateDownloader IoT device certificate downloader
     * @param caHelper              Helper class for managing certificate authorities
     */
    @Inject
    public CertificateManager(CertificateDownloader certificateDownloader, CAHelper caHelper) {
        this.certificateDownloader = certificateDownloader;
        this.caHelper = caHelper;
    }

    /**
     * Initialize the certificate manager.
     *
     * @throws KeyStoreException if unable to load the CA key store
     */
    void init(String caPassphrase) throws KeyStoreException {
        caHelper.init(caPassphrase);
    }

    /**
     * Set device configurations.
     *
     * @param deviceConfigurationList Updated device configuration list
     */
    void setDeviceConfigurations(List<DeviceConfig> deviceConfigurationList) {
        Set<DeviceConfig> currentDeviceSet = deviceCertificateMap.keySet();
        Set<DeviceConfig> newDeviceSet = deviceConfigurationList.stream().collect(Collectors.toSet());

        currentDeviceSet.forEach(d -> {
            if (!newDeviceSet.contains(d)) {
                logger.atDebug()
                        .kv("deviceArn", d.getDeviceArn())
                        .kv("certificateId", d.getCertificateId())
                        .log("removing device certificate");
                deviceCertificateMap.remove(d);
            }
        });

        // TODO: handle exceptions
        newDeviceSet.forEach(d -> {
            deviceCertificateMap.computeIfAbsent(d, k -> {
                logger.atInfo()
                        .kv("deviceArn", d.getDeviceArn())
                        .kv("certificateId", d.getCertificateId())
                        .log("downloading device certificate");
                return certificateDownloader.downloadSingleDeviceCertificate(d.getCertificateId());
            });
        });
    }

    /**
     * Return a map of device certificates, indexed by Device ARN.
     */
    Map<String, String> getDeviceCertificates() {
        return deviceCertificateMap.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().getDeviceArn(),
                entry -> entry.getValue()
        ));
    }

    /**
     * Return a list of CA certificates used to issue client certs.
     */
    List<String> getCACertificates() throws KeyStoreException, IOException, CertificateEncodingException {
        List<String> caList = new ArrayList<>();
        String caPem = CertificateHelper.toPem(caHelper.getCACertificate());
        caList.add(caPem);

        return caList;
    }

    /**
     * Subscribe to server certificate updates.
     * <p>
     * The certificate manager will save the given CSR and generate a new certificate under the following scenarios:
     *   1) The previous certificate is nearing expiry
     *   2) GGC connectivity information changes
     * Certificates will continue to be generated until the client calls unsubscribeFromCertificateUpdates.
     * </p>
     *
     * @param csr Certificate signing request
     * @param cb  Certificate consumer
     * @throws KeyStoreException if unable to access KeyStore
     * @throws CsrProcessingException if unable to process CSR
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void subscribeToServerCertificateUpdates(@NonNull String csr, @NonNull Consumer<X509Certificate> cb)
            throws KeyStoreException, CsrProcessingException {
        // BouncyCastle can throw RuntimeExceptions, and unfortunately it is not easy to detect
        // bad input beforehand. For now, just catch and re-throw a CsrProcessingException
        try {
            Instant now = Instant.now();
            PKCS10CertificationRequest pkcs10CertificationRequest =
                    CertificateHelper.getPKCS10CertificationRequestFromPem(csr);
            X509Certificate certificate = CertificateHelper.signServerCertificateRequest(
                    caHelper.getCACertificate(),
                    caHelper.getCAPrivateKey(),
                    pkcs10CertificationRequest,
                    Date.from(now),
                    Date.from(now.plusSeconds(DEFAULT_CERT_EXPIRY_SECONDS)));

            // TODO: Save cb
            // For now, just generate certificate and accept it
            cb.accept(certificate);
        } catch (KeyStoreException e) {
            logger.atError().setCause(e).log("unable to subscribe to certificate update");
            throw e;
        } catch (RuntimeException | OperatorCreationException | NoSuchAlgorithmException | CertificateException
                | InvalidKeyException | IOException e) {
            throw new CsrProcessingException(csr, e);
        }
    }

    /**
     * Subscribe to client certificate updates.
     * <p>
     * The certificate manager will save the given CSR and generate a new certificate under the following scenarios:
     *   1) The previous certificate is nearing expiry
     * Certificates will continue to be generated until the client calls unsubscribeFromCertificateUpdates.
     * </p>
     *
     * @param csr Certificate signing request
     * @param cb  Certificate consumer
     * @throws KeyStoreException if unable to access KeyStore
     * @throws CsrProcessingException if unable to process CSR
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void subscribeToClientCertificateUpdates(@NonNull String csr, @NonNull Consumer<X509Certificate> cb)
            throws KeyStoreException, CsrProcessingException {
        // BouncyCastle can throw RuntimeExceptions, and unfortunately it is not easy to detect
        // bad input beforehand. For now, just catch and re-throw a CsrProcessingException
        try {
            Instant now = Instant.now();
            PKCS10CertificationRequest pkcs10CertificationRequest =
                    CertificateHelper.getPKCS10CertificationRequestFromPem(csr);
            X509Certificate certificate = CertificateHelper.signClientCertificateRequest(
                    caHelper.getCACertificate(),
                    caHelper.getCAPrivateKey(),
                    pkcs10CertificationRequest,
                    Date.from(now),
                    Date.from(now.plusSeconds(DEFAULT_CERT_EXPIRY_SECONDS)));

            // TODO: Save cb
            // For now, just generate certificate and accept it
            cb.accept(certificate);
        } catch (KeyStoreException e) {
            logger.atError().setCause(e).log("unable to subscribe to certificate update");
            throw e;
        } catch (RuntimeException | OperatorCreationException | NoSuchAlgorithmException | CertificateException
                | InvalidKeyException | IOException e) {
            throw new CsrProcessingException(csr, e);
        }
    }

    /**
     * Unsubscribe from server certificate updates.
     *
     * @param cb Certificate consumer
     */
    public void unsubscribeFromCertificateUpdates(@NonNull Consumer<String> cb) {
        // TODO
    }
}
