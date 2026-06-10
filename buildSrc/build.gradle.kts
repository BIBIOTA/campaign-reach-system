plugins {
    `kotlin-dsl`
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    // Allow convention plugins to apply the Spring Boot / dependency-management plugins.
    implementation("org.springframework.boot:spring-boot-gradle-plugin:${catalog.findVersion("springBoot").get()}")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:${catalog.findVersion("springDependencyManagement").get()}")
    // Spotless formatting plugin so the java-conventions plugin can apply it across all modules.
    implementation("com.diffplug.spotless:spotless-plugin-gradle:${catalog.findVersion("spotless").get()}")
    // SpotBugs static-analysis plugin (Checkstyle + JaCoCo are built into Gradle).
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:${catalog.findVersion("spotbugs").get()}")
}
