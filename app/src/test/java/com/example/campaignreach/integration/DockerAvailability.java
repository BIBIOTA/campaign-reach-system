package com.example.campaignreach.integration;

import org.testcontainers.DockerClientFactory;

/**
 * JUnit 5 {@code @EnabledIf} condition gating the Testcontainers integration
 * suite on a reachable Docker daemon.
 *
 * <p>Evaluated by JUnit <em>before</em> the {@code @Testcontainers} extension
 * attempts to start any container, so a missing / unreachable Docker daemon
 * <em>skips</em> the integration tests instead of failing the build. This keeps
 * {@code ./gradlew build} green on machines (or sandboxes) without Docker, while
 * the tests still run for real wherever a daemon is available (design.md §7).
 */
final class DockerAvailability {

    private DockerAvailability() {}

    /**
     * @return {@code true} only when a Docker daemon is actually reachable (this
     *     performs a real ping, so it also returns {@code false} when a socket
     *     exists but the daemon rejects API calls).
     */
    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
