/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;


@SuppressWarnings({"rawtypes", "unchecked"})
public class DomainEvents {
    private final Map<Class, CopyOnWriteArrayList<Consumer>> eventHandlers = new ConcurrentHashMap<>();
    private final Logger logger = LogManager.getLogger(DomainEvents.class);

    /**
     * Register event listener.
     *
     * @param listener Event listener callback
     * @param clazz    Type of domain event
     * @param <T>      Type of domain event
     */
    public <T extends DomainEvent> void registerListener(Consumer<T> listener, Class<T> clazz) {
        CopyOnWriteArrayList<Consumer> listeners =
                eventHandlers.computeIfAbsent(clazz, (k) -> new CopyOnWriteArrayList<>());
        listeners.addIfAbsent(listener);
    }

    /**
     * Emit an event.
     *
     * @param domainEvent Domain event
     * @param <T>         Type of domain event
     */
    public <T extends DomainEvent> void emit(T domainEvent) {
        List<Consumer> handlers = eventHandlers.getOrDefault(domainEvent.getClass(), new CopyOnWriteArrayList<>());

        for (Consumer<T> handler : handlers) {
            logger.atDebug().kv("eventHandler", handler.getClass().getSimpleName())
                    .kv("event", domainEvent.getClass().getSimpleName()).log("Invoking event handler");

            handler.accept(domainEvent);
        }
    }
}
