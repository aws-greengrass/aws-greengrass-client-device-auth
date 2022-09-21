/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.infra;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import lombok.Getter;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Simple store for server certificate generators.
 */
public class CertificateGeneratorRegistry {
    @Getter
    private final Set<CertificateGenerator> certificateGenerators = new CopyOnWriteArraySet<>();


    /**
     * Stores generators for server certificates that can be called upon specific triggers.
     * @param generator A certificate generator
     */
    public void registerGenerator(CertificateGenerator generator) {
        certificateGenerators.add(generator);
    }

    /**
     * Remove a generator from the registry.
     * @param generator  A certificate generator
     */
    public void removeGenerator(CertificateGenerator generator) {
        certificateGenerators.remove(generator);
    }
}
