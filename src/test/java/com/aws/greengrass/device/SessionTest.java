/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.IotControlPlaneBetaClient;
import com.aws.greengrass.device.iot.Thing;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class SessionTest {
    @Mock
    private IotControlPlaneBetaClient mockIotClient;

    @Test
    public void GIVEN_sessionWithThingAndCert_WHEN_getSessionAttributes_THEN_attributesAreReturned() {
        Certificate cert = new Certificate("FAKE_PEM", mockIotClient);
        Thing thing = new Thing("MyThing");
        Session session = new Session(cert);
        session.put(thing.getNamespace(), thing);

        Assertions.assertEquals(session.getSessionAttribute("Certificate", "CertificateId").toString(),
                cert.getDeviceAttributes().get("CertificateId").toString());
        Assertions.assertEquals(session.getSessionAttribute("Thing", "ThingName").toString(),
                thing.getDeviceAttributes().get("ThingName").toString());
    }
}
