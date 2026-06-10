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

    // Architecture guard.
    testImplementation(libs.archunit.junit5)
}
