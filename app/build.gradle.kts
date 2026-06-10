plugins {
    id("campaignreach.java-conventions")
    // Version comes from the Spring Boot gradle plugin already on the build
    // classpath via buildSrc (declared in gradle/libs.versions.toml).
    id("org.springframework.boot")
}

dependencies {
    implementation(libs.spring.boot.starter)

    // The single deployable aggregates all three bounded modules so the
    // @SpringBootApplication can component-scan campaign / reach / shared.
    implementation(project(":campaign"))
    implementation(project(":reach"))
    implementation(project(":shared"))

    // Flyway owns the schema (Hibernate ddl-auto: none). The deployable runs the
    // migrations on startup; PostgreSQL 16 requires the -postgresql module too.
    // JPA + driver are needed at runtime so the migrated Campaign entity persists.
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    // Architecture guard.
    testImplementation(libs.archunit.junit5)

    // Integration testing on real infrastructure (design.md §7). The broker is
    // NEVER mocked: a real Kafka container and a real PostgreSQL container back
    // the module-boundary integration tests. Versions flow from the
    // Testcontainers BOM so nothing is hard-coded here.
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.spring.boot.testcontainers)

    // The smoke integration test drives a real KafkaTemplate against the
    // container broker and persists via JPA, so the app's own test classpath
    // needs these starters (they are `implementation`-scoped in :shared and so do
    // not leak onto this compile classpath transitively).
    testImplementation(libs.spring.kafka)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testRuntimeOnly(libs.postgresql)

    // The campaign HTTP API integration test (CampaignApiIntegrationTest) drives the
    // real /internal/campaigns endpoints through MockMvc + the full Spring Security
    // filter chain. :campaign's web/security starters are `implementation`-scoped, so
    // MockMvc and the httpBasic test post-processor are not on this compile classpath
    // transitively — declare them here for the test sources.
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.security.test)
}
