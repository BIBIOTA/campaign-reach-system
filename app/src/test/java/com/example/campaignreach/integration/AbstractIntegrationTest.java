package com.example.campaignreach.integration;

import org.springframework.boot.test.context.SpringBootTest;
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
 *   <li>Connectivity is published via {@link DynamicPropertySource} into the exact
 *       standard properties Spring Boot and the fail-fast
 *       {@code RequiredInfrastructurePropertiesValidator} both read —
 *       {@code spring.datasource.url/username/password} and
 *       {@code spring.kafka.bootstrap-servers}. We deliberately do <em>not</em> use
 *       {@code @ServiceConnection}: it injects {@code ConnectionDetails} beans
 *       consumed directly by the auto-configurations but never populates these
 *       {@code Environment} properties, so the validator (which reads the
 *       {@code Environment}) would see them as missing and fail startup. Sourcing
 *       both the real wiring and the validator from one property set keeps a single
 *       source of truth and mirrors how production resolves them from
 *       {@code application.yml}.</li>
 *   <li>The Email provider API key has no container source, so a non-secret test
 *       value is supplied via the same {@link DynamicPropertySource} to pass the
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

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    static {
        if (DockerAvailability.isDockerAvailable()) {
            POSTGRES.start();
            KAFKA.start();
        }
    }

    /**
     * Publishes the running containers' connectivity into the standard Spring Boot
     * properties that both the auto-configurations and the fail-fast
     * {@code RequiredInfrastructurePropertiesValidator} read, plus a non-secret
     * placeholder for the Email provider API key so the {@code @NotBlank} binding on
     * {@code EmailProviderProperties} passes. All values are test fixtures, never
     * real credentials.
     */
    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("campaignreach.email-provider.api-key", () -> "test-email-provider-api-key");
        // A throwaway back-office operator so OperatorProperties' @NotBlank/@NotNull binding passes
        // when the full context boots (production resolves these from env/vault, never defaults).
        registry.add("campaignreach.security.operators[0].username", () -> "it-operator");
        registry.add("campaignreach.security.operators[0].password", () -> "it-operator-secret");
        registry.add("campaignreach.security.operators[0].id", () -> "00000000-0000-0000-0000-0000000000ab");
    }
}
