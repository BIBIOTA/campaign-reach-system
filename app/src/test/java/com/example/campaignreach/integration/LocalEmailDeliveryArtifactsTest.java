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
        // The getting-started guide was extracted out of README.md into docs/getting-started.md (#26),
        // so the smoke-test walkthrough now lives there.
        String guide = Files.readString(ROOT.resolve("docs/getting-started.md"));

        assertThat(guide)
                .contains("本機寄信 smoke test")
                .contains("docker compose up -d")
                .contains("SPRING_PROFILES_ACTIVE=local")
                .contains("EMAIL_PROVIDER_MODE=smtp-local")
                .contains("http://localhost:8025")
                .contains("Mailpit 只在本機")
                .contains("LOCAL_SMTP_RECIPIENT");
    }

    @Test
    @DisplayName("Postman collection contains the ordered EMAIL e2e acceptance flow")
    void postmanCollectionContainsEmailE2eFlow() throws IOException {
        String collection = Files.readString(ROOT.resolve("docs/postman/campaign-reach.postman_collection.json"));

        // SCHEDULED is the only manual lifecycle edge; the lifecycle scheduler auto-advances
        // SCHEDULED -> RUNNING at the past startAt, so there is deliberately no manual
        // "Transition SCHEDULED to RUNNING" step (it would race the scheduler and 409 on a stale version).
        assertThat(collection)
                .contains("local/manual EMAIL e2e acceptance")
                .contains("E2E 1 - Create EMAIL campaign")
                .contains("E2E 2 - Transition DRAFT to SCHEDULED")
                .contains("E2E 3 - Poll metrics until SENT")
                .contains("E2E 4 - Assert Mailpit captured EMAIL")
                .doesNotContain("Transition SCHEDULED to RUNNING");
        // Async reach is awaited via capped polling, never a fixed sleep.
        assertThat(collection)
                .contains("e2eMaxPollAttempts")
                .contains("e2ePollAttempt")
                .doesNotContain("setTimeout(");
        // Mailpit assertion hits the messages API and checks the subject markers (task 5.2).
        // The Postman URL stores the path as ordered segments, so match those.
        assertThat(collection.replaceAll("\\s+", "")).contains("\"api\",\"v1\",\"messages\"");
        assertThat(collection)
                .contains("{{mailpitBaseUrl}}")
                .contains("[Local Campaign Reach]")
                .contains("e2eTemplateRef");
        // Reuses existing basic-auth variables (overridden by the Newman env file).
        assertThat(collection).contains("basicAuthUsername").contains("basicAuthPassword");
    }

    @Test
    @DisplayName("Newman environment file exposes overridable params without secrets")
    void newmanEnvironmentExposesOverridableParams() throws IOException {
        String environment = Files.readString(ROOT.resolve("docs/postman/local-email-e2e.postman_environment.json"));

        assertThat(environment)
                .contains("baseUrl")
                .contains("mailpitBaseUrl")
                .contains("basicAuthUsername")
                .contains("basicAuthPassword")
                .contains("e2eMaxPollAttempts")
                .contains("e2ePollIntervalMs");
    }

    @Test
    @DisplayName("Newman runner script paces polling and clears Mailpit before the run")
    void newmanRunnerScriptPacesPollingAndClearsMailpit() throws IOException {
        String script = Files.readString(ROOT.resolve("docs/scripts/run-local-email-e2e.sh"));

        assertThat(script)
                .contains("set -euo pipefail")
                // Poll interval must reach Newman so requests are actually paced.
                .contains("--delay-request \"${E2E_POLL_INTERVAL_MS}\"")
                // Clears the mailbox before the run to avoid cross-run pollution.
                .contains("-X DELETE \"${MAILPIT_BASE_URL}/api/v1/messages\"")
                .contains("--folder \"${FOLDER}\"");
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
