/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.clientdevices.auth.certificate.infra.ClientCertificateStore;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.clientdevices.auth.session.SessionManager;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ClientDeviceFetcher {
    private final SessionManager sessionManager;
    private final ThingRegistry thingRegistry;
    private final CertificateRegistry certificateRegistry;
    private final ClientCertificateStore certificateStore;

    @Inject
    public ClientDeviceFetcher(SessionManager sessionManager,
                               ThingRegistry thingRegistry,
                               CertificateRegistry certificateRegistry,
                               ClientCertificateStore certificateStore) {
        this.sessionManager = sessionManager;
        this.thingRegistry = thingRegistry;
        this.certificateRegistry = certificateRegistry;
        this.certificateStore = certificateStore;
    }

    public List<ClientDevice> listClientDevices() {
        return thingRegistry.getAllThings()
                .map(thing -> ClientDevice.builder()
                        .thingName(thing.getThingName())
                        .certExpiry(getCertExpiry(thing.getThingName()))
                        .hasSession(hasSession(thing.getThingName()))
                        .build())
                .collect(Collectors.toList());
    }

    private boolean hasSession(String thingName) {
        return sessionManager.listSessions().stream()
                .anyMatch(session -> {
                    DeviceAttribute attr = session.getSessionAttribute("Thing", "ThingName");
                    return attr != null && Objects.equals(attr.toString(), thingName);
                });
    }

    private Date getCertExpiry(String thingName) {
        List<Certificate> certs = lookupCerts(thingName);
        if (certs.isEmpty()) {
            return null;
        }
        X509Certificate cert = getCert(certs.get(0));
        if (cert == null) {
            return null;
        }
        return cert.getNotAfter();
    }

    private X509Certificate getCert(Certificate certificate) {
        String pem;
        try {
            pem = certificateStore.getPem(certificate.getCertificateId()).orElse(null);
            if (pem == null) {
                return null;
            }
        } catch (IOException e) {
            // TODO log
            return null;
        }

        return pemToCert(pem);
    }

    private X509Certificate pemToCert(String pem) {
        CertificateFactory cf;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            return null;
        }

        try (InputStream is = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))) {
            return (X509Certificate) cf.generateCertificate(is);
        } catch (IOException | CertificateException e) {
            // TODO log
            return null;
        }
    }

    private List<Certificate> lookupCerts(String thingName) {
        return thingRegistry.getThing(thingName)
                .getAttachedCertificateIds()
                .keySet()
                .stream()
                // TODO improve perf
                .flatMap(id -> certificateRegistry.getAllCertificates().filter(c -> Objects.equals(c.getCertificateId(), id)))
                .collect(Collectors.toList());
    }
}
