package com.aws.greengrass.device;

import com.aws.greengrass.device.attribute.AttributeProvider;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.iot.Certificate;

import java.util.concurrent.ConcurrentHashMap;

public class SessionImpl extends ConcurrentHashMap<String, AttributeProvider> implements Session {

    static final long serialVersionUID = -1L;

    // TODO: Replace this with Principal abstraction
    // so that a session can be instantiated using something else
    // e.g. username/password
    public SessionImpl(Certificate certificate) {
        super();
        this.put(certificate.getNamespace(), certificate);
    }

    @Override
    @SuppressWarnings("PMD.UselessOverridingMethod")
    public AttributeProvider get(String attributeProviderNameSpace) {
        return super.get(attributeProviderNameSpace);
    }

    /**
     * Get session attribute.
     *
     * @param attributeNamespace Attribute namespace
     * @param attributeName      Attribute name
     *
     * @return Session attribute
     */
    @Override
    public DeviceAttribute getSessionAttribute(String attributeNamespace, String attributeName) {
        if (this.get(attributeNamespace) != null) {
            return this.get(attributeNamespace).getDeviceAttributes().get(attributeName);
        }
        return null;
    }
}
