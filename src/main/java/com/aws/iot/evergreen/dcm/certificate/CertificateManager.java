/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm.certificate;

import com.aws.iot.evergreen.deployment.DeviceConfiguration;
import com.aws.iot.evergreen.gcmclient.GCMClientException;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@SuppressWarnings("PMD.UnusedPrivateField")
public class CertificateManager {

    private final ConnectivityInfo connectivityInfo;
    private final CloudCertificateGenerator cloudCertificateGenerator;
    private final DeviceConfiguration deviceConfiguration;

    /**
     * Constructor for Certificate Manager.
     *
     * @param connectivityInfo  connectivity information from CIS client
     * @param cloudCertificateGenerator certificate generator
     * @param deviceConfiguration Device configuration for thing
     */
    public CertificateManager(ConnectivityInfo connectivityInfo,
                              CloudCertificateGenerator cloudCertificateGenerator,
                              DeviceConfiguration deviceConfiguration) {
        this.connectivityInfo = connectivityInfo;
        this.cloudCertificateGenerator = cloudCertificateGenerator;
        this.deviceConfiguration = deviceConfiguration;
    }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private void certificateGenerationWorkFlow(KeyPair keyPair,
                                               String thingName,
                                               ConnectivityInfo connectivityInfo,
                                               CloudCertificateGenerator cloudCertificateGenerator) throws
            IOException,
            OperatorCreationException,
            GCMClientException,
            CertificateException {
        // TODO: Handle workflow cancellation and restart logic
        // Get CSR
        String csr = CertificateRequestGenerator.createCSR(keyPair,
                thingName,
                connectivityInfo.ipAddresses,
                connectivityInfo.dnsNames);

        // Get new certificate
        @SuppressWarnings("PMD.UnusedLocalVariable")
        X509Certificate certificate = cloudCertificateGenerator.generateNewCertificate(csr);
    }

    public void addGreengrassServerCertificate(String id) {
        //TODO: add a map of certs with given key id. Set keyPair member variable.
    }

    public void addDeviceCertificate(String thingArn, String id) {
        //TODO: add device certificate
    }

    public void updateConnectivityInfo(ConnectivityInfo connectivityInfo) {
        //TODO: Given connectivity info compare versions and trigger certificate generation
        //this.connectivityInfo = connectivityInfo;
    }

    public void regenerateServerCertificates() {
        //TODO: Loop through certs and regenerate
        //keyPair = KeyStoreFactory.getKeyStore(id).getKeyPair();
        //call certificateGenerationWorkFlow
    }
}