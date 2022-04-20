/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;

import com.aws.greengrass.device.exception.AuthenticationException;

import java.util.Map;

public interface SessionFactory {
    Session createSession(Map<String, String> credentialMap) throws AuthenticationException;
}
