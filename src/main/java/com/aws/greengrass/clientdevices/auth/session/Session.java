 /*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

 package com.aws.greengrass.clientdevices.auth.session;

 import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
 import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
 
 public interface Session {
 
     /**
      * Get attribute provider by namespace.
      *
      * @param attributeProviderNameSpace Attribute namespace
      * @return Attribute provider
      */
     AttributeProvider getAttributeProvider(String attributeProviderNameSpace);
 
     /**
      * Get session attribute.
      *
      * @param attributeNamespace Attribute namespace
      * @param attributeName      Attribute name
      * @return Session attribute
      */
     DeviceAttribute getSessionAttribute(String attributeNamespace, String attributeName);
 
     /**
      * Check if attribute provider exists.
      *
      * @param attributeProviderNameSpace Attribute namespace
      * @return True/false
      */
     boolean containsAttributeProvider(String attributeProviderNameSpace);
 
     /**
      * Check if attribute exists.
      *
      * @param attributeNamespace Attribute namespace
      * @param attributeName      Attribute name
      * @return True/false
      */
     boolean containsSessionAttribute(String attributeNamespace, String attributeName);
 }
 