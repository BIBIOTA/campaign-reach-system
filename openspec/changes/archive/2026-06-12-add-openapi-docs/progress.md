# Progress — add-openapi-docs

## Session 1 — 2026-06-12 11:00
- Stage: TDD
- Task: 4.1 / 2.1 / 2.2 / 3.1 / 3.2 / 3.3 / 1.1 / 1.2 (runtime docs core，behavioral red→green 綁定)
- Transition: not_started → in_progress
- Evidence:
  - Commits: （pending — 先寫 RED 測試）
  - Tests: 預計新增 `OpenApiSpecExportTest`、`SwaggerDocsSecurityIntegrationTest`
- Next action: 撰寫 RED 整合測試，斷言 `/v3/api-docs` 200 + 繁中描述 + basicAuth scheme、`/swagger-ui` 可存取、spec 匯出到檔；在加入 springdoc 前應因 404 失敗。
- Note: 1.1/1.2（版本目錄與 build 依賴）與下游純設定任務（5.1/6.x）無 JVM 層 failing test，其驗收以 build 解析 / 工具執行 / workflow 驗證為證據，於對應 session 記錄。

## Session 2 — 2026-06-12 11:30
- Stage: TDD
- Task: 1.1 / 1.2 / 2.1 / 2.2 / 3.1 / 3.2 / 3.3 / 4.1 / 4.2 (runtime docs core)
- Transition: in_progress → passing
- Evidence:
  - Commits: red `test: red - OpenAPI docs export, content and doc-path security scenarios`; green `feat: green - springdoc OpenAPI docs, doc-path security, 繁中 controller/DTO annotations`
  - Tests: `:app:test --tests *OpenApiSpecExportTest --tests *SwaggerDocsSecurityIntegrationTest` → BUILD SUCCESSFUL, 7 tests（含「取得 OpenAPI spec」「端點文件涵蓋成功與錯誤回應」「DTO schema 帶繁中欄位說明」「CI 上匯出 spec 檔」「瀏覽 Swagger UI」「文件路徑免認證可存取」「內部 API 仍需認證」）。`app/build/openapi/openapi.{yaml,json}` 已匯出。
  - Discovery: `:app` 需直接依賴 `spring-boot-starter-security`（security 型別僅透過 :campaign 的 implementation 傳遞，不在 :app compile classpath）。
- Next action: 進入 task 5.1（由 spec 產生 Postman collection）。
- Blockers: 無。
- Note: 4.3（完整 gate / JaCoCo）保留至所有程式碼落地後一次性 `./gradlew check` 驗證。

## Session 3 — 2026-06-12 11:55
- Stage: TDD
- Task: 5.1 / 6.1 / 6.2 / 6.3 / 4.3（Postman + Pages + 完整 gate）
- Transition: in_progress → passing
- Evidence:
  - Commits: `fix: export OpenAPI spec as UTF-8 and apply basicAuth globally`; `feat: add generated Postman collection for /internal API`; `feat: add GitHub Pages docs site (Swagger UI landing) and docs.yml deploy workflow`
  - Postman: `openapi-to-postmanv2` 產出 `docs/postman/campaign-reach.postman_collection.json`，含全部 6 個 `/internal` 端點，collection auth = basic（`{{basicAuthUsername}}`/`{{basicAuthPassword}}`）。
  - Pages: `docs/index.html` landing + `.github/workflows/docs.yml`（push main / workflow_dispatch；permissions pages:write+id-token:write；自包含 swagger-ui-dist 5.18.2，無 CDN；與 ci.yml 分離）。YAML 經 ruby 解析通過；swagger-ui-dist 檔案清單已驗證 cp 目標皆存在。
  - Gate: `./gradlew check` → BUILD SUCCESSFUL in 2m 9s（spotless / checkstyle / spotbugs / ArchUnit ModuleBoundaryTest / JaCoCo / 全部測試）。
- Next action: 進入 `spec-driven-dev:verification-before-completion`。
- Blockers: 無。
