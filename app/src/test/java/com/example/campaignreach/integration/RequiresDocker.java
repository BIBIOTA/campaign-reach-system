package com.example.campaignreach.integration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Marks an integration test that needs a reachable Docker daemon.
 *
 * <p>{@code @Inherited} so the gate applies to every concrete subclass of
 * {@code AbstractIntegrationTest} without each having to repeat it — plain
 * {@code @EnabledIf} is not inherited, so the Testcontainers extension would
 * otherwise still try (and fail) to start containers on Docker-less machines.
 * When Docker is unreachable the annotated class is <em>skipped</em>, keeping
 * {@code ./gradlew build} green (design.md §7).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@EnabledIf("com.example.campaignreach.integration.DockerAvailability#isDockerAvailable")
@interface RequiresDocker {}
