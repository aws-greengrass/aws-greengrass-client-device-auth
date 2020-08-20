/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm.certificate;

import com.aws.iot.evergreen.deployment.DeviceConfiguration;

@SuppressWarnings("PMD.UnusedPrivateField")
public class CertificateManager {

    private final ConnectivityInfo connectivityInfo;
    private final DeviceConfiguration deviceConfiguration;

    /**
     * Constructor for Certificate Manager.
     *
     * @param connectivityInfo  connectivity information from CIS client
     * @param deviceConfiguration Device configuration for thing
     */
    public CertificateManager(ConnectivityInfo connectivityInfo,
                              DeviceConfiguration deviceConfiguration) {
        this.connectivityInfo = connectivityInfo;
        this.deviceConfiguration = deviceConfiguration;
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
