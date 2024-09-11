/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.util;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class WildcardTrie {
    private static final String GLOB_WILDCARD = "*";
    private static final String SINGLE_CHAR_WILDCARD = "?";

    private final Map<String, WildcardTrie> children = new DefaultHashMap<>(WildcardTrie::new);

    private boolean isTerminal;
    private boolean isGlobWildcard;
    private boolean isSingleCharWildcard;

    public void add(String subject) {
        add(subject, true);
    }

    private WildcardTrie add(String subject, boolean isTerminal) {
        if (subject == null || subject.isEmpty()) {
            this.isTerminal |= isTerminal;
            return this;
        }
        StringBuilder currPrefix = new StringBuilder(subject.length());
        for (int i = 0; i < subject.length(); i++) {
            char c = subject.charAt(i);
            if (c == GLOB_WILDCARD.charAt(0)) {
                return addGlobWildcard(subject, currPrefix.toString(), isTerminal);
            }
            if (c == SINGLE_CHAR_WILDCARD.charAt(0)) {
                return addSingleCharWildcard(subject, currPrefix.toString(), isTerminal);
            }
            currPrefix.append(c);
        }
        WildcardTrie node = children.get(currPrefix.toString());
        node.isTerminal |= isTerminal;
        return node;
    }

    private WildcardTrie addGlobWildcard(String subject, String currPrefix, boolean isTerminal) {
        WildcardTrie node = this;
        node = node.add(currPrefix, false);
        node = node.children.get(GLOB_WILDCARD);
        node.isGlobWildcard = true;
        // wildcard at end of subject is terminal
        if (subject.length() - currPrefix.length() == 1) {
            node.isTerminal = isTerminal;
            return node;
        }
        return node.add(subject.substring(currPrefix.length() + 2), true);
    }

    private WildcardTrie addSingleCharWildcard(String subject, String currPrefix, boolean isTerminal) {
        WildcardTrie node = this;
        node = node.add(currPrefix, false);
        node = node.children.get(SINGLE_CHAR_WILDCARD);
        node.isSingleCharWildcard = true;
        // wildcard at end of subject is terminal
        if (subject.length() - currPrefix.length() == 1) {
            node.isTerminal = isTerminal;
            return node;
        }
        return node.add(subject.substring(currPrefix.length() + 1), true);
    }

    public boolean matches(String s) {
        return matches(s, true);
    }

    public boolean matches(String s, boolean matchSingleCharWildcard) {
        if (s == null) {
            return children.isEmpty();
        }

        if ((isWildcard() && isTerminal) || (isTerminal && s.isEmpty())) {
            return true;
        }

        boolean childMatchesWildcard = children
                .values()
                .stream()
                .filter(WildcardTrie::isWildcard)
                .filter(childNode -> matchSingleCharWildcard || !childNode.isSingleCharWildcard)
                .anyMatch(childNode -> childNode.matches(s, matchSingleCharWildcard));
        if (childMatchesWildcard) {
            return true;
        }

        if (matchSingleCharWildcard) {
            boolean childMatchesSingleCharWildcard = children
                    .values()
                    .stream()
                    .filter(childNode -> childNode.isSingleCharWildcard)
                    .anyMatch(childNode -> childNode.matches(s, matchSingleCharWildcard));
            if (childMatchesSingleCharWildcard) {
                return true;
            }
        }

        boolean childMatchesRegularCharacters = children
                .keySet()
                .stream()
                .filter(s::startsWith)
                .anyMatch(childToken -> {
                    WildcardTrie childNode = children.get(childToken);
                    String rest = s.substring(childToken.length());
                    return childNode.matches(rest, matchSingleCharWildcard);
                });
        if (childMatchesRegularCharacters) {
            return true;
        }

        if (isWildcard() && !isTerminal) {
            return findMatchingChildSuffixesAfterWildcard(s, matchSingleCharWildcard)
                    .entrySet()
                    .stream()
                    .anyMatch((e) -> {
                        String suffix = e.getKey();
                        WildcardTrie childNode = e.getValue();
                        return childNode.matches(suffix, matchSingleCharWildcard);
                    });
        }
        return false;
    }

    private Map<String, WildcardTrie> findMatchingChildSuffixesAfterWildcard(String s, boolean matchSingleCharWildcard) {
        Map<String, WildcardTrie> matchingSuffixes = new HashMap<>();
        for (Map.Entry<String, WildcardTrie> e : children.entrySet()) {
            String childToken = e.getKey();
            WildcardTrie childNode = e.getValue();
            int suffixIndex = s.indexOf(childToken);
            if (matchSingleCharWildcard && suffixIndex > 1) {
                continue;
            }
            while (suffixIndex >= 0) {
                matchingSuffixes.put(s.substring(suffixIndex + childToken.length()), childNode);
                suffixIndex = s.indexOf(childToken, suffixIndex + 1);
            }
        }
        return matchingSuffixes;
    }

    private boolean isWildcard() {
        return isGlobWildcard || isSingleCharWildcard;
    }

    @SuppressFBWarnings("EQ_DOESNT_OVERRIDE_EQUALS")
    private static class DefaultHashMap<K, V> extends HashMap<K, V> {
        private final transient Supplier<V> defaultVal;

        public DefaultHashMap(Supplier<V> defaultVal) {
            super();
            this.defaultVal = defaultVal;
        }

        @Override
        @SuppressWarnings("unchecked")
        public V get(Object key) {
            return super.computeIfAbsent((K) key, (k) -> defaultVal.get());
        }

        @Override
        public boolean containsKey(Object key) {
            return super.get(key) != null;
        }
    }
}
