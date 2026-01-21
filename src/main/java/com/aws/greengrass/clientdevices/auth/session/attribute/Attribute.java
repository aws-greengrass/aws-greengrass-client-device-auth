/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session.attribute;


import lombok.Getter;

@Getter
public enum Attribute {

    THING_NAME(Namespaces.THING, "ThingName"),
    THING_ATTRIBUTES(Namespaces.THING, "ThingAttributes"),
    CERTIFICATE_ID(Namespaces.CERTIFICATE, "CertificateId"),
    COMPONENT(Namespaces.COMPONENT, "component");

    private final String namespace;
    private final String name;

    Attribute(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public static class Namespaces {
        public static final String THING = "Thing";
        public static final String CERTIFICATE = "Certificate";
        public static final String COMPONENT = "Component";
    }
}
