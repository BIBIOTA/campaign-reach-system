## Why

campaign-reach-system 目前有兩組內部 REST（`/internal/campaigns`、`/internal/reach/campaigns`），但**沒有任何 API 文件**：使用者必須讀原始碼才能知道端點、請求/回應 schema 與錯誤合約。團隊也缺少可直接匯入操作 API 的工具檔。

本變更導入 springdoc-openapi，從現有 controllers/DTOs 產生 OpenAPI spec（**說明文字以繁體中文撰寫**），讓執行中的 app 直接提供 Swagger UI，並把同一份 spec 在 CI build 時匯出為靜態檔，部署到 GitHub Pages，同時自動產生 Postman collection。所有交付物圍繞**單一來源** `openapi.yaml`，避免文件與實作漂移。

## What Changes

- **api-docs**：新增繁體中文 OpenAPI 文件能力——
  - runtime 提供 `/swagger-ui` 與 `/v3/api-docs`，並以獨立的 `@Order(1)` security filter chain 放行文件路徑（`/internal/**` 仍維持 OPERATOR HTTP Basic）。
  - 為 `CampaignController`、`ReachMetricsController` 及其 DTO 加上繁中 `@Operation` / `@Schema` / `@ApiResponse` 註解，涵蓋成功與錯誤合約。
  - 以 `@RequiresDocker` 整合測試在 build 時匯出 `openapi.yaml` / `openapi.json`（重用既有 Testcontainers 基礎建設）。
  - 由 spec 自動產生 Postman collection 並提交一份到 `docs/postman/`。
  - 新增與既有品質 gate 分離的 `docs.yml` workflow，把自包含的 Swagger UI 靜態站 + Postman 部署到 GitHub Pages。

## Impact

- Affected specs: `specs/api-docs/`
- Affected code:
  - `gradle/libs.versions.toml`、`:app`/`:campaign`/`:reach` build scripts（新增 springdoc / swagger-annotations 依賴）
  - `:app` 新增 `OpenApiConfig`、`SwaggerDocsSecurityConfig`、`OpenApiSpecExportTest` 與文件路徑安全測試
  - `campaign` / `reach` controllers 與 DTO 加註解（**不修改** `CampaignSecurityConfig` 安全邏輯）
  - 新增 `docs/`（landing + Postman）、`.github/workflows/docs.yml`
- Breaking changes: No——僅新增文件能力，不改變既有 API 行為或 `/internal/**` 的認證授權。

## Related Artifacts

### Design
- [design.md](./design.md)
- [tasks.md](./tasks.md)

### Diagrams
- （無）

### Figma Designs
- （無）
