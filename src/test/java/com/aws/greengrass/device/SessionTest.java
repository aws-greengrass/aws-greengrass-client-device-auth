/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.Thing;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
public class SessionTest {
    @Test
    public void GIVEN_session_with_thing_and_cert_WHEN_getSessionAttributes_THEN_attributes_are_returned() {
        Certificate cert = new Certificate("FAKE_PEM");
        Thing thing = new Thing("MyThing");
        Session session = new Session(cert);
        session.addAttributeProvider(thing);

        Assertions.assertEquals(session.getSessionAttribute("Certificate", "CertificateId").getValue(),
                cert.getDeviceAttributes().get("CertificateId").getValue());
        Assertions.assertEquals(session.getSessionAttribute("Thing", "ThingName").getValue(),
                thing.getDeviceAttributes().get("ThingName").getValue());
    }
}
