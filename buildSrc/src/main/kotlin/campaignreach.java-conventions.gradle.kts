plugins {
    java
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
}

// Resolve the version catalog at runtime so this precompiled script plugin does
// not depend on the generated `LibrariesForLibs` accessor type.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "com.example.campaignreach"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion.toInt()))
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    add("testImplementation", libs.findLibrary("spring-boot-starter-test").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Code formatting — single source of truth: Palantir Java Format (4-space indent).
// No custom layout rules, no per-file/per-block toggles (design.md §11.2).
spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.findVersion("palantirJavaFormat").get().requiredVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
