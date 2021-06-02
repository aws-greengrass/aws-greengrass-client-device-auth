/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;


import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class SessionManagerTest {

    @Test
    void GIVEN_session_exist_WHEN_close_session_THEN_succeed() throws Exception {
        SessionManager sessionManager = new SessionManager();
        String id = sessionManager.createSession(new Certificate("pem", "certificateId"));
        assertThat(sessionManager.findSession(id), notNullValue());
        sessionManager.closeSession(id);
        assertThat(sessionManager.findSession(id), nullValue());
    }

    @Test
    void GIVEN_session_not_exist_WHEN_close_session_THEN_throw_exception() throws Exception {
        SessionManager sessionManager = new SessionManager();
        assertThrows(AuthorizationException.class, () -> sessionManager.closeSession("id"));
    }

}
