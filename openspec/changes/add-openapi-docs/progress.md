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
