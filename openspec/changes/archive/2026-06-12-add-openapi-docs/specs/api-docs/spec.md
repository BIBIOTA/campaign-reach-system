## ADDED Requirements

### Requirement: 執行中 app 提供繁體中文 OpenAPI 文件與 Swagger UI
The system SHALL 透過 springdoc-openapi 從現有 controllers 與 DTO 產生 OpenAPI spec，SHALL 以全域 `OpenAPI` bean 提供繁體中文的 title 與 description，並 SHALL 在執行中的 app 暴露 `/v3/api-docs` 與 `/swagger-ui`。

#### Scenario: 取得 OpenAPI spec
- **WHEN** 對執行中的 app 請求 `GET /v3/api-docs`
- **THEN** the system 回傳 200 與含 `/internal/campaigns`、`/internal/reach/campaigns` 等路徑的 OpenAPI 文件
- **AND** 文件 info 區段的 title 與 description 為繁體中文
- **AND** 宣告一個 HTTP Basic 的 security scheme 供 Swagger UI「Try it out」帶入帳密

#### Scenario: 瀏覽 Swagger UI
- **WHEN** 以瀏覽器開啟 `/swagger-ui/index.html`
- **THEN** the system 載入 Swagger UI 並渲染所有已標註端點
- **AND** 端點 summary/description 與 DTO 欄位說明以繁體中文呈現

### Requirement: 文件路徑放行且不削弱既有端點授權
The system SHALL 以獨立的 `@Order(1)` `SecurityFilterChain` 放行文件路徑（`/swagger-ui/**`、`/swagger-ui.html`、`/v3/api-docs/**`），且該鏈的 securityMatcher SHALL 不與 `/internal/**` 重疊，使 `/internal/**` 仍由既有 `CampaignSecurityConfig` 以 OPERATOR HTTP Basic 保護（不修改該設定）。

#### Scenario: 文件路徑免認證可存取
- **WHEN** 未帶認證請求 `/swagger-ui/index.html` 或 `/v3/api-docs`
- **THEN** the system 不回 401（文件可公開存取）

#### Scenario: 內部 API 仍需認證
- **WHEN** 未帶認證請求任一 `/internal/**` 端點
- **THEN** the system 回 401
- **AND** 既有 `CampaignSecurityConfig` 的 OPERATOR 授權行為不受文件能力影響

### Requirement: controllers 與 DTO 帶繁體中文 API 註解涵蓋錯誤合約
The system SHALL 為 `CampaignController` 的建立/查詢/修改/狀態轉換端點與 `ReachMetricsController` 的兩個查詢端點加上繁體中文 `@Operation` 與 `@Tag`，SHALL 為相關 request/response/view DTO 欄位加上 `@Schema` 繁中說明，並 SHALL 以 `@ApiResponse` 標註 `CampaignApiExceptionHandler` 產生的錯誤回應（400 / 404 / 409 / 422）。

#### Scenario: 端點文件涵蓋成功與錯誤回應
- **WHEN** 從產生的 spec 檢視 `POST /internal/campaigns` 與 `POST /internal/campaigns/{id}/status`
- **THEN** 每個端點有繁中 summary/description
- **AND** 文件涵蓋成功回應（201 / 200）與對應的錯誤回應（404 / 409 / 422）

#### Scenario: DTO schema 帶繁中欄位說明
- **WHEN** 從產生的 spec 檢視 components/schemas
- **THEN** `CreateCampaignRequest`、`UpdateCampaignRequest`、`ChangeCampaignStatusRequest` 及相關 response/view 的欄位帶繁體中文 description

### Requirement: build 時匯出靜態 OpenAPI spec
The system SHALL 以 `@RequiresDocker` 整合測試（重用既有 Testcontainers PostgreSQL）啟動完整 Spring context、取得 OpenAPI 文件，並 SHALL 將其寫出為 `app/build/openapi/openapi.yaml` 與 `openapi.json`；本機無 Docker 時 SHALL 自動跳過，與既有 gate 行為一致。

#### Scenario: CI 上匯出 spec 檔
- **WHEN** 在具備 Docker 的環境執行 `OpenApiSpecExportTest`
- **THEN** the system 以完整 context 取得 `GET /v3/api-docs` 回 200
- **AND** 寫出 `app/build/openapi/openapi.yaml` 與 `openapi.json`
- **AND** 內容含預期端點路徑與繁體中文描述字串

#### Scenario: 本機無 Docker 自動跳過
- **WHEN** 在無 Docker 的本機執行測試
- **THEN** 該匯出測試被跳過
- **AND** 其他既有本機檢查不受影響

### Requirement: 由 spec 產生 Postman collection 並提交 repo
The system SHALL 以 `openapi-to-postmanv2` 從匯出的 `openapi.yaml` 產生 Postman collection，SHALL 提交一份到 `docs/postman/campaign-reach.postman_collection.json`，且該 collection SHALL 涵蓋所有 `/internal/**` 端點並提供 HTTP Basic 認證變數供使用者填入帳密。

#### Scenario: 產生可匯入的 Postman collection
- **WHEN** 以 `openapi-to-postmanv2` 轉換 `openapi.yaml`
- **THEN** 產出 `docs/postman/campaign-reach.postman_collection.json`
- **AND** collection 含 campaign 與 reach 的所有 `/internal/**` 端點
- **AND** collection 設定 HTTP Basic 認證變數方便使用者操作 API

### Requirement: 透過 GitHub Actions 部署文件到 GitHub Pages
The system SHALL 新增與既有 `ci.yml` 分離的 `docs.yml` workflow，在 push `main` 或手動 `workflow_dispatch` 時匯出 spec、產生 Postman、以自包含（不依賴 CDN、版本鎖定）的 `swagger-ui-dist` 組成靜態站，並 SHALL 以 `pages: write` 與 `id-token: write` 權限部署到 GitHub Pages；文件 SHALL 註明需手動將 repo Pages source 設為「GitHub Actions」。

#### Scenario: push main 觸發文件部署
- **WHEN** 變更被 push 到 `main`
- **THEN** `docs.yml` 匯出 spec、產生 Postman、組裝 Swagger UI 靜態站並部署到 GitHub Pages
- **AND** workflow 宣告 `permissions: pages: write` 與 `id-token: write`
- **AND** 既有 `ci.yml` 品質 gate 不受影響

#### Scenario: Swagger UI 靜態站不依賴外部 CDN
- **WHEN** 組裝 GitHub Pages 靜態站
- **THEN** 使用自包含的 `swagger-ui-dist`（版本鎖定）指向 `./openapi.yaml`
- **AND** landing `docs/index.html` 連到 Swagger UI 與 Postman 下載

#### Scenario: 文件記載手動前置設定
- **WHEN** 使用者閱讀變更文件
- **THEN** 文件註明需於 repo Settings → Pages → Source 設為「GitHub Actions」（此步驟無法以程式碼完成）

### Requirement: 新增程式碼通過既有品質 gate
The system SHALL 確保所有新增的 Java 檔通過既有 `./gradlew check` gate（spotless / checkstyle / spotbugs / ArchUnit / JaCoCo），且新增的 swagger-annotations 依賴 SHALL 不造成 `campaign` 與 `reach` 模組互相依賴。

#### Scenario: gate 全數通過
- **WHEN** 執行 `./gradlew check`
- **THEN** spotless、checkstyle、spotbugs、ArchUnit、JaCoCo 全部通過
- **AND** `ModuleBoundaryTest` 驗證 campaign 與 reach 仍未互相依賴
