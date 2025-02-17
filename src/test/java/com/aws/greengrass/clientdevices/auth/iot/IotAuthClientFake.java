/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import software.amazon.awssdk.core.pagination.sync.PaginatedResponsesIterator;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.core.pagination.sync.SyncPageFetcher;
import software.amazon.awssdk.services.greengrassv2.model.AssociatedClientDevice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * IoT Auth Client Fake allows test writers to set up valid and invalid certificates, as well as Thing <-> certificate
 * attachments without needing to manage mocks.
 */
public class IotAuthClientFake implements IotAuthClient {
    private final List<Supplier<String>> thingsAttachedToCore = new ArrayList<>();
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
        if (Objects.isNull(certificate)) {
            return false;
        }
        return isThingAttachedToCertificate(thing, certificate.getCertificateId());
    }

    @Override
    public boolean isThingAttachedToCertificate(Thing thing, String certificateId) {
        if (thingToCerts.containsKey(thing.getThingName())) {
            return thingToCerts.get(thing.getThingName()).contains(certificateId);
        }
        return false;
    }


    public void attachThingToCore(Supplier<String> thingName) {
        thingsAttachedToCore.add(thingName);
    }

    public void detachThingFromCore(Supplier<String> thingName) {
        thingsAttachedToCore.remove(thingName);
    }

    @Override
    public List<AssociatedClientDevice> getThingsAssociatedWithCoreDevice() {
        ThingsAttachedToCorePaginator paginator = new ThingsAttachedToCorePaginator(
                new ListClientDevicesAssociatedWithCoreDeviceResponseFetcher(this.thingsAttachedToCore));

        return paginator.stream().flatMap(Collection::stream).collect(Collectors.toList());
    }

    /**
     * Returns 1 AssociatedClientDevice per page.
     */
    private static class ThingsAttachedToCorePaginator implements SdkIterable<List<AssociatedClientDevice>> {
        private final SyncPageFetcher<List<AssociatedClientDevice>> syncPageFetcher;

        public ThingsAttachedToCorePaginator(SyncPageFetcher<List<AssociatedClientDevice>> syncPageFetcher) {
            this.syncPageFetcher = syncPageFetcher;
        }

        @Override
        public Iterator<List<AssociatedClientDevice>> iterator() {
            return PaginatedResponsesIterator.builder().nextPageFetcher(this.syncPageFetcher).build();
        }
    }

    private static class ListClientDevicesAssociatedWithCoreDeviceResponseFetcher
            implements SyncPageFetcher<List<AssociatedClientDevice>> {
        private final List<Supplier<String>> thingsAttachedToCore;
        private int currentPage;


        public ListClientDevicesAssociatedWithCoreDeviceResponseFetcher(List<Supplier<String>> thingsAttachedToCore) {
            this.thingsAttachedToCore = thingsAttachedToCore;
            this.currentPage = 0;
        }

        @Override
        public boolean hasNextPage(List<AssociatedClientDevice> previousPage) {
            if (thingsAttachedToCore.isEmpty()) {
                return false;
            }

            return currentPage < thingsAttachedToCore.size();
        }

        @Override
        public List<AssociatedClientDevice> nextPage(List<AssociatedClientDevice> previousPage) {
            if (hasNextPage(previousPage)) {
                Thing toReturn = Thing.of(thingsAttachedToCore.get(currentPage).get());
                AssociatedClientDevice device = AssociatedClientDevice.builder().associationTimestamp(Instant.now())
                        .thingName(toReturn.getThingName()).build();
                currentPage++;
                return Collections.singletonList(device);
            }

            return Collections.emptyList();
        }
    }
}
