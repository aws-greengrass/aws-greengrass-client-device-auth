/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.inject.Inject;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


@ExtendWith({MockitoExtension.class, GGExtension.class})
public class UseCasesTest {
    private Topics topics;
    private UseCases useCases;

    static class TestDependency {
        private final String name;

        public TestDependency(String name) {
            this.name = name;
        }
    }

    static class UseCaseWithDependencies implements UseCases.UseCase<String, Void> {
        private final TestDependency dep;

        @Inject
        public UseCaseWithDependencies(TestDependency dep) {
            this.dep = dep;
        }

        @Override
        public String apply(Void dto) {
            return dep.name;
        }
    }

    static class UseCaseWithExceptions implements UseCases.UseCase<Void, Void> {

        @Override
        public Void apply(Void dto) throws InvalidConfigurationException {
            throw new InvalidConfigurationException("Explode");
        }
    }

    static class UseCaseWithParameters implements UseCases.UseCase<String, String> {

        @Override
        public String apply(String dto) {
            return dto;
        }
    }

    @BeforeEach
    void beforeEach() {
        topics = Topics.of(new Context(), CLIENT_DEVICES_AUTH_SERVICE_NAME, null);
        this.useCases = new UseCases(topics.getContext());
        this.useCases.init(topics.getContext());
    }

    @Test
    void GIVEN_aUseCaseWithDependencies_WHEN_ran_THEN_itExecutesWithNoExceptions() {
        TestDependency aTestDependency = new TestDependency("Something");
        topics.getContext().put(TestDependency.class, aTestDependency);

        UseCaseWithDependencies useCase = useCases.get(UseCaseWithDependencies.class);
        assertEquals(useCase.apply(null), aTestDependency.name);
    }

    @Test
    void GIVEN_aUseCaseWithExceptions_WHEN_ran_THEN_itThrowsAnException() {
        UseCaseWithExceptions useCase = useCases.get(UseCaseWithExceptions.class);
        assertThrows(InvalidConfigurationException.class, () -> useCase.apply(null));
    }

    @Test
    void GIVEN_aUseCaseWithParameters_WHEN_ran_itAcceptsTheParamsAndReturnsThem() {
        UseCaseWithParameters useCase = useCases.get(UseCaseWithParameters.class);
        assertEquals(useCase.apply("hello"), "hello");
    }
}
