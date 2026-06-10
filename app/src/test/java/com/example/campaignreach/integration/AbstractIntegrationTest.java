package com.example.campaignreach.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
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
 *   <li>Containers are started once in a static initialiser (Singleton Containers
 *       Pattern) and live for the entire JVM lifetime. This prevents Spring context
 *       cache misses caused by per-class container lifecycle: if JUnit's
 *       {@code @Testcontainers} extension started and stopped containers per test
 *       class, a second subclass would get new random ports while Spring reuses the
 *       cached context that still points to the first class's (now stopped) ports,
 *       causing connection failures.</li>
 * </ul>
 *
 * <p>Docker is required. When Docker is unavailable the suite is <em>skipped</em>
 * (via the inherited {@link RequiresDocker} condition) rather than failed, so
 * {@code ./gradlew build} stays green on machines without a Docker daemon. The
 * static initialiser is guarded by the same {@link DockerAvailability} check so
 * that class loading itself does not attempt a container start on Docker-less hosts.
 */
@SpringBootTest
@RequiresDocker
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @ServiceConnection
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    static {
        if (DockerAvailability.isDockerAvailable()) {
            POSTGRES.start();
            KAFKA.start();
        }
    }

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
