/* Copyright Amazon.com Inc. or its affiliates.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.dcm.persistence;

import java.security.KeyPair;
import javax.security.cert.X509Certificate;


public interface KeyStore {
    /**
     * Retrieve the KeyPair associated with this key store.
     */
    KeyPair getKeyPair();

    /**
     * Retrieves the KeyStore public certificate.
     */
    X509Certificate getCertificate();

    /**
     * Update the KeyStore public certificate.
     *
     * @param certificate Updated certificate
     */
    void setCertificate(X509Certificate certificate);
}
