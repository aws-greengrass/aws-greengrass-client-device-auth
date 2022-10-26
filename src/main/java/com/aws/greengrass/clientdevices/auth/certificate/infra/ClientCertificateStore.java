/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.infra;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.lifecyclemanager.Kernel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;


/**
 * A KeyStore for client certificates. When a client device provides their certificate we store it
 * in this store it so that later we can refresh it later on using the cloud API.
 */
public class ClientCertificateStore {
    private static final String DEVICE_CERTIFICATE_DIR = "devices";
    private final Path workPath;

    // TODO: ideally we'd remove this, but right now our service init is somewhat fragile
    //  so directly extracting the plugin work dir from the Kernel is easiest
    @Inject
    public ClientCertificateStore(Kernel kernel) throws IOException {
        this(kernel.getNucleusPaths().workPath(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME));
    }

    /**
     * Create a certificate store for tests.
     *
     * @param   workPath Component work path to store certificate store
     */
    public ClientCertificateStore(Path workPath) {
        this.workPath = workPath;
    }

    /**
     * Checks if there is a certificate PEM already stored for a certificateId.
     * @param certificateId - The id of the stored certificate.
     */
    public boolean exists(String certificateId) {
        Path filePath = certificateIdToPath(certificateId);
        return Files.exists(filePath);
    }

    /**
     * Stores the PEM for a certificate.
     * @param certificateId - A Certificate ID
     * @param certificatePem - The Pem string of the certificate
     *
     * @throws IOException - If fails to write PEM
     */
    public void storePem(String certificateId, String certificatePem) throws IOException {
        saveCertificatePem(certificateIdToPath(certificateId), certificatePem);
    }

    /**
     * Removes the PEM for a certificateId alias.
     * @param certificateId - a certificate id
     * @throws IOException - if the certificate cannot be removed.
     */
    public void removePem(String certificateId) throws IOException {
        Path certPath = certificateIdToPath(certificateId);
        Files.deleteIfExists(certPath);
    }

    /**
     * Returns the PEM for a certificate.
     *
     * @param certificateId - The id of a Certificate
     */
    public Optional<String> getPem(String certificateId) {
        if (!exists(certificateId)) {
            return Optional.empty();
        }

        try {
            return Optional.of(loadDeviceCertificate(certificateId));
        } catch (IOException e) {
            // TODO: This was existing logic. Empty optional should be reserved for the case
            //  where no cert is present. This should throw.
            return Optional.empty();
        }
    }

    private void saveCertificatePem(Path filePath, String certificatePem) throws IOException {
        Files.createDirectories(filePath.getParent());
        try (OutputStream writeStream = Files.newOutputStream(filePath)) {
            writeStream.write(certificatePem.getBytes());
        }
    }

    private Path certificateIdToPath(String certificateId) {
        return workPath.resolve(DEVICE_CERTIFICATE_DIR).resolve(certificateId + ".pem");
    }

    public String loadDeviceCertificate(String certificateId) throws IOException {
        Path certPath = certificateIdToPath(certificateId);
        return new String(Files.readAllBytes(certPath));
    }
}
