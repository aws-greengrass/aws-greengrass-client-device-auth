/* Copyright Amazon.com Inc. or its affiliates.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.dcm.persistence;

public final class KeyStoreFactory {

    private KeyStoreFactory() {
    }

    /**
     * Retrieves an appropriate KeyStore for the given key ID.
     *
     * @param id key id
     */
    public static KeyStore getKeyStore(String id) {
        // TODO: What type of KeyStore is this?
        // For now, just assume file
        return new FileKeyStoreImpl(id);
    }
}
