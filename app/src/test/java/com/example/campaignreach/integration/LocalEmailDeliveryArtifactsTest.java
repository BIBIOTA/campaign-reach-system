package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalEmailDeliveryArtifactsTest {

    private static final Path ROOT = findProjectRoot();

    @Test
    @DisplayName("Docker compose starts Mailpit with local infrastructure")
    void dockerComposeStartsMailpitWithLocalInfrastructure() throws IOException {
        String compose = Files.readString(ROOT.resolve("docker-compose.yml"));

        assertThat(compose)
                .contains("postgres:")
                .contains("kafka:")
                .contains("mailpit:")
                .contains("${MAILPIT_SMTP_PORT:-1025}:1025")
                .contains("${MAILPIT_UI_PORT:-8025}:8025");
    }

    @Test
    @DisplayName("Local smoke test is documented")
    void localSmokeTestIsDocumented() throws IOException {
        String readme = Files.readString(ROOT.resolve("README.md"));

        assertThat(readme)
                .contains("本機寄信 smoke test")
                .contains("docker compose up -d")
                .contains("SPRING_PROFILES_ACTIVE=local")
                .contains("EMAIL_PROVIDER_MODE=smtp-local")
                .contains("http://localhost:8025")
                .contains("Mailpit 只在本機")
                .contains("LOCAL_SMTP_RECIPIENT");
    }

    private static Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("project root not found");
    }
}
