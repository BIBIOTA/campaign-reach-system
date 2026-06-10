# CLAUDE.md — AI Collaboration & Engineering Conventions

> This file is the **single source of truth** for AI assistants and developers.
> Module boundaries, build/check commands, and CI gate rules are all authoritative here.
> `AGENTS.md` is a symlink to this file — maintain only this one.

## Project Overview

campaign-reach-system is a **modular monolith** backend service for an e-commerce marketing campaign platform.
It uses Kafka to decouple campaign evaluation from reach execution. **Backend only** — no frontend.

Tech stack: Spring Boot 3 / Java 21, Gradle Kotlin DSL multi-module, Kafka, PostgreSQL (Testcontainers integration tests).

## Module Boundaries

There is a single deployable unit (`:app` owns the `bootJar` and `@SpringBootApplication CampaignReachApplication`),
containing three bounded modules. All packages are rooted at `com.example.campaignreach`:

| Module | Gradle | Responsibility | Package |
| --- | --- | --- | --- |
| **campaign** | `:campaign` | Campaign API, Campaign Consumer, Scheduler, Evaluators — decides who, when, and under what conditions | `…campaignreach.campaign` (`api` / `domain` / `evaluation` / `scheduler`) |
| **reach** | `:reach` | Orchestrator, AudienceResolver, Dispatcher, Channel/Email Adapter — executes actual reach delivery | `…campaignreach.reach` (`orchestrator` / `audience` / `channel` / `dispatcher`) |
| **shared** | `:shared` | Cross-module **stable contracts**: event schemas and configuration | `…campaignreach.shared` (`event` / `config`) |

### Boundary Rules (Hard Constraints)

- **campaign and reach communicate only via `shared/event` (Kafka events) — direct domain imports between them are forbidden.**
  Both trigger paths (API-triggered and Scheduler-triggered) converge on the same `reach.requested` topic.
- Both campaign and reach **may** depend on `shared`; they **must not** depend on each other's domain.
- `shared` contains only cross-module stable contracts (`event` / `config`) — campaign/reach entities, repositories, and services **must not** be placed here.
- These constraints are enforced by **ArchUnit**: `com.example.campaignreach.architecture.ModuleBoundaryTest` in `:app`
  verifies `campaign ↛ reach`, `reach ↛ campaign`, and `shared ↛ campaign/reach` (the kernel stays free of any dependency on either module). Any violation fails the test and blocks the gate.

> When modifying module boundaries, always update this section and `ModuleBoundaryTest` together to keep documentation and guard tests in sync.

## Build & Check Commands

All checks are centrally configured and applied to each module via the buildSrc convention plugin `campaignreach.java-conventions`.

| Command | Purpose |
| --- | --- |
| `./gradlew spotlessApply` | **Auto-format locally** (run this before committing). |
| `./gradlew spotlessCheck` | Formatting check. **Spotless (Palantir Java Format) is the single source of truth for formatting.** |
| `./gradlew checkstyleMain` | Style check (derived from google_checks, **formatting rules removed** to avoid overlap with Spotless). `maxErrors=0`. A `SuppressWarningsFilter` is enabled, so a scoped `@SuppressWarnings("checkstyle:<RuleName>")` suppresses that one rule at the annotated element — use it only for documented, justified cases (e.g. the deliberate broad `catch (RuntimeException)` that implements per-item exception isolation), never to broadly relax `maxErrors=0`. |
| `./gradlew spotbugsMain` | Static analysis (`effort=MAX` / `reportLevel=MEDIUM`). **High and Normal severity bugs are blocking.** |
| `./gradlew test` | Unit tests + **ArchUnit** boundary guards + **Testcontainers** integration tests. |
| `./gradlew check` | **Aggregate gate**: all of the above + JaCoCo coverage verification (`jacocoTestCoverageVerification`). |

Config file locations:
- Checkstyle: `config/checkstyle/checkstyle.xml`
- SpotBugs exclusions: `config/spotbugs/exclude.xml`
- Version catalog: `gradle/libs.versions.toml`

> Note: `checkstyleTest` / `spotbugsTest` are non-blocking (`ignoreFailures`); the gate only applies to `*Main`.

## CI Gate Rules

- Triggers: **PR** and push to `main` (`.github/workflows/ci.yml`).
- Gate: runner executes `./gradlew check`, equivalent to
  `spotlessCheck + checkstyleMain + spotbugsMain + test (unit + ArchUnit + Testcontainers) + JaCoCo verification`.
- **All checks must pass to merge; any failure blocks the gate** (design.md §11.5).
- **Testcontainers integration tests require Docker**: GitHub-hosted runners have Docker and execute them fully;
  **locally without Docker they are auto-skipped** (`@RequiresDocker` gate) without affecting other local checks.

## Maintenance Conventions

When **module boundaries** or **lint / CI commands** change, **always update this file** (along with the corresponding build scripts,
`ModuleBoundaryTest`, and `ci.yml`) to maintain a single source of truth and avoid drift between documentation and build scripts.
