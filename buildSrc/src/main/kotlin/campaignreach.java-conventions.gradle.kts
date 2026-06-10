plugins {
    java
    checkstyle
    jacoco
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
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

// Retain method/constructor parameter names in the bytecode. Spring MVC relies on them
// to infer @PathVariable / @RequestParam names without an explicit value, and Spring Data
// binds @ConfigurationProperties record components by name. Only :app applies the Spring
// Boot plugin (which would add this), so set it centrally for every module here.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
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
    // SpotBugs annotations (@SuppressFBWarnings, @Nullable, ...) — compile-time only.
    add("compileOnly", libs.findLibrary("spotbugs-annotations").get())
    add("testCompileOnly", libs.findLibrary("spotbugs-annotations").get())
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

// ---------------------------------------------------------------------------
// Style check — Checkstyle (design.md §11.3).
// google_checks base style with ALL layout/whitespace modules removed (those
// are owned by Spotless). Config lives in config/checkstyle/checkstyle.xml at
// the repo root and is shared by every module. A violation fails the build.
// Acceptance points only at `checkstyleMain`, so main is strict (maxErrors=0);
// test sources are checked with a relaxed visibility model to avoid breaking on
// existing test fixtures while still catching naming/structure issues.
// ---------------------------------------------------------------------------
checkstyle {
    toolVersion = libs.findVersion("checkstyle").get().requiredVersion
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    maxErrors = 0
    isIgnoreFailures = false
}

// Checkstyle's Javadoc/visibility expectations are tuned for production code;
// don't fail the build on test fixtures, but still report them.
tasks.named<Checkstyle>("checkstyleTest") {
    isIgnoreFailures = true
}

// ---------------------------------------------------------------------------
// Static analysis — SpotBugs (design.md §11.3).
// effort=MAX + reportLevel=MEDIUM => High AND Normal (Medium-and-above) priority
// bugs fail the build. A narrow exclude filter removes known framework false
// positives only. Acceptance points at `spotbugsMain`; test analysis is opt-in.
// ---------------------------------------------------------------------------
spotbugs {
    toolVersion.set(libs.findVersion("spotbugsTool").get().requiredVersion)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
    ignoreFailures = false
}

tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsMain") {
    reports.create("html") { required.set(true) }
}

// Don't gate the build on test-source SpotBugs analysis (test doubles trip many
// rules); keep it available but non-failing.
tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsTest") {
    ignoreFailures = true
}

// ---------------------------------------------------------------------------
// Coverage — JaCoCo (design.md §11.5).
// Reports + a minimum instruction-coverage gate wired into `check`. Each module
// sets its own floor via the `jacocoMinCoverage` project property: modules with
// real production logic (e.g. :shared) enforce a meaningful floor, while modules
// that today hold only package-info / marker types report 0/0 instructions and
// must keep the default 0.00 (JaCoCo treats 0/0 as a failure against any positive
// minimum). As core logic lands in later tasks, raise each module's floor.
// Floors are kept at or below the current measured coverage so a clean checkout
// never "bare-fails".
// ---------------------------------------------------------------------------
jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                // Per-module floor: a module sets `jacocoMinCoverage` in its own
                // build script to enforce a meaningful minimum (see :shared). The
                // default 0.00 applies to marker-only modules that report 0/0
                // instructions, which JaCoCo would fail against any positive floor.
                // Read lazily here so the module's value (set after the plugin is
                // applied) is honoured.
                minimum = (findProperty("jacocoMinCoverage") as String?)?.toBigDecimal()
                    ?: "0.00".toBigDecimal()
            }
        }
    }
}

// Single aggregate gate: `./gradlew check` runs spotlessCheck + checkstyleMain
// + spotbugsMain + test (unit + ArchUnit + Testcontainers) + coverage verify.
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
