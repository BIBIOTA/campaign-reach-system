---
change_id: add-openapi-docs
doc_language: 繁體中文
---

# Design — add-openapi-docs

## 目標

為 campaign-reach-system 建立 OpenAPI (Swagger) API 文件，達成三個交付物：

1. **Swagger 文件** — 用 springdoc-openapi 從現有 controllers/DTOs 產生 OpenAPI spec，**API 說明文字以繁體中文撰寫**；執行中的 app 提供 `/swagger-ui` 與 `/v3/api-docs`。
2. **GitHub Pages 部署** — 新增 GitHub Actions workflow，在 build 時匯出靜態 OpenAPI spec，組成自包含的 Swagger UI 靜態站並部署到 GitHub Pages。
3. **Postman collection** — 從同一份 OpenAPI spec 自動產生 Postman collection，方便使用者匯入操作 API。

所有交付物圍繞**單一來源** `openapi.yaml`，避免文件與實作漂移。

## 範圍與現況

- REST 端點（皆在 `/internal/**` 之下，由 HTTP Basic + `OPERATOR` 角色保護）：
  - `CampaignController` — `/internal/campaigns`：`POST`（建立）、`GET /{id}`、`PATCH /{id}`、`POST /{id}/status`。
  - `ReachMetricsController` — `/internal/reach/campaigns`：`GET /{campaignId}/metrics`、`GET /{campaignId}/recipients/{userId}`。
  - `CampaignApiExceptionHandler` — 產生 400 / 404 / 409 / 422 錯誤回應。
- 目前**沒有**任何 springdoc / swagger 依賴。Spring Boot 3 / Java 21。
- 安全：`CampaignSecurityConfig` 是唯一的 `SecurityFilterChain` bean，使用 `securityMatcher("/internal/**")` + `anyRequest().hasRole("OPERATOR")`。
- CI：既有 `.github/workflows/ci.yml`（`./gradlew check` 品質 gate）。

## 關鍵取捨：OpenAPI spec 如何在 build 時匯出

**採用方案 — 用整合測試匯出（重用既有 Testcontainers 基礎建設）。**
啟動 app 需要 PostgreSQL（Flyway 在啟動時跑 migration），而專案已有成熟的 Testcontainers 慣例。因此用一個 `@RequiresDocker` 整合測試啟動完整 Spring context、呼叫 `GET /v3/api-docs.yaml`，把結果寫到 `app/build/openapi/openapi.yaml`。CI 上 Docker 一定存在；本機無 Docker 則自動跳過，與既有 gate 行為一致。

**未採用 — `springdoc-openapi-gradle-plugin`。** 會 fork `bootRun`，但同樣需要可用 datasource，要額外接 Postgres，設定較繁瑣，故不採用。

## 架構總覽

三個交付物圍繞單一來源 `openapi.yaml`：

```
                    ┌─────────────────────────────────────────┐
                    │  controllers + DTOs（campaign / reach）   │
                    │  加上繁體中文 @Operation / @Schema 註解    │
                    └────────────────────┬────────────────────┘
                                         │ springdoc 反射讀取
              ┌──────────────────────────┴──────────────────────────┐
              │                                                      │
   runtime（app 執行中）                          build 時匯出（整合測試）
   /swagger-ui  /v3/api-docs                      app/build/openapi/openapi.yaml
                                                          │
                                              ┌───────────┴───────────┐
                                       Swagger UI 靜態站         openapi-to-postmanv2
                                       (swagger-ui-dist)         → postman_collection.json
                                              │                         │
                                              └─────────┬───────────────┘
                                              GitHub Pages（docs.yml workflow）
```

## 元件與檔案

| 元件 | 位置 | 職責 |
| --- | --- | --- |
| `springdoc-openapi-starter-webmvc-ui` | `:app` 依賴（`implementation`） | runtime 提供 `/swagger-ui`、`/v3/api-docs` |
| `swagger-annotations`（jakarta） | `:campaign`、`:reach`（`implementation`） | 讓 controllers/DTOs 可標註繁中說明（runtime retention，springdoc 反射讀取） |
| `OpenApiConfig` | `:app` | 全域 `OpenAPI` bean：title、繁中描述、server、HTTP Basic security scheme |
| `SwaggerDocsSecurityConfig` | `:app` | `@Order(1)` 的 `SecurityFilterChain`，`securityMatcher` 放行 `/swagger-ui/**`、`/v3/api-docs/**`（permitAll）。`/internal/**` 仍由 `CampaignSecurityConfig` 守住 OPERATOR |
| `OpenApiSpecExportTest` | `:app/src/test`（`@RequiresDocker`） | 啟動 context → 寫出 `openapi.yaml` + `.json`，並斷言含預期路徑與繁中描述 |
| `.github/workflows/docs.yml` | 新 workflow | push main 時：跑匯出測試 → 組 Swagger UI 站 + Postman → 部署 Pages |
| `docs/index.html` | repo（landing） | Pages 首頁，連到 Swagger UI 與 Postman 下載 |
| `docs/postman/campaign-reach.postman_collection.json` | repo | 從 spec 產生的 Postman collection，提交一份到 repo（同時也發佈到 Pages） |

**繁中註解擺放原則**：
- `@Tag` / `@Operation`（summary、description）放 controller 方法上。
- `@Schema(description=...)` 放 DTO（`CreateCampaignRequest`、`UpdateCampaignRequest`、`ChangeCampaignStatusRequest`、各 response/view）欄位上。
- 錯誤回應（400 / 404 / 409 / 422，由 `CampaignApiExceptionHandler` 產生）用 `@ApiResponse` 標註，讓文件涵蓋錯誤合約。

## 資料流

1. 開發者在 controller / DTO 加繁中 swagger 註解。
2. **本機**：`./gradlew :app:bootRun` → 瀏覽 `/swagger-ui` 即時檢視。
3. **CI（docs.yml）**：`./gradlew :app:test --tests *OpenApiSpecExportTest`（Docker 起 Postgres）→ 產出 `openapi.yaml` → `npx openapi-to-postmanv2` 產 Postman → 用 `swagger-ui-dist` 組自包含靜態站 → `upload-pages-artifact` + `deploy-pages`。
4. 使用者：上 GitHub Pages 看 Swagger UI、下載 Postman collection 匯入操作 API。

## 安全與錯誤處理

- 新增 `@Order(1)` 的 swagger 鏈，`securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")`，`permitAll`。路徑與 `/internal/**` 無重疊，避開 `ReachMetricsController` javadoc 警告的「模糊匹配」問題。
- `/internal/**` API 端點本身**維持** OPERATOR HTTP Basic；在 `OpenApiConfig` 宣告 `http basic` security scheme，讓 Swagger UI「Try it out」會跳出帳密輸入。
- **不觸碰** `CampaignSecurityConfig`（campaign 模組安全邏輯不動）。
- Pages 是公開站：文件只描述 schema 與端點，**不含**任何密鑰或營運帳號。

## 測試策略

- `OpenApiSpecExportTest`：斷言 `/v3/api-docs` 回 200、含 `/internal/campaigns` 等路徑與繁中描述字串；寫出 `openapi.yaml` + `.json` artifact。
- 安全測試（沿用 `ReachMetricsApiSecurityIntegrationTest` 模式）：`/swagger-ui`、`/v3/api-docs` 免驗證可存取；`/internal/**` 無帳密仍 401。
- 既有 gate（spotless / checkstyle / spotbugs / ArchUnit / JaCoCo）全數套用到新 Java 檔。
- **風險點**：JaCoCo 覆蓋率門檻 —— `OpenApiConfig`、`SwaggerDocsSecurityConfig` 等 config 類可能需要納入排除或由整合測試帶到覆蓋；實作時確認，避免 gate 失敗。

## 模組邊界考量

- `swagger-annotations` 是第三方函式庫，campaign / reach 兩模組各自依賴它，**不會**造成 `campaign ↔ reach` 互相 import，`ModuleBoundaryTest` 不受影響。
- `OpenApiConfig` 與 `SwaggerDocsSecurityConfig` 放在 `:app`（已依賴 campaign / reach / shared），符合既有「`:app` 為單一可部署單元」的設計。
- springdoc starter 僅在 `:app`，web / security starter 透過 `:campaign` 的 `implementation` 依賴在 `:app` runtime classpath 上可用。

## CI / Pages 設定

- 新 `docs.yml`，與既有 `ci.yml` **分離**；`permissions: pages: write, id-token: write`；trigger = push `main` + `workflow_dispatch`。
- **需手動前置（無法用程式碼完成）**：repo Settings → Pages → Source 設為「GitHub Actions」。文件會註明此步驟。
- Postman collection 由 spec 自動產生避免手寫漂移；同時提交一份到 `docs/postman/` 並發佈到 Pages 供下載。
- Swagger UI 採自包含 `swagger-ui-dist`（版本鎖定），不依賴 CDN。

## 已決議的開放問題

- **Postman 檔提交位置**：同時提交一份到 repo（`docs/postman/`）並發佈到 Pages。
- **Swagger UI 來源**：自包含 `swagger-ui-dist`（版本鎖定），不依賴 CDN。

## Probable next steps

- **UML（`spec-driven-dev:writing-uml`）**：不需要 —— 本變更無複雜元件互動、狀態機或資料流，屬於文件工具與 CI 接線。
- **Figma（`spec-driven-dev:writing-figma`）**：不需要 —— backend-only，無前端 UI 變更（Swagger UI 為現成靜態資產，非自製介面）。
