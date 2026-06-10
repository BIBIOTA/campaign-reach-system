plugins {
    id("campaignreach.java-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter)

    // The Campaign aggregate is JPA-persisted (task 3.1). The PostgreSQL driver is
    // a runtime concern; the schema itself is owned by Flyway migrations in :app.
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql)

    // RuleConfig DTOs (task 3.2): JSR-380 schema validation of per-type rule
    // configs and Jackson (de)serialization to/from the rule_config JSONB column.
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.databind)

    // Internal REST (activity CRUD) + HTTP Basic auth with the OPERATOR back-office
    // role (task 4.1, §10). Controllers/DTOs/security all stay under campaign.api.
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)

    // campaign may only talk to reach through the shared kernel (event/config).
    // It must NOT depend on :reach — enforced by the ArchUnit guard in :app.
    implementation(project(":shared"))

    // Web/MockMvc slice + security tests for the campaign CRUD endpoints (task 4.1).
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
}
