package com.aws.greengrass.device.configuration;

import com.aws.greengrass.device.SessionManager;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;

/**
 * a singleton manager class for managing device group roles. It listens to configuration update through nucleus, build
 * GroupDefinition including rule binary expression tree. On the hand, for each request in a session, it iterates
 * through the configurations to find match group(s), returning the authorization policies of group(s).
 */
@SuppressWarnings("PMD.UnusedPrivateField")
public class GroupManager {

    @Inject
    private SessionManager sessionManager;

    private AtomicReference<GroupConfiguration> groupConfiguration;

    public void handleGroupConfigurationUpdate() {
        // take updated group definition from nucleus configuration store
        // rebuild GroupConfiguration model
    }

    /**
     * find applicable policies to evaluate for the given device request.
     *
     * @param sessionId sessionId can retrieve cached device attributes
     * @return set of applicable policy for the device
     */
    public Set<AuthorizationPolicy> getApplicablePolicies(String sessionId) {
        // GroupConfiguration config = groupConfiguration.get();
        // Session session = sessionManager.getSession(sessionId);
        //TODO iterate groups to find matching group(s), return the policies.

        return Collections.emptySet();
    }

}
