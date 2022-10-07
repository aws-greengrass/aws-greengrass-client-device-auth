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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

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
        return Thing.of(thingName, new HashMap<>());
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
        Map<String, Instant> certIds;
        synchronized (attachedCertificateIds) {
            certIds = new HashMap<>(attachedCertificateIds);
        }
        return certIds;
    }

    /**
     * Indicates whether the given certificate is attached to this thing.
     *
     * @param certificateId Certificate ID
     * @return whether the given certificate is attached
     */
    public boolean isCertificateAttached(String certificateId) {
        return attachedCertificateIds.containsKey(certificateId);
    }

    private Thing(String thingName, Map<String, Instant> certificateIds) {
        this.thingName = thingName;
        this.attachedCertificateIds = Collections.synchronizedMap(certificateIds);
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
}
