/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.exception.PolicyException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateFake;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.SessionImpl;
import com.aws.greengrass.clientdevices.auth.session.attribute.WildcardSuffixAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PolicyVariableResolverTest {
    private static final String FAKE_CERT_ID = "FAKE_CERT_ID";
    private static final String THING_NAME = "b";
    private static final String THING_NAMESPACE = "Thing";
    private static final String THING_NAME_ATTRIBUTE = "ThingName";
    private Certificate cert;
    private Thing thing;
    private Session session;
    @Mock
    private Session mockSession;
    @Mock
    private WildcardSuffixAttribute wildcardSuffixAttribute;
    private static final List<String> POLICY_VARIABLES = Collections.singletonList("${iot:Connection.Thing.ThingName}");

    @BeforeEach
    void beforeEach() throws InvalidCertificateException {
        cert = CertificateFake.of(FAKE_CERT_ID);
        thing = Thing.of(THING_NAME);
        session = new SessionImpl(cert, thing);
        mockSession = mock(Session.class);
        wildcardSuffixAttribute = mock(WildcardSuffixAttribute.class);
    }

    @Test
    void GIVEN_valid_resource_and_policy_variables_WHEN_resolve_policy_variables_THEN_return_updated_resource()
            throws PolicyException {
        String resource = "msg/${iot:Connection.Thing.ThingName}/test";
        String expected = String.format("msg/%s/test", THING_NAME);
        String actual = PolicyVariableResolver.resolvePolicyVariables(POLICY_VARIABLES, resource, session);
        assertThat(expected.equals(actual), is(true));
    }

    @Test
    void GIVEN_invalid_resource_and_policy_variables_WHEN_resolve_policy_variables_THEN_return_original_resource()
            throws PolicyException {
        String resource = "msg/${iot:Connection.Thing/ThingName}/test";
        String expected = "msg/${iot:Connection.Thing/ThingName}/test";
        String actual = PolicyVariableResolver.resolvePolicyVariables(POLICY_VARIABLES, resource, session);
        assertThat(expected.equals(actual), is(true));
    }

    @Test
    void GIVEN_valid_resource_and_policy_variables_WHEN_no_session_attribute_THEN_throw_exception() {
        String resource = "msg/${iot:Connection.Thing.ThingName}/test";

        when(mockSession.getSessionAttribute(THING_NAMESPACE, THING_NAME_ATTRIBUTE)).thenReturn(wildcardSuffixAttribute);

        when(wildcardSuffixAttribute.toString()).thenReturn(null);

        assertThrows(
                PolicyException.class, () -> PolicyVariableResolver.resolvePolicyVariables(POLICY_VARIABLES, resource,
                mockSession));
    }

}
