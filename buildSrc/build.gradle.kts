plugins {
    `kotlin-dsl`
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    // Allow convention plugins to apply the Spring Boot / dependency-management plugins.
    implementation("org.springframework.boot:spring-boot-gradle-plugin:${catalog.findVersion("springBoot").get()}")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:${catalog.findVersion("springDependencyManagement").get()}")
}
