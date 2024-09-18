/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.util;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class WildcardTrie {

    private static final Function<WildcardType, UnsupportedOperationException> EXCEPTION_UNSUPPORTED_WILDCARD_TYPE = wildcardType ->
            new UnsupportedOperationException("wildcard type " + wildcardType.name() + " not supported");

    private Node root;

    private final MatchOptions opts;

    private static String cleanPattern(@NonNull String s) {
        // for example "abc***def" can be reduced to "abc*def"
        return s.replaceAll(String.format("\\%s+", WildcardType.GLOB.val), WildcardType.GLOB.val);
    }

    public WildcardTrie withPattern(@NonNull String s) {
        root = new Node();
        withPattern(root, cleanPattern(s));
        return this;
    }

    private Node withPattern(@NonNull Node n, @NonNull String s) {
        StringBuilder token = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isWildcard(s.charAt(i))) {
                // create child node from non-wildcard chars that have been accumulated so far
                Node node = token.length() > 0 ? n.children.get(token.toString()) : n;
                // create child node for wildcard char itself
                WildcardType type = WildcardType.from(c);
                node = node.children.get(type.val());
                node.wildcardType = type;
                if (i == s.length() - 1) {
                    // we've reached the last token
                    return node;
                }
                return withPattern(node, s.substring(i + 1));
            } else {
                token.append(c);
            }
        }
        // use remaining (non-wildcard) chars as last token
        if (token.length() > 0) {
            return n.children.get(token.toString());
        } else {
            return n;
        }
    }

    private boolean isWildcard(char c) {
        WildcardType type = WildcardType.from(c);
        if (type == null) {
            return false;
        }
        if (type == WildcardType.SINGLE) {
            return opts.useSingleCharWildcard;
        }
        return true;
    }

    public boolean matches(@NonNull String s) {
        if (root == null) {
            return s.isEmpty();
        }
        return matches(root, s);
    }

    private boolean matches(@NonNull Node n, @NonNull String s) {
        if (n.isTerminal()) {
            return n.wildcardType == WildcardType.GLOB || s.isEmpty();
        }

        if (n.isWildcard()) {
            switch (n.wildcardType) {
                case SINGLE:
                    return n.children.keySet().stream().anyMatch(token -> {
                        Node child = n.children.get(token);
                        // skip over one character for single wildcard
                        if (child.isWildcard()) {
                            return !s.isEmpty() && matches(child, s.substring(1));
                        } else {
                            return !s.isEmpty() && s.startsWith(token.substring(0, 1)) && matches(child, s.substring(1));
                        }
                    });
                case GLOB:
                    return n.children.keySet().stream().anyMatch(token -> {
                        Node child = n.children.get(token);
                        if (child.isWildcard()) {
                            return true;// TODO
                        } else {
                            // consume the input string to find a match
                            return allIndicesOf(s, token).stream()
                                    .anyMatch(tokenIndex ->
                                            matches(child, s.substring(tokenIndex + token.length()))
                                    );
                        }
                    });
                default:
                    throw EXCEPTION_UNSUPPORTED_WILDCARD_TYPE.apply(n.wildcardType);
            }
        }

        return n.children.keySet().stream().anyMatch(token -> {
            Node child = n.children.get(token);
            if (child.isWildcard()) {
                switch (child.wildcardType) {
                    case SINGLE:
                        // skip past the next character for ? matching
                        return !s.isEmpty() && matches(child, s.substring(1));
                    case GLOB:
                        // skip past token and figure out retroactively if the glob matched
                        return matches(child, s);
                    default:
                        throw EXCEPTION_UNSUPPORTED_WILDCARD_TYPE.apply(child.wildcardType);
                }
            } else {
                // match found, keep following this trie branch
                return s.startsWith(token) && matches(child, s.substring(token.length()));
            }
        });
    }

    private static List<Integer> allIndicesOf(@NonNull String s, @NonNull String sub) {
        List<Integer> indices = new ArrayList<>();
        int i = s.indexOf(sub);
        while (i >= 0) {
            indices.add(i);
            i = s.indexOf(sub, i + sub.length());
        }
        return indices;
    }

    @Value
    @Builder
    public static class MatchOptions {
        boolean useSingleCharWildcard;
    }

    enum WildcardType {
        GLOB("*"),
        SINGLE("?");

        private final String val;

        WildcardType(@NonNull String val) {
            this.val = val;
        }

        public static WildcardType from(char c) {
            return Arrays.stream(WildcardType.values())
                    .filter(t -> t.charVal() == c)
                    .findFirst()
                    .orElse(null);
        }

        public String val() {
            return val;
        }

        public char charVal() {
            return val.charAt(0);
        }
    }

    private class Node {
        private final Map<String, Node> children = new DefaultHashMap<>(Node::new);
        private WildcardType wildcardType;

        public boolean isWildcard() {
            return wildcardType != null;
        }

        public boolean isTerminal() {
            return children.isEmpty();
        }
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
