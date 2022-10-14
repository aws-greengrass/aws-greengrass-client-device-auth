/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.WildcardSuffixAttribute;
import lombok.Getter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES;

/**
 * This is a versioned representation of an IoT Thing. It is **NOT** updated
 * when the local Thing Registry is updated, or when changes to this Thing are
 * made in IoT Core. Consider calling the ThingRegistry to retrieve Thing
 * objects as they are needed rather than storing references long term.
 */
@Getter
public final class Thing implements AttributeProvider, Cloneable {
    public static final String NAMESPACE = "Thing";
    private static final String thingNamePattern = "[a-zA-Z0-9\\-_:]+";
    private static final AtomicInteger metadataTrustDurationMinutes =
            new AtomicInteger(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES);

    private final String thingName;
    // map of certificate ID to the time this certificate was known to be attached to the Thing
    private final Map<String, Instant> attachedCertificateIds;
    private boolean modified = false;

    /**
     * Create a new Thing.
     *
     * @param thingName      AWS IoT ThingName
     * @throws IllegalArgumentException If the given ThingName contains illegal characters
     */
    public static Thing of(String thingName) {
        return Thing.of(thingName, null);
    }

    /**
     * Create a new Thing.
     *
     * @param thingName      AWS IoT ThingName
     * @param certificateIds Attached certificate IDs
     * @throws IllegalArgumentException If the given ThingName contains illegal characters
     */
    public static Thing of(String thingName, Map<String, Instant> certificateIds) {
        if (!Pattern.matches(thingNamePattern, thingName)) {
            throw new IllegalArgumentException("Invalid thing name. The thing name must match \"[a-zA-Z0-9\\-_:]+\".");
        }
        return new Thing(thingName, certificateIds);
    }

    @Override
    public Thing clone() {
        Thing newThing = Thing.of(getThingName(), getAttachedCertificateIds());
        newThing.modified = modified;
        return newThing;
    }

    /**
     * Attach a certificate ID.
     * @param certificateId Certificate ID to attach
     */
    public void attachCertificate(String certificateId) {
        attachedCertificateIds.put(certificateId, Instant.now());
        modified = true;
    }

    /**
     * Detach a certificate ID.
     * @param certificateId Certificate ID to detach
     */
    public void detachCertificate(String certificateId) {
        if (attachedCertificateIds.remove(certificateId) != null) {
            modified = true;
        }
    }

    /**
     * Returns copy of attached certificate IDs.
     * <p>
     * This map should not be modified directly. Refer to {@link #attachCertificate(String) attachCertificate} and
     * {@link #detachCertificate(String) detachCertificate}
     * </p>
     * @return Certificate IDs
     */
    public Map<String, Instant> getAttachedCertificateIds() {
        // TODO: Do not expose this it is breaking encapsulation. Instead add methods inside of here that can
        //  answer to questions external callers might have
        return new HashMap<>(attachedCertificateIds);
    }

    /**
     * Returns the last updated instant we updated the value of a certificate being attached to a thing.
     * @param certificateId - A certificateId
     */
    public Optional<Instant> certificateLastAttachedOn(String certificateId) {
        if (!attachedCertificateIds.containsKey(certificateId)) {
            return Optional.empty();
        }

        return Optional.of(attachedCertificateIds.get(certificateId));
    }

    /**
     * Indicates whether the given certificate is attached to this thing.
     *
     * @param certificateId Certificate ID
     * @return whether the given certificate is attached
     */
    public boolean isCertificateAttached(String certificateId) {
        Instant lastVerified = attachedCertificateIds.get(certificateId);
        return lastVerified != null && isCertAttachmentTrusted(lastVerified);
    }

    private Thing(String thingName, Map<String, Instant> certificateIds) {
        this.thingName = thingName;
        if (certificateIds == null) {
            this.attachedCertificateIds = new ConcurrentHashMap<>();
        } else {
            this.attachedCertificateIds = new ConcurrentHashMap<>(certificateIds);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Thing)) {
            return false;
        }

        Thing other = (Thing) o;
        if (isModified() || other.isModified()) {
            return false;
        }

        return thingName.equals(other.thingName)
                && attachedCertificateIds.equals(other.attachedCertificateIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingName, attachedCertificateIds);
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public Map<String, DeviceAttribute> getDeviceAttributes() {
        return Collections.singletonMap("ThingName", new WildcardSuffixAttribute(thingName));
    }

    /**
     * Updates the duration for which a certificate attachment can be trusted.
     *
     * @param newTrustDuration desired trust duration in minutes
     */
    public static void updateMetadataTrustDurationMinutes(int newTrustDuration) {
        metadataTrustDurationMinutes.set(newTrustDuration);
    }

    private boolean isCertAttachmentTrusted(Instant lastVerified) {
        Instant validTill = lastVerified.plus(metadataTrustDurationMinutes.get(), ChronoUnit.MINUTES);
        return validTill.isAfter(Instant.now());
    }
}
