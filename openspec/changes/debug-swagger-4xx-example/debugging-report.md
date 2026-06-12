# Debugging Report: debug-swagger-4xx-example

Date: 2026-06-12
Debugger: claude-opus-4-8 (system-debugging session)

> Debugging-only artifact. No approved spec change attached yet; created to record root-cause
> evidence for the "Swagger 4xx Example value 顯示成功情境 response" report.

## Symptom
- Reported behavior: 在 Swagger UI / OpenAPI 文件中，每個 4xx（400 / 401 / 404 / 409 / 422）回應的
  Example value 顯示的都是「成功情境」的 response body，而不是錯誤回應。
- Expected behavior: 4xx 回應的 schema / example 應為錯誤回應結構（`ApiError`：`{ error, reasons }`），
  與 2xx 的成功回傳型別區隔開。
- Impact: API 文件誤導使用者，讓 4xx 看起來會回傳成功物件；前端 / 整合方無從得知真正的錯誤格式。

## Reproduction
- Status: reproduced
- Steps:
  1. 取得 springdoc 產生的 OpenAPI 文件（`GET /v3/api-docs`），或直接讀已匯出的
     `app/build/openapi/openapi.json`（由 `OpenApiSpecExportTest` 產出）。
  2. 檢視任一端點的 `responses` 區塊，比對各狀態碼的 `content[*].schema.$ref`。
- Environment: feat/local-email-newman-e2e 分支；springdoc / Spring Boot 3；既有匯出 spec。
- Test data / record IDs: N/A（純文件產生問題，與資料無關）。

## Observation Plan
| Layer | Observation method | Evidence captured |
|---|---|---|
| API/backend | 解析 `app/build/openapi/openapi.json`，列出每端點各狀態碼的 response schema | 見下方 Evidence — 所有 4xx 的 `$ref` 等於 2xx 成功型別 |
| Source | `CampaignController` / `CampaignApiExceptionHandler` / `ApiError` annotation 與實作 | 4xx `@ApiResponse` 只有 `description`，未宣告 `content` |
| Database/persistence | N/A | — |
| Background/async | N/A | — |
| Environment/build | `OpenApiSpecExportTest` 匯出流程 / `docs.yml` | 文件由 springdoc 反射 controller 回傳型別產生 |

## Evidence

每端點各狀態碼的 response schema（由 `app/build/openapi/openapi.json` 解析）：

```text
### POST /internal/campaigns
  201 建立成功            | CreateCampaignResponse
  400 請求欄位驗證失敗     | CreateCampaignResponse   ← 應為 ApiError
  401 未通過認證          | CreateCampaignResponse   ← 不應有成功 body

### POST /internal/campaigns/{id}/status
  200 轉換成功            | CampaignView
  404 查無此活動          | CampaignView   ← 應為 ApiError
  409 樂觀鎖版本過期       | CampaignView   ← 應為 ApiError
  422 不合法的狀態轉換邊   | CampaignView   ← 應為 ApiError
  401 未通過認證          | CampaignView

### GET /internal/campaigns/{id}
  200 查詢成功            | CampaignView
  404 查無此活動          | CampaignView   ← 應為 ApiError
  401 未通過認證          | CampaignView

### PATCH /internal/campaigns/{id}
  200 修改成功            | CampaignView
  400 / 404 / 409        | CampaignView   ← 應為 ApiError
  401 未通過認證          | CampaignView
```

對應的原始碼（`CampaignController.java`）— 4xx 只宣告 `description`，未指定 `content`：

```java
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "建立成功，回傳新活動 id 與初始版本"),
    @ApiResponse(responseCode = "400", description = "請求欄位驗證失敗（如名稱空白、規則設定不合法）"),
    @ApiResponse(responseCode = "401", description = "未通過後台 OPERATOR 認證")
})
@PostMapping
public ResponseEntity<CreateCampaignResponse> create(...) { ... }
```

實際的錯誤回應型別（`CampaignApiExceptionHandler` 回傳 `ApiError`，文件中卻完全未引用）：

```java
public record ApiError(String error, List<String> reasons) {}
// handler: 400 validation_failed / 404 not_found / 409 conflict / 422 illegal_transition
```

## Data Flow Trace
- Symptom observed at: Swagger UI / `openapi.json` 各 4xx 的 Example value。
- First incorrect state found at: springdoc 產生 spec 時，對某狀態碼的 `@ApiResponse` **未指定 `content`**，
  便回退（fallback）套用 controller 方法的回傳型別 schema（成功型別）到該狀態碼。
- Boundary where expected became actual: `@ApiResponse(responseCode="4xx", description=...)` 缺少
  `content = @Content(schema = @Schema(implementation = ApiError.class))`，導致 springdoc 以方法回傳型別
  （`CreateCampaignResponse` / `CampaignView`）填入所有狀態碼。

## Working Reference
- Reference: springdoc 官方行為 —「未宣告 content 的 `@ApiResponse` 會繼承 operation 的預設（方法回傳型別）
  media type 與 schema」。
- Meaningful differences: 正確做法是在每個錯誤狀態碼上明確標註 `content`/`schema`，指向 `ApiError`
  （401/403 這類由 Spring Security 直接回應、無 body 的狀態碼則應標為空 `content` 或不附 schema）。

## Hypothesis
I think the root cause is **`@ApiResponse` 上的 4xx 只寫了 `description`、沒有指定 `content`/`schema`**，
所以 springdoc 對這些狀態碼自動沿用 controller 方法的成功回傳型別（`CreateCampaignResponse` /
`CampaignView`）作為 schema 與 example —— because `openapi.json` 中每個 4xx 的 `content["*/*"].schema.$ref`
都等於同端點 2xx 的成功型別，且 `ApiError`（實際錯誤 body）在整份 spec 中完全沒有被任何 4xx response 引用。

## Next Action
- Route to: `spec-driven-dev:test-driven-development`（實作 bug、契約已明確；以 `OpenApiSpecExportTest`
  新增「4xx schema 應為 ApiError、非成功型別」的 red test 驅動修正）。
- Minimal fix/test direction:
  1. 在 `CampaignController` 各 4xx `@ApiResponse` 加上
     `content = @Content(schema = @Schema(implementation = ApiError.class))`（401/403 標為無 body）。
  2. 在 `OpenApiSpecExportTest` 加斷言：`.../responses/400(404/409/422)/content/*/schema/$ref`
     指向 `ApiError`，且不等於成功型別。
  3. 重新匯出 `openapi.{json,yaml}` 驗證 Example value 改為錯誤結構。
  4. 注意模組邊界：`ApiError` 位於 `campaign.api`，reach 端點不可引用；reach 目前僅宣告 401（無 4xx body），
     可另以 reach 自有的錯誤型別處理或維持無 body。
```
