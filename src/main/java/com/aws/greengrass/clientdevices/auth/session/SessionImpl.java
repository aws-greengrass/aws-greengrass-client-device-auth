/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;

import java.util.concurrent.ConcurrentHashMap;

public class SessionImpl extends ConcurrentHashMap<String, AttributeProvider> implements Session {

 static final long serialVersionUID = -1L;

 /**
  * Create a Session from a list of attribute providers.
  *
  * @param providers list of attribute providers
  */
 public SessionImpl(AttributeProvider... providers) {
     super();
     for (AttributeProvider provider : providers) {
         this.put(provider.getNamespace(), provider);
     }
 }

 @Override
 public AttributeProvider getAttributeProvider(String attributeProviderNameSpace) {
     return this.get(attributeProviderNameSpace);
 }

 /**
  * Get session attribute.
  *
  * @param attributeNamespace Attribute namespace
  * @param attributeName      Attribute name
  * @return Session attribute
  */
 @Override
 public DeviceAttribute getSessionAttribute(String attributeNamespace, String attributeName) {
     if (this.getAttributeProvider(attributeNamespace) != null) {
         return this.getAttributeProvider(attributeNamespace).getDeviceAttributes().get(attributeName);
     }
     return null;
 }


 /**
  * Check if attribute provider exists.
  *
  * @param attributeProviderNameSpace Attribute provider namespace
  * @return True/false
  */
 @Override
 public boolean containsAttributeProvider(String attributeProviderNameSpace) {
     return this.containsKey(attributeProviderNameSpace);
 }


 /**
  * Check if attribute exists.
  *
  * @param attributeNamespace Attribute namespace
  * @param attributeName      Attribute name
  * @return True/false
  */
 @Override
 public boolean containsSessionAttribute(String attributeNamespace, String attributeName) {
     return containsAttributeProvider(attributeNamespace)
                 && this.getAttributeProvider(attributeNamespace)
                 .getDeviceAttributes()
                 .containsKey(attributeName);
 }

}
