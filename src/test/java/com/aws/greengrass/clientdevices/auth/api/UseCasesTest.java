/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
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
import static org.junit.jupiter.api.Assertions.assertTrue;


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
        public Result<String> apply(Void dto) {
            return Result.ok(dep.name);
        }
    }

    static class UseCaseWithExceptions implements UseCases.UseCase<InvalidConfigurationException, Void> {

        @Override
        public Result<InvalidConfigurationException> apply(Void dto) {
            return Result.error(new InvalidConfigurationException("Explode"));
        }
    }

    static class UseCaseWithParameters implements UseCases.UseCase<String, String> {

        @Override
        public Result<String> apply(String dto) {
            return Result.ok(dto);
        }
    }

    static class UseCaseUpdatingDependency implements UseCases.UseCase<String, Void> {
        private final CDAConfiguration configuration;

        @Inject
        public UseCaseUpdatingDependency(CDAConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public Result<String> apply(Void dto) {
            return Result.ok(configuration.getCertificateUri().get().toString());
        }
    }

    @BeforeEach
    void beforeEach() {
        topics = Topics.of(new Context(), CLIENT_DEVICES_AUTH_SERVICE_NAME, null);
        this.useCases = new UseCases();
        this.useCases.init(topics.getContext());
    }

    @Test
    void GIVEN_aUseCaseWithDependencies_WHEN_ran_THEN_itExecutesWithNoExceptions() {
        TestDependency aTestDependency = new TestDependency("Something");
        topics.getContext().put(TestDependency.class, aTestDependency);

        UseCaseWithDependencies useCase = useCases.get(UseCaseWithDependencies.class);
        assertEquals(useCase.apply(null).get(), aTestDependency.name);
    }

    @Test
    void GIVEN_aUseCaseWithExceptions_WHEN_ran_THEN_itThrowsAnException() {
        UseCaseWithExceptions useCase = useCases.get(UseCaseWithExceptions.class);
        assertTrue(useCase.apply(null).get() instanceof InvalidConfigurationException);
    }

    @Test
    void GIVEN_aUseCaseWithParameters_WHEN_ran_itAcceptsTheParamsAndReturnsThem() {
        UseCaseWithParameters useCase = useCases.get(UseCaseWithParameters.class);
        assertEquals(useCase.apply("hello").get(), "hello");
    }
}
