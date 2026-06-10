plugins {
    id("campaignreach.java-conventions")
}

// :shared holds the only executable production logic in Section 1 (config binding
// + fail-fast validator), so it enforces a real JaCoCo instruction-coverage floor
// instead of the 0.00 default reserved for marker-only modules (design.md §11.5).
// Current measured coverage is ~0.84; 0.70 leaves headroom while keeping the gate
// meaningful. Raise as more logic and tests land.
extra["jacocoMinCoverage"] = "0.70"

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
