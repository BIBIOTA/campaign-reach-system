plugins {
    id("campaignreach.java-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter)

    // The reach_request batch is JPA-persisted (task 7.1). The PostgreSQL driver is a
    // runtime concern; the schema itself is owned by Flyway migrations in :app.
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql)

    // Kafka consumer side (task 7.1): the orchestrator consumes activity-level
    // ReachRequested events from reach.requested via its own typed at-least-once factory.
    implementation(libs.spring.kafka)

    // Jackson is referenced directly when configuring the typed JsonDeserializer for the
    // ReachRequested payload (spring-kafka exposes jackson-core types on that API surface).
    implementation(libs.jackson.databind)

    // Circuit breaker (task 8.1, NFR-004): wraps the EmailAdapter so an unavailable Email
    // provider degrades stably (open on failure-rate threshold, cool-down, half-open probing)
    // instead of cascading. The Spring Boot 3 starter auto-configures the CircuitBreakerRegistry
    // and is tuned via EmailChannelProperties / programmatic CircuitBreakerConfig.
    implementation(libs.resilience4j.spring.boot3)

    // Internal reach-metrics read REST (task 10.3, FR-018): a read-only query slice exposing
    // campaign-level reach metrics + per-recipient status under /internal/reach/**. These endpoints
    // are secured by the same /internal/** OPERATOR HTTP-Basic filter chain wired in the campaign
    // module (CampaignSecurityConfig), so no second SecurityFilterChain is added here.
    implementation(libs.spring.boot.starter.web)

    // reach may only talk to campaign through the shared kernel (event/config).
    // It must NOT depend on :campaign — enforced by the ArchUnit guard in :app.
    implementation(project(":shared"))

    // Unit tests for the orchestrator batch-landing decision logic (task 7.1).
    testImplementation(libs.spring.boot.starter.test)

    // Web/MockMvc slice + security tests for the reach-metrics read endpoints (task 10.3). The
    // /internal/** OPERATOR chain is owned by the campaign module at runtime (CampaignSecurityConfig);
    // these test-only deps let the reach slice mirror that auth contract in isolation.
    testImplementation(libs.spring.boot.starter.security)
    testImplementation(libs.spring.security.test)
}
