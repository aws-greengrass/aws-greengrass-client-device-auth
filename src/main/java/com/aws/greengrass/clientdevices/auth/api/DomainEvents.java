/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


public class DomainEvents {
    private final Map<Class, CopyOnWriteArrayList<DomainEventListener>> eventListeners = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface DomainEventListener<T> {
        void handle(T event);
    }

    /**
     * Register event listener.
     * @param listener Event listener callback
     * @param clazz    Type of domain event
     * @param <T>      Type of domain event
     */
    public <T> void registerListener(DomainEventListener<T> listener, Class<T> clazz) {
        CopyOnWriteArrayList<DomainEventListener> listeners =
                eventListeners.computeIfAbsent(clazz, (k) -> new CopyOnWriteArrayList<>());
        listeners.addIfAbsent(listener);
    }

    /**
     * Emit an event.
     * @param domainEvent Domain event
     * @param <T>         Type of domain event
     */
    public <T> void emit(T domainEvent) {
        List<DomainEventListener> listeners = eventListeners.get(domainEvent.getClass());
        if (listeners != null) {
            for (DomainEventListener<T> listener : listeners) {
                listener.handle(domainEvent);
            }
        }
    }
}
