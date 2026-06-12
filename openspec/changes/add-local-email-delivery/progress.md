# Progress: add-local-email-delivery

Implementation tracking for the SDD run (Task groups 1–4). Each status transition
appends one Session block below per the subagent-driven-development skill.

Branch: `feat/local-email-delivery` (off `main` @ ed27136)

## Session 1 — 2026-06-12
- Stage: SDD
- Task: 1.1 加入 Spring Mail 依賴與本機 SMTP 設定模型
- Transition: not_started → in_progress
- Next action: Implementer adds Spring Mail dependency to `:reach` and a validated `LocalSmtpEmailProperties` (host/port/from/recipient/timeout, fail-fast).

## Session 2 — 2026-06-12
- Stage: SDD
- Task: 1.1 加入 Spring Mail 依賴與本機 SMTP 設定模型
- Transition: in_progress → passing
- Evidence:
  - Commits: 59ccacb feat: add Spring Mail dependency and LocalSmtpEmailProperties (task 1.1)
  - Tests: `./gradlew :reach:test --tests '*LocalSmtpEmailProperties*'` BUILD SUCCESSFUL; checkstyleMain + spotbugsMain clean
  - Spec reviewer: ✅ Spec compliant (all 5 checks)
  - Code-quality reviewer: APPROVE after fix — Important issue (stray committed `buildSrc/.kotlin` build artifact) removed + `**/.kotlin/` added to .gitignore; Java/test code was approval-quality
- Next action: Start task 1.2 — register local SMTP `EmailProviderClient` only under `local` profile + `campaignreach.email-provider.mode=smtp-local`.

## Session 3 — 2026-06-12
- Stage: SDD
- Task: 2.1 實作本機簡易信件樣板 renderer
- Transition: not_started → in_progress
- Next action: Implementer adds `LocalEmailTemplateRenderer` producing subject (`[Local Campaign Reach]` + templateRef) and body (local notice, templateRef, userId, channel, send time). NOTE: execution order reordered to 2.1 → 2.2 → 1.2 so 1.2 can register the real provider bean instead of a stub; no declared dependency is violated (1.2 depends only on 1.1).

## Session 4 — 2026-06-12
- Stage: SDD
- Task: 2.1 實作本機簡易信件樣板 renderer
- Transition: in_progress → passing
- Evidence:
  - Commits: 8891b1b feat: add LocalEmailTemplateRenderer (task 2.1)
  - Tests: `./gradlew :reach:test --tests '*LocalEmailTemplateRenderer*'` BUILD SUCCESSFUL; checkstyleMain + spotbugsMain clean
  - Spec reviewer: ✅ Spec compliant (all 5 checks; render(message, sentAt) matches sequence diagram)
  - Code-quality reviewer: ✅ Approved (only Minor non-blocking nits)
- Next action: Start task 2.2 — implement `LocalSmtpEmailProviderClient` (fixed recipient, configured from, renderer content, SMTP send via JavaMailSender, SendResult with provider message id, transient failures stay retryable).

## Session 5 — 2026-06-12
- Stage: SDD
- Task: 2.2 實作 `LocalSmtpEmailProviderClient`
- Transition: not_started → in_progress
- Next action: Implementer adds `LocalSmtpEmailProviderClient implements EmailProviderClient`, sending via JavaMailSender to the fixed local recipient/from, returning SendResult; transient SMTP failures propagate as RuntimeException so EmailAdapter maps them to RetryableSendException.

## Session 6 — 2026-06-12
- Stage: SDD
- Task: 2.2 實作 `LocalSmtpEmailProviderClient`
- Transition: in_progress → passing
- Evidence:
  - Commits: e03d4ac feat: add LocalSmtpEmailProviderClient (task 2.2)
  - Tests: `./gradlew :reach:test --tests '*LocalSmtpEmailProviderClient*'` BUILD SUCCESSFUL; checkstyleMain + spotbugsMain clean
  - Spec reviewer: ✅ Spec compliant (call order matches sequence diagram; transient MailException propagates unwrapped → retryable)
  - Code-quality reviewer: ✅ Approved (only Minor nits; failure contract well-documented)
- Next action: Start task 1.2 — `LocalSmtpEmailConfig` registering JavaMailSender + the local `EmailProviderClient` bean only under `local` profile + `campaignreach.email-provider.mode=smtp-local`, enabling the existing EmailAdapter.

## Session 7 — 2026-06-12
- Stage: SDD
- Task: 1.2 加入本機 provider 啟用條件
- Transition: not_started → in_progress
- Next action: Implementer adds `LocalSmtpEmailConfig` (component-scanned `@Configuration` gated by `@Profile("local")` + `@ConditionalOnProperty(...mode=smtp-local)`) registering JavaMailSender/Clock/renderer/`EmailProviderClient` beans, with ApplicationContextRunner tests proving the three registration conditions and EmailAdapter activation.

## Session 8 — 2026-06-12
- Stage: SDD
- Task: 1.2 加入本機 provider 啟用條件
- Transition: in_progress → passing
- Evidence:
  - Commits: 7841ff1 feat: register local SMTP EmailProviderClient under local + smtp-local mode
  - Tests: `./gradlew :reach:test --tests '*LocalSmtpEmailConfig*'` BUILD SUCCESSFUL (3 tests, 0 failures, no Docker); checkstyleMain + spotbugsMain clean
  - Spec reviewer: ✅ Spec compliant (all 6 checks; EmailAdapter-activation assertion genuinely passes via real auto-config phase ordering)
  - Code-quality reviewer: ✅ Approved (no Critical/Important; only Minor notes)
- Next action: Start task 2.3 — assert PII minimization & conservative provider logging (no email address in reach_task/event/ReachMessage/metrics/audit; no full body in logs).

## Session 9 — 2026-06-12
- Stage: SDD
- Task: 2.3 維持 PII 最小化與 provider 邊界
- Transition: not_started → in_progress
- Next action: Implementer adds a conservative success log (providerMessageId + userId only, never recipient/body) to LocalSmtpEmailProviderClient and tests proving the recipient is sourced only from config and never leaks into ReachMessage/body/logs.

## Session 10 — 2026-06-12
- Stage: SDD
- Task: 2.3 維持 PII 最小化與 provider 邊界
- Transition: in_progress → passing
- Evidence:
  - Commits: 9e294ee feat: enforce + prove PII boundary and conservative logging for local SMTP provider (task 2.3)
  - Tests: `./gradlew :reach:test --tests '*LocalSmtpEmailProviderClient*'` BUILD SUCCESSFUL (4 tests, 0 failures, no Docker); full `:reach:test` green; checkstyleMain + spotbugsMain clean
  - Spec reviewer: ✅ Spec compliant (recipient from config only; ReachMessage has no email field; log excludes recipient/from/subject/body)
  - Code-quality reviewer: ✅ Approved (only Minor nits)
- Next action: Start task 3.1 — add Mailpit service (SMTP 1025 / Web UI 8025) to local docker-compose.yml.

## Session 11 — 2026-06-12
- Stage: SDD
- Task: 3.1 將 Mailpit 加入本機 `docker-compose.yml`
- Transition: not_started → in_progress
- Next action: Implementer adds a Mailpit service to docker-compose.yml exposing SMTP 1025 and Web UI 8025, matching the existing compose conventions (named container, restart policy, overridable ports).

## Session 12 — 2026-06-12
- Stage: SDD
- Task: 3.1 將 Mailpit 加入本機 `docker-compose.yml`
- Transition: in_progress → passing
- Evidence:
  - Commits: 4acd768 feat: add Mailpit to local docker-compose for email smoke tests (amended to include MAILPIT_* host-port vars in .env.example + header wording fix)
  - Tests: `docker compose config` exit 0; real `docker compose up -d mailpit` smoke test reached healthy, `/readyz` OK, then cleaned up
  - Spec reviewer: ✅ Spec compliant (all three services start; SMTP 1025 + UI 8025 exposed; image pinned)
  - Code-quality reviewer: ✅ Approved after fix (added MAILPIT_SMTP_PORT/MAILPIT_UI_PORT to .env.example host-port section; fixed stale "both services" wording)
- Note: the two MAILPIT_* host-port-mapping vars were folded into 3.1 (they parallel POSTGRES_PORT/KAFKA_PORT). Task 3.2 covers the email-provider-specific settings (mode, SMTP host/from/recipient, profile) only — must NOT re-add the port vars.
- Next action: Start task 3.2 — add local email-provider settings (mode=smtp-local, SMTP host/from/recipient, local profile hint) to .env.example so defaults point at Mailpit.

## Session 13 — 2026-06-12
- Stage: SDD
- Task: 3.2 更新 `.env.example` 的本機 email 設定
- Transition: not_started → in_progress
- Next action: Implementer adds local SMTP provider settings (profile=local, mode=smtp-local, SMTP host/port/from/recipient/timeout) to .env.example via Spring relaxed-binding env var names so defaults point at Mailpit, without re-adding the MAILPIT_* port vars (already in 3.1) and without production-misusable application.yml defaults.

## Session 14 — 2026-06-12
- Stage: SDD
- Task: 3.2 更新 `.env.example` 的本機 email 設定
- Transition: in_progress → passing
- Evidence:
  - Commits: 9d987bd feat: add local SMTP email settings to .env.example + application.yml placeholders (task 3.2)
  - Correctness fix: relaxed-binding env name `CAMPAIGNREACH_EMAILPROVIDER_MODE` would NOT activate `@ConditionalOnProperty` (Environment resolves hyphen→underscore, not dropped). Rerouted through `application.yml` `${ENV:}` placeholders (friendly names EMAIL_PROVIDER_MODE / LOCAL_SMTP_*), matching the existing `api-key: ${EMAIL_PROVIDER_API_KEY:}` pattern. Empty defaults keep prod safe.
  - Tests: `set -a; source .env.example` OK; `./gradlew :app:processResources` exit 0 (yaml parses)
  - Spec reviewer: ✅ Spec compliant (end-to-end .env→yaml→property→Java names aligned; empty defaults; Mailpit targeting)
  - Code-quality reviewer: ✅ Approved (only Minor cosmetic nits)
- Note: application.yml WAS edited this task (placeholders only, empty defaults) — superseding Session 13's "no application.yml change" intent; this is required for correct @ConditionalOnProperty activation and matches the existing env-placeholder convention.
- Next action: Start task 3.3 — README local-email smoke-test section (compose up, load .env, start app, trigger EMAIL reach, view at http://localhost:8025; document Mailpit no-external-send + fixed recipient).

## Session 15 — 2026-06-12
- Stage: SDD
- Task: 3.3 更新 README 本機寄信 smoke test
- Transition: not_started → in_progress
- Next action: Implementer adds a README section walking through compose up → load .env → start app (local profile) → trigger one EMAIL reach → view at http://localhost:8025, and documents Mailpit captures locally / fixed recipient is smoke-test only.

## Session 16 — 2026-06-12
- Stage: SDD
- Task: 3.3 更新 README 本機寄信 smoke test
- Transition: in_progress → passing
- Evidence:
  - Commits: 2a4f052 docs: add local email smoke-test section to README (task 3.3) (amended with cross-ref label fix)
  - Tests: doc task — all curl endpoints/commands cross-checked against CampaignController / ReachMetricsController / CampaignReachScanScheduler / docker-compose.yml / .env.example by the reviewer
  - Spec reviewer: ✅ Spec compliant (all 6 steps present + factually accurate; async caveat honest)
  - Code-quality reviewer: ✅ Approved (only Minor; §5.1/§5.2 label fixed post-review)
- Next action: Start task 4.1 — config & context tests (fail-fast on invalid/missing/timeout settings; provider not registered on profile/mode mismatch; provider + EmailAdapter active under local+smtp-local).

## Session 17 — 2026-06-12
- Stage: SDD
- Task: 4.1 加入設定與 context 測試
- Transition: not_started → in_progress
- Next action: Implementer adds context-level fail-fast tests (local+smtp-local with missing/invalid/zero-timeout settings → ApplicationContextRunner reports startup failure), reusing the registration-gating + EmailAdapter-activation coverage already in LocalSmtpEmailConfigTest (task 1.2) rather than duplicating it.

## Session 18 — 2026-06-12
- Stage: SDD
- Task: 4.1 加入設定與 context 測試
- Transition: in_progress → passing
- Evidence:
  - Commits: 4458a99 test: add context-level fail-fast tests for local SMTP config (task 4.1)
  - Tests: `LocalSmtpEmailConfigTest` now 7 tests (3 from 1.2 + 4 new context fail-fast: missing host, missing recipient, timeout=0s, port=70000), all green; full `:reach:test` green; checkstyle/spotbugs clean; no Docker
  - Spec reviewer: ✅ Spec compliant (all 3 acceptance bullets covered across suite; new tests are genuine context/binding-level fail-fast, not duplicates)
  - Code-quality reviewer: ✅ Approved (well-factored via helpers; real contract-string assertions; only Minor nits)
- Next action: Start task 4.2 — renderer + provider unit tests. NOTE: renderer tests (LocalEmailTemplateRendererTest, task 2.1) and provider tests incl. transient-retryable path + PII (LocalSmtpEmailProviderClientTest, tasks 2.2/2.3) already exist; 4.2 verifies completeness and fills any gap.

## Session 19 — 2026-06-12
- Stage: SDD
- Task: 4.2 加入 renderer 與 provider 單元測試
- Transition: not_started → in_progress
- Next action: Implementer audits existing renderer/provider tests against 4.2 acceptance and adds the genuine gap — an end-to-end test wrapping LocalSmtpEmailProviderClient in a real EmailAdapter to prove a transient SMTP MailException surfaces as RetryableSendException (providerFailure) — without duplicating the subject/body and success-path tests already in place.

## Session 20 — 2026-06-12
- Stage: SDD
- Task: 4.2 加入 renderer 與 provider 單元測試
- Transition: in_progress → passing
- Evidence:
  - Commits: b36a09e test: prove local SMTP transient failure surfaces as RetryableSendException end-to-end via EmailAdapter (task 4.2)
  - Tests: LocalSmtpEmailProviderClientTest now 5 tests, LocalEmailTemplateRendererTest 3 tests, all green; full `:reach:test` green; checkstyle/spotbugs clean; no Docker
  - Spec reviewer: ✅ Spec compliant (all 3 bullets covered across suite; new test is genuine end-to-end mapping via real EmailAdapter, not a duplicate)
  - Code-quality reviewer: ✅ Approved (DRY reuse of fixtures; distinct from provider-boundary test; only Minor naming nits)
- Next action: Start task 4.3 — run the full quality gate `./gradlew spotlessCheck checkstyleMain spotbugsMain test` and record results (Java 21 + Docker are available locally, so the gate can run fully).

## Session 21 — 2026-06-12
- Stage: SDD
- Task: 4.3 跑本 change 的品質 gate
- Transition: not_started → in_progress
- Next action: Run the aggregate gate `./gradlew check` (spotlessCheck + checkstyleMain + spotbugsMain + test incl. ArchUnit + Testcontainers + JaCoCo) and record the result; fix any failure before marking passing.

## Session 22 — 2026-06-12
- Stage: SDD
- Task: 4.3 跑本 change 的品質 gate
- Transition: in_progress → passing
- Evidence:
  - Commits: 06fd2fc test: rename renderer test method to clear checkstyle abbreviation nit (task 4.3)
  - Gate: `./gradlew check` → BUILD SUCCESSFUL (Java 21 + Docker). Blocking tasks pass: spotlessCheck, checkstyleMain, spotbugsMain, test (campaign/reach/shared incl. Testcontainers), ArchUnit ModuleBoundaryTest (3 tests, no boundary violation), jacocoTestCoverageVerification. New Local* tests ran green: LocalEmailTemplateRendererTest(3), LocalSmtpEmailConfigTest(7), LocalSmtpEmailPropertiesTest(2), LocalSmtpEmailProviderClientTest(5).
  - Note: gate surfaced one new non-blocking checkstyleTest nit (AbbreviationAsWordInName) introduced by 2.1's test → fixed via method rename; checkstyleTest/spotbugsTest are ignoreFailures by project policy and remaining warnings are pre-existing/unrelated.
  - Spec reviewer: ✅ Spec compliant (independently re-ran gate → BUILD SUCCESSFUL; new tests not skipped; boundaries intact)
  - Code-quality reviewer: ✅ Approved (minimal one-line rename, DisplayName/mapping preserved, no dangling refs)
- Next action: Tasks 1–4 complete; run `openspec validate add-local-email-delivery --strict` and transition to verification-before-completion. Section 5 (newman e2e) is out of scope for this run (user requested Tasks 1–4).

## Session 23 — 2026-06-12 16:25
- Stage: SDD
- Task: 5.1 擴充 Postman collection 為全鏈路驗收流程
- Transition: not_started → in_progress
- Next action: Implementer expands `docs/postman/campaign-reach.postman_collection.json` with an ordered EMAIL end-to-end flow that creates a campaign, transitions it to RUNNING, and polls metrics until a SENT task appears using existing Basic Auth variables.

## Session 24 — 2026-06-12 16:35
- Stage: SDD
- Task: 5.1 擴充 Postman collection 為全鏈路驗收流程
- Transition: in_progress → passing
- Evidence:
  - Commits: 6d0f2cb Add EMAIL e2e Postman flow for task 5.1
  - Tests: `node -e "JSON.parse(...)"` OK; custom structure check verified create → DRAFT→SCHEDULED → SCHEDULED→RUNNING → bounded metrics polling until `statusDistribution.SENT > 0`; `git diff --check HEAD~1..HEAD` clean.
  - Spec reviewer: ✅ Spec compliant (ordered EMAIL flow, no fixed one-shot sleep, bounded `postman.setNextRequest` polling, existing `basicAuthUsername` / `basicAuthPassword` reused with no new auth variables).
  - Code-quality reviewer: ✅ Approved (single collection-only change; clear variables and fail-fast placeholder for Task 5.3 environment setup; no unrelated generated-doc churn).
- Next action: Start task 5.2 — add Mailpit HTTP API assertion after metrics reports SENT and provide mailbox cleanup guidance to avoid rerun contamination.

## Session 25 — 2026-06-12 16:36
- Stage: SDD
- Task: 5.2 加入 Mailpit HTTP API 寄送斷言
- Transition: not_started → in_progress
- Next action: Implementer extends the local EMAIL e2e collection with a Mailpit HTTP API assertion request that verifies a captured message subject contains `[Local Campaign Reach]` and the dynamic `templateRef`, then adds mailbox cleanup guidance or a cleanup request to prevent rerun contamination.

## Session 26 — 2026-06-12 16:43
- Stage: SDD
- Task: 5.2 加入 Mailpit HTTP API 寄送斷言
- Transition: in_progress → passing
- Evidence:
  - Commits: 5102dc5 Add Mailpit assertion to email e2e collection
  - Tests: `node -e "JSON.parse(...)"` OK; inspected flow order now continues from metrics SENT to Mailpit assertion; request uses `{{mailpitBaseUrl}}/api/v1/messages`, `noauth`, and subject matching on `[Local Campaign Reach]` plus dynamic `e2eTemplateRef`.
  - Spec reviewer: ✅ Spec compliant (Mailpit HTTP API assertion runs only after metrics observes SENT; cleanup guidance request/logs are present to avoid rerun contamination).
  - Code-quality reviewer: ✅ Approved (defensive response parsing for `messages`/`Messages`/array; no destructive API call added without needing it; no unrelated edits).
- Next action: Start task 5.3 — add a Newman execution script and local environment file, including setup for the static audience list id required by the Postman flow.

## Session 27 — 2026-06-12 16:44
- Stage: SDD
- Task: 5.3 提供 newman 執行腳本與環境檔
- Transition: not_started → in_progress
- Next action: Implementer adds a local Newman environment file and executable script that seeds the required static audience list, clears Mailpit, and runs the EMAIL e2e Postman folder against an already-started local stack.

## Session 28 — 2026-06-12 16:50
- Stage: SDD
- Task: 5.3 提供 newman 執行腳本與環境檔
- Transition: in_progress → passing
- Evidence:
  - Commits: 555a142 Add local Newman email e2e runner for task 5.3
  - Tests: `bash -n docs/scripts/run-local-email-e2e.sh` OK; Postman environment JSON parse OK; custom structure check verified env keys and script overrides for `baseUrl`, `basicAuthUsername`, `basicAuthPassword`, `mailpitBaseUrl`, `e2eAudienceListId`, and poll tunables; `git diff --check HEAD~1..HEAD` clean.
  - Spec reviewer: ✅ Spec compliant (script seeds the required static audience list, clears Mailpit before run, invokes Newman against the EMAIL e2e folder, and environment contains local non-secret defaults).
  - Code-quality reviewer: ✅ Approved (small single-purpose shell runner, configurable via environment variables, no CI wiring added).
- Next action: Start task 5.4 — document how to run the local Newman EMAIL e2e acceptance and clarify Mailpit/fixed-recipient/manual-not-CI constraints.

## Session 29 — 2026-06-12 16:51
- Stage: SDD
- Task: 5.4 更新 README 端到端驗收說明
- Transition: not_started → in_progress
- Next action: Implementer updates README with the local Newman EMAIL end-to-end acceptance steps, including stack/app prerequisites, the runner command, Mailpit/fixed-recipient dependency, and the fact that this manual script is outside the CI gate.

## Session 30 — 2026-06-12 16:55
- Stage: SDD
- Task: 5.4 更新 README 端到端驗收說明
- Transition: in_progress → passing
- Evidence:
  - Commits: 54e92fe Document local Newman email e2e acceptance for task 5.4
  - Tests: `rg "^## [0-9]\\." README.md` confirmed top-level numbering remains coherent; `git diff --check -- README.md` clean.
  - Spec reviewer: ✅ Spec compliant (README covers stack startup, `.env`, local profile / smtp-local app startup, `docs/scripts/run-local-email-e2e.sh`, Mailpit API assertion, fixed local recipient, and non-CI-gate scope).
  - Code-quality reviewer: ✅ Approved (docs are scoped to the local acceptance workflow and reuse existing README conventions).
- Next action: Run Task 5 final validation, confirm all Task 5 items are passing, then invoke verification-before-completion.
