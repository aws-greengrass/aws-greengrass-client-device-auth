/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.ipc;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.EncryptionUtils;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;

import java.io.IOException;
import java.util.Base64;

import static com.aws.greengrass.util.EncryptionUtils.CERTIFICATE_PEM_HEADER;

public final class IPCUtils {
    private static final Logger logger = LogManager.getLogger(IPCUtils.class);

    private IPCUtils() {

    }


    /**
     * utility method of encoding certificate to PEM format.
     *
     * @param certificatePem certificate pem string to encode
     * @return encoded pem with headers
     * @throws InvalidArgumentsError if unable to encode the certificate
     */
    @SuppressWarnings("PMD.PreserveStackTrace")
    public static String reEncodeCertToPem(String certificatePem) {
        if (!certificatePem.startsWith(CERTIFICATE_PEM_HEADER)) {
            try {
                certificatePem = EncryptionUtils.encodeToPem("CERTIFICATE",
                        // Use MIME decoder as it is more forgiving of formatting
                        Base64.getMimeDecoder().decode(certificatePem));
            } catch (IllegalArgumentException | IOException e) {
                logger.atWarn().log("Unable to convert certificate PEM", e);
                throw new InvalidArgumentsError("Unable to convert certificate PEM");
            }
        }
        return certificatePem;
    }
}
