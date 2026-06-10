plugins {
    id("campaignreach.java-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter)

    // Base cross-module connectivity contracts (design.md §3): DataSource (JPA),
    // Kafka client and JSR-380 validation for fail-fast config binding.
    // Topic / schema / entity definitions belong to later tasks (2.x, 3.x).
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.kafka)
    implementation(libs.spring.boot.starter.validation)

    // PostgreSQL driver is a runtime concern of the deployable; keep it here so
    // the shared DataSource config resolves a driver at startup (fail-fast).
    runtimeOnly(libs.postgresql)

    testImplementation(libs.junit.jupiter)
}
