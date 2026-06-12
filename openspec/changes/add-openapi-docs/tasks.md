# Tasks: add-openapi-docs

## 1. 依賴與版本目錄
- [ ] 1.1 在 `gradle/libs.versions.toml` 加入 springdoc starter 與 swagger-annotations
  - Acceptance: WHEN 版本目錄被讀取 THEN 含 `springdoc-openapi-starter-webmvc-ui`（pin 版本）與 `swagger-annotations`（jakarta）library 別名 AND 版本字串集中在 `[versions]`
  - Depends on: -
  - Independence: independent
  - status: not_started
- [ ] 1.2 在 `:app` 加入 springdoc starter 依賴；在 `:campaign`、`:reach` 加入 swagger-annotations（`implementation`）
  - Acceptance: WHEN `./gradlew :app:dependencies` 執行 THEN springdoc starter 在 `:app` runtime classpath AND swagger-annotations 在 campaign/reach compile classpath AND `ModuleBoundaryTest` 仍通過（無 campaign↔reach 互依）
  - Depends on: 1.1
  - Independence: serial
  - status: not_started

## 2. OpenAPI 設定與安全
- [ ] 2.1 新增 `OpenApiConfig`（`:app`）提供全域 `OpenAPI` bean
  - Acceptance: WHEN app 啟動 THEN `/v3/api-docs` 含繁體中文 title 與 description AND 宣告名為 `basicAuth` 的 HTTP Basic security scheme AND 設定 server 資訊
  - Depends on: 1.2
  - Independence: serial
  - status: not_started
- [ ] 2.2 新增 `SwaggerDocsSecurityConfig`（`:app`）放行文件路徑
  - Acceptance: WHEN 收到 `/swagger-ui/**`、`/swagger-ui.html`、`/v3/api-docs/**` 請求 THEN 由 `@Order(1)` 的 `SecurityFilterChain` permitAll AND `/internal/**` 仍由 `CampaignSecurityConfig` 守住 OPERATOR（不修改該檔）AND 兩條鏈 securityMatcher 無重疊
  - Depends on: 1.2
  - Independence: serial
  - status: not_started

## 3. 繁體中文 API 註解
- [ ] 3.1 為 `CampaignController` 四個端點加 `@Tag` / `@Operation` / `@ApiResponse`
  - Acceptance: WHEN 產生 spec THEN 每個端點有繁中 summary/description AND 涵蓋成功與錯誤回應（201/200/404/409/422）對應 `CampaignApiExceptionHandler` 的合約
  - Depends on: 1.2
  - Independence: parallel-safe
  - status: not_started
- [ ] 3.2 為 `ReachMetricsController` 兩個 GET 端點加 `@Tag` / `@Operation` / `@ApiResponse`
  - Acceptance: WHEN 產生 spec THEN 兩端點有繁中 summary/description AND 標註 404 等錯誤回應
  - Depends on: 1.2
  - Independence: parallel-safe
  - status: not_started
- [ ] 3.3 為 campaign 與 reach 的 request/response/view DTO 欄位加 `@Schema(description=...)` 繁中說明
  - Acceptance: WHEN 產生 spec THEN `CreateCampaignRequest`、`UpdateCampaignRequest`、`ChangeCampaignStatusRequest` 及相關 response/view 的欄位在 schema 區段帶繁中描述
  - Depends on: 1.2
  - Independence: parallel-safe
  - status: not_started

## 4. Spec 匯出與測試
- [ ] 4.1 新增 `OpenApiSpecExportTest`（`:app/src/test`，`@RequiresDocker`）
  - Acceptance: WHEN 測試以完整 Spring context（Testcontainers Postgres）執行 THEN `GET /v3/api-docs` 回 200 AND 內容含 `/internal/campaigns` 等路徑與繁中描述字串 AND 寫出 `app/build/openapi/openapi.yaml` 與 `openapi.json` AND 本機無 Docker 時自動跳過
  - Depends on: 2.1, 3.1, 3.2, 3.3
  - Independence: serial
  - status: not_started
- [ ] 4.2 新增文件路徑安全測試
  - Acceptance: WHEN 無認證請求 `/swagger-ui/index.html` 與 `/v3/api-docs` THEN 回非 401（可存取）AND 無認證請求 `/internal/**` 仍回 401
  - Depends on: 2.2
  - Independence: serial
  - status: not_started
- [ ] 4.3 確保新 Java 檔通過既有 gate 並處理 JaCoCo 覆蓋
  - Acceptance: WHEN `./gradlew check` 執行 THEN spotless / checkstyle / spotbugs / ArchUnit / JaCoCo 全通過 AND `OpenApiConfig`、`SwaggerDocsSecurityConfig` 不致使覆蓋率門檻失敗（必要時納入既有排除設定，並於 PR 說明理由）
  - Depends on: 2.1, 2.2, 4.1, 4.2
  - Independence: serial
  - status: not_started

## 5. Postman collection
- [ ] 5.1 由 OpenAPI spec 產生 Postman collection 並提交一份到 repo
  - Acceptance: WHEN 以 `openapi-to-postmanv2` 從 `openapi.yaml` 轉換 THEN 產出 `docs/postman/campaign-reach.postman_collection.json` AND 含所有 `/internal/**` 端點 AND collection 設定 HTTP Basic 認證變數方便使用者填入帳密
  - Depends on: 4.1
  - Independence: serial
  - status: not_started

## 6. GitHub Pages 部署
- [ ] 6.1 新增 Pages landing 與 Swagger UI 靜態資產組裝
  - Acceptance: WHEN 組裝靜態站 THEN 使用自包含 `swagger-ui-dist`（版本鎖定，不依賴 CDN）指向 `./openapi.yaml` AND `docs/index.html` landing 連到 Swagger UI 與 Postman 下載
  - Depends on: 4.1, 5.1
  - Independence: serial
  - status: not_started
- [ ] 6.2 新增 `.github/workflows/docs.yml` 部署到 GitHub Pages
  - Acceptance: WHEN push 到 `main` 或手動 `workflow_dispatch` THEN workflow 跑 `OpenApiSpecExportTest` 產 spec → 產 Postman → 組 Swagger UI 站 → `upload-pages-artifact` + `deploy-pages` AND 宣告 `permissions: pages: write, id-token: write` AND 與既有 `ci.yml` 分離（不影響品質 gate）
  - Depends on: 6.1
  - Independence: serial
  - status: not_started
- [ ] 6.3 記錄 Pages 手動前置設定
  - Acceptance: WHEN 文件被閱讀 THEN 註明需於 repo Settings → Pages → Source 設為「GitHub Actions」（此步驟無法用程式碼完成）
  - Depends on: 6.2
  - Independence: serial
  - status: not_started

## Optional artifacts
- [ ] PlantUML diagrams (spec-driven-dev:writing-uml)
- [ ] Figma designs (spec-driven-dev:writing-figma)
