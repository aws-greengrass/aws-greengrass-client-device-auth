/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session.attribute;

import java.util.Map;

public interface AttributeProvider {
    String getNamespace();

    Map<String, DeviceAttribute> getDeviceAttributes();
}
