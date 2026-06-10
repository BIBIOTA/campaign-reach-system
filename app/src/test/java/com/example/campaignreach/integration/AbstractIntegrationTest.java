package com.example.campaignreach.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Reusable base for module-boundary integration tests (design.md §7).
 *
 * <p>Boots the full Spring context on <em>real</em> infrastructure — a real
 * PostgreSQL container and a real Kafka container. The broker is never mocked:
 * future scenarios (e.g. {@code domain.events} → {@code reach.requested}, and
 * {@code reach.requested} → ReachTask persistence) exercise true Kafka and DB
 * behaviour through these containers.
 *
 * <p>Wiring decisions:
 * <ul>
 *   <li>{@link ServiceConnection} auto-wires {@code spring.datasource.*} (from the
 *       PostgreSQL container) and {@code spring.kafka.bootstrap-servers} (from the
 *       Kafka container) into the {@code Environment}. This satisfies the
 *       fail-fast {@code RequiredInfrastructurePropertiesValidator} without any
 *       manual property plumbing.</li>
 *   <li>The Email provider API key has no container source, so a non-secret test
 *       value is supplied via {@link DynamicPropertySource} to pass the
 *       {@code @NotBlank} binding on {@code EmailProviderProperties}.</li>
 *   <li>Containers are {@code static} so they start once and are shared across the
 *       whole class, keeping the suite fast.</li>
 * </ul>
 *
 * <p>Docker is required. When Docker is unavailable the suite is <em>skipped</em>
 * (via the inherited {@link RequiresDocker} condition, which JUnit evaluates
 * before the {@code @Testcontainers} extension tries to start any container)
 * rather than failed, so {@code ./gradlew build} stays green on machines without
 * a Docker daemon.
 */
@Testcontainers
@SpringBootTest
@RequiresDocker
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    /**
     * Supplies a non-secret placeholder for the Email provider API key so the
     * validated {@code @NotBlank} binding passes during context startup. This is a
     * test fixture value, never a real credential.
     */
    @DynamicPropertySource
    static void emailProviderProperties(DynamicPropertyRegistry registry) {
        registry.add("campaignreach.email-provider.api-key", () -> "test-email-provider-api-key");
    }
}
