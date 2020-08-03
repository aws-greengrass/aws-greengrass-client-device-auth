/* Copyright Amazon.com Inc. or its affiliates.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.dcm.persistence;

import java.security.KeyPair;
import javax.security.cert.X509Certificate;

public class FileKeyStoreImpl implements KeyStore {
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public FileKeyStoreImpl(String id) {
        // TODO
    }

    @Override
    public KeyPair getKeyPair() {
        // TODO
        return null;
    }

    @Override
    public X509Certificate getCertificate() {
        // TODO
        return null;
    }

    @Override
    public void setCertificate(X509Certificate certificate) {
        // TODO
    }
}
