/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;


import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.hamcrest.collection.IsMapContaining;
import org.hamcrest.collection.IsMapWithSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class SessionManagerTest {

    @Spy
    private SessionManager sessionManager;

    @Test
    void GIVEN_session_exist_WHEN_close_session_THEN_succeed() throws Exception {
        String id = sessionManager.createSession(new Certificate("pem", "certificateId"));
        assertThat(sessionManager.findSession(id), notNullValue());
        sessionManager.closeSession(id);
        assertThat(sessionManager.findSession(id), nullValue());
    }

    @Test
    void Given_generateIdCollision_WHEN_createSession_THEN_retryTillUniqueId() {
        when(sessionManager.generateSessionId()).thenReturn("id1", "id1", "id1", "id2");
        sessionManager.createSession(new Certificate("pem", "certificateId"));
        sessionManager.createSession(new Certificate("pem", "certificateId"));

        Map<String, Session> sessionMap = sessionManager.getSessionMap();
        assertThat(sessionMap, IsMapWithSize.aMapWithSize(2));
        assertThat(sessionMap, IsMapContaining.hasKey("id1"));
        assertThat(sessionMap, IsMapContaining.hasKey("id2"));
    }
}
