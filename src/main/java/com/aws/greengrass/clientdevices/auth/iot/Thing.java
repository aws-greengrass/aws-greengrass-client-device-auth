/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.WildcardSuffixAttribute;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
public final class Thing implements AttributeProvider {
    public static final String NAMESPACE = "Thing";
    private static final String thingNamePattern = "[a-zA-Z0-9\\-_:]+";

    private final int version;
    private final String thingName;
    private final List<String> attachedCertificateIds;
    private boolean modified = false;

    /**
     * Create a new Thing.
     *
     * @param thingName AWS IoT ThingName
     * @throws IllegalArgumentException If the given ThingName contains illegal characters
     */
    public static Thing of(String thingName) {
        return Thing.of(0, thingName);
    }

    /**
     * Create a new Thing.
     *
     * @param version        Thing version
     * @param thingName      AWS IoT ThingName
     * @throws IllegalArgumentException If the given ThingName contains illegal characters
     */
    public static Thing of(int version, String thingName) {
        return Thing.of(version, thingName, new ArrayList<>());
    }

    /**
     * Create a new Thing.
     *
     * @param version        Thing version
     * @param thingName      AWS IoT ThingName
     * @param certificateIds Attached certificate IDs
     * @throws IllegalArgumentException If the given ThingName contains illegal characters
     */
    public static Thing of(int version, String thingName, List<String> certificateIds) {
        if (version < 0) {
            throw new IllegalArgumentException("Invalid version. Version must not be < 0");
        }
        if (!Pattern.matches(thingNamePattern, thingName)) {
            throw new IllegalArgumentException("Invalid thing name. The thing name must match \"[a-zA-Z0-9\\-_:]+\".");
        }
        return new Thing(version, thingName, new ArrayList<>(certificateIds));
    }

    /**
     * Create a copy of a Thing.
     *
     * @param other Thing to copy
     */
    public static Thing of(Thing other) {
        return Thing.of(other.getVersion(), other.getThingName(), other.getAttachedCertificateIds());
    }

    /**
     * Attach a certificate ID.
     * @param certificateId Certificate ID to attach
     */
    public void attachCertificate(String certificateId) {
        if (attachedCertificateIds.contains(certificateId)) {
            return;
        }
        attachedCertificateIds.add(certificateId);
        modified = true;
    }

    /**
     * Detach a certificate ID.
     * @param certificateId Certificate ID to detach
     */
    public void detachCertificate(String certificateId) {
        if (!attachedCertificateIds.contains(certificateId)) {
            return;
        }
        attachedCertificateIds.remove(certificateId);
        modified = true;
    }

    /**
     * Returns copy of attached certificate IDs.
     * </p>
     * This list cannot be modified directly. Refer to {@link #attachCertificate(String) attachCertificate} and
     * {@link #detachCertificate(String) detachCertificate}
     *
     * @return Certificate IDs
     */
    public List<String> getAttachedCertificateIds() {
        return new ArrayList<>(attachedCertificateIds);
    }

    private Thing(int version, String thingName, List<String> certificateIds) {
        this.version = version;
        this.thingName = thingName;
        this.attachedCertificateIds = certificateIds;
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

        return version == other.version
                && thingName.equals(other.thingName)
                && attachedCertificateIds.equals(other.attachedCertificateIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, thingName, attachedCertificateIds);
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
