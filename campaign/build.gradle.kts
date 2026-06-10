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
    implementation(libs.jackson.datatype.jsr310)

    // campaign may only talk to reach through the shared kernel (event/config).
    // It must NOT depend on :reach — enforced by the ArchUnit guard in :app.
    implementation(project(":shared"))
}
