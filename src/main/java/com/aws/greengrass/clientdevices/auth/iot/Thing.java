/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.WildcardSuffixAttribute;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES;

/**
 * This is a versioned representation of an IoT Thing. It is **NOT** updated when the local Thing Registry is updated,
 * or when changes to this Thing are made in IoT Core. Consider calling the ThingRegistry to retrieve Thing objects as
 * they are needed rather than storing references long term.
 */
@Getter
public final class Thing implements AttributeProvider, Cloneable {
    public static final String NAMESPACE = "Thing";
    private static final String thingNamePattern = "[a-zA-Z0-9\\-_:]+";
    private static final AtomicInteger metadataTrustDurationMinutes =
            new AtomicInteger(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES);

    private final String thingName;
    private final Map<String, Attachment> attachmentsByCertId;
    private boolean modified = false;

    /**
     * Create a new Thing.
     *
     * @param thingName AWS IoT ThingName
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
     *
     * @param certificateId Certificate ID to attach
     */
    public void attachCertificate(String certificateId) {
        attachmentsByCertId.put(certificateId, new Attachment());
        modified = true;
    }

    /**
     * Detach a certificate ID.
     *
     * @param certificateId Certificate ID to detach
     */
    public void detachCertificate(String certificateId) {
        if (attachmentsByCertId.remove(certificateId) != null) {
            modified = true;
        }
    }

    /**
     * Returns copy of attached certificate IDs.
     * <p>
     * This map should not be modified directly. Refer to {@link #attachCertificate(String) attachCertificate} and
     * {@link #detachCertificate(String) detachCertificate}
     * </p>
     *
     * @return Certificate IDs
     */
    public Map<String, Instant> getAttachedCertificateIds() {
        // TODO: Do not expose this it is breaking encapsulation. Instead add methods inside of here that can
        //  answer to questions external callers might have
        return attachmentsByCertId.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getCreated()));
    }

    /**
     * Returns the last updated instant we updated the value of a certificate being attached to a thing.
     *
     * @param certificateId - A certificateId
     */
    public Optional<Instant> certificateLastAttachedOn(String certificateId) {
        return Optional.ofNullable(attachmentsByCertId.get(certificateId)).map(Attachment::getCreated);
    }

    public Optional<Attachment> getAttachment(String certificateId) {
        return Optional.ofNullable(attachmentsByCertId.get(certificateId));
    }

    private Thing(String thingName, Map<String, Instant> certificateIds) {
        this.thingName = thingName;
        if (certificateIds == null) {
            this.attachmentsByCertId = new ConcurrentHashMap<>();
        } else {
            this.attachmentsByCertId = new ConcurrentHashMap<>(
                    certificateIds.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> new Attachment(e.getValue()))));
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

        return thingName.equals(other.thingName) && attachmentsByCertId.equals(other.attachmentsByCertId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingName, attachmentsByCertId);
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

    @Value
    public static class Attachment {
        Instant created;

        public Attachment() {
            this(Instant.now());
        }

        public Attachment(@NonNull Instant created) {
            this.created = created;
        }

        public Instant getExpiration() {
            return created.plus(Duration.ofMinutes(metadataTrustDurationMinutes.get()));
        }

        public boolean isTrusted() {
            return getExpiration().isAfter(Instant.now());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Attachment)) {
                return false;
            }

            Attachment other = (Attachment) o;
            return Objects.equals(getCreated(), other.getCreated());
        }

        @Override
        public int hashCode() {
            return Objects.hash(created);
        }
    }
}
