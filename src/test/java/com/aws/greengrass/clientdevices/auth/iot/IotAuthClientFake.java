/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * IoT Auth Client Fake allows test writers to set up valid and invalid certificates,
 * as well as Thing <-> certificate attachments without needing to manage mocks.
 */
public class IotAuthClientFake implements IotAuthClient {
    private final Map<String, Set<String>> thingToCerts = new HashMap<>();
    private final Set<String> activeCertIds = new HashSet<>();
    private final Set<String> inactiveCertIds = new HashSet<>();

    public void activateCert(String certPem) throws InvalidCertificateException {
        Certificate cert = Certificate.fromPem(certPem);
        String certId = cert.getCertificateId();

        activeCertIds.add(certId);
        inactiveCertIds.remove(certId);
    }

    public void deactivateCert(String certPem) throws InvalidCertificateException {
        Certificate cert = Certificate.fromPem(certPem);
        String certId = cert.getCertificateId();

        activeCertIds.remove(certId);
        inactiveCertIds.add(certId);
    }

    public void attachCertificateToThing(String thing, String certPem) throws InvalidCertificateException {
        Certificate cert = Certificate.fromPem(certPem);
        String certId = cert.getCertificateId();

        thingToCerts.computeIfAbsent(thing, (k) -> new HashSet<>()).add(certId);
    }

    public void detachCertificateFromThing(String thing, String certPem) throws InvalidCertificateException {
        Certificate cert = Certificate.fromPem(certPem);
        String certId = cert.getCertificateId();

        if (thingToCerts.containsKey(thing)) {
            thingToCerts.get(thing).remove(certId);
        }
    }

    @Override
    public Optional<String> getActiveCertificateId(String certificatePem) {
        // Current implementation returns empty optional if the cert is invalid
        // This isn't ideal behavior, but we'll stick with it for now
        Certificate cert;
        try {
            cert = Certificate.fromPem(certificatePem);
        } catch (InvalidCertificateException e) {
            return Optional.empty();
        }

        if (activeCertIds.contains(cert.getCertificateId())) {
            return Optional.of(cert.getCertificateId());
        }

        return Optional.empty();
    }

    @Override
    public Optional<Certificate> getIotCertificate(String certificatePem) throws InvalidCertificateException {
        Certificate cert = Certificate.fromPem(certificatePem);

        if (activeCertIds.contains(cert.getCertificateId())) {
            cert.setStatus(Certificate.Status.ACTIVE);
            return Optional.of(cert);
        }
        if (inactiveCertIds.contains(cert.getCertificateId())) {
            cert.setStatus(Certificate.Status.UNKNOWN);
            return Optional.of(cert);
        }

        return Optional.empty();
    }

    @Override
    public boolean isThingAttachedToCertificate(Thing thing, Certificate certificate) {
        if (thingToCerts.containsKey(thing.getThingName())) {
            return thingToCerts.get(thing.getThingName()).contains(certificate.getCertificateId());
        }
        return false;
    }
}
