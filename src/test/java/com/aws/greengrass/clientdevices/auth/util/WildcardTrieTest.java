/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// TODO fix failing cases
class WildcardTrieTest {

    static Stream<Arguments> validMatches() {
        return Stream.of(
                arguments("foo", singletonList("foo")),
                arguments("foo/bar", singletonList("foo/bar")),
                // glob wildcard *
                arguments("*", asList("foo", "foo/bar", "foo/bar/baz", "$foo/bar", "foo*", "foo?", "***", "???")),
                arguments("*test", asList("test", "*test", "**test", "testtest", "test*test")),
                arguments("test*", asList("test", "test*", "testA", "test**")),
                arguments("test*test", asList("testtest", "testAtest", "test*test")),
                arguments("test*test*", asList("testtest", "testtesttesttest", "test*test*")),
                arguments("*test*test", asList("Atesttest", "testAtest", "AtestAtest", "testtest", "*test*test")),
                arguments("*test*test*", asList("testtest", "*test*test*", "Atesttest", "testAtest", "testtestA", "AtestAtest", "testAtestA", "AtestAtestA")),
                // single character wildcard ?
                arguments("?", asList("f", "*", "?")),
                arguments("??", asList("ff", "**", "??", "*?")),
                arguments("?f?", asList("fff", "f", "ff", "*f", "*f*", "f*"))
        );
    }

    @MethodSource("validMatches")
    @ParameterizedTest
    void GIVEN_trie_with_wildcards_WHEN_valid_matches_provided_THEN_pass(String pattern, List<String> matches) {
        WildcardTrie.MatchOptions opts = WildcardTrie.MatchOptions.builder().useSingleCharWildcard(true).build();
        WildcardTrie trie = new WildcardTrie(opts).withPattern(pattern);
        matches.forEach(m -> assertTrue(trie.matches(m)));
    }


    static Stream<Arguments> invalidMatches() {
        return Stream.of(
                arguments("foo", singletonList("bar")),
                arguments("foo/bar", singletonList("bar/foo")),
                // glob wildcard *
                arguments("*test", asList("testA", "*testA", "AtestA", "bar")),
                arguments("test*", asList("Atest", "Atest*", "AtestA", "Atest**", "bar")),
                arguments("test*test", asList("Atesttest", "AtestAtest", "testAtestA", "testbar")),
                arguments("test*test*", asList("Atesttest", "AtestAtest", "AtestAtestA", "testbar")),
                arguments("*test*test", asList("AtestAtestA", "test*bar", "bartest", "testbar")),
                arguments("*test*test*", asList("AtestAbar", "testbar", "bartest")),
                // single character wildcard ?
                arguments("?", asList("aa", "??", "**")),
                arguments("??", asList("aaa", "???", "***")),
                arguments("?a?", asList("aaaa", "fff"))
        );
    }

    @MethodSource("invalidMatches")
    @ParameterizedTest
    void GIVEN_trie_with_wildcards_WHEN_invalid_matches_provided_THEN_fail(String pattern, List<String> matches) {
        WildcardTrie.MatchOptions opts = WildcardTrie.MatchOptions.builder().useSingleCharWildcard(true).build();
        WildcardTrie trie = new WildcardTrie(opts).withPattern(pattern);
        matches.forEach(m -> assertFalse(trie.matches(m)));
    }
}
