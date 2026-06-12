# API 文件（GitHub Pages）

本目錄是 campaign-reach-system **繁體中文 OpenAPI 文件**的來源。

## 內容

| 檔案 | 說明 |
| --- | --- |
| `index.html` | GitHub Pages 首頁（landing），連到 Swagger UI、OpenAPI 規格與 Postman collection。 |
| `postman/campaign-reach.postman_collection.json` | 由 OpenAPI 規格自動產生的 Postman collection；已預填可直接打通的預設值（`baseUrl=http://localhost:8080`、`basicAuthUsername=operator`、`basicAuthPassword=operator-pass`，對齊 `.env.example`）並帶有合法的範例請求 body／路徑參數，匯入後即可直接對本機 app 送出請求；連到其他環境時於 Postman 覆寫這些變數即可。 |

`openapi.yaml` / `openapi.json` 與 Swagger UI 靜態資產**不提交到 repo**，由 `.github/workflows/docs.yml` 在
部署時產生（spec 來自 `OpenApiSpecExportTest`，Swagger UI 來自版本鎖定的 `swagger-ui-dist`，不依賴 CDN）。

## 一次性前置設定（已完成）

GitHub Pages 的部署來源需設為 **GitHub Actions**。本 repo 已設定完成，可用 UI 或 API：

- UI：Repo **Settings → Pages → Build and deployment → Source** 設為 **「GitHub Actions」**。
- API（等效）：`gh api -X POST repos/<owner>/<repo>/pages -f build_type=workflow`。

> 注意：private repo 的 Pages 需付費方案（Pro/Team/Enterprise）；public repo 則免費。本 repo 為 public。

設定後，每次 push 到 `main`（或在 `main` 上手動觸發 `workflow_dispatch`）即會重新部署文件。此 workflow 與品質
gate（`ci.yml`）**分離**，部署成功與否不影響 PR 合併。網站網址：`https://<owner>.github.io/<repo>/`。

## 本機重新產生 Postman collection

```bash
./gradlew :app:test --tests "*OpenApiSpecExportTest"   # 產生 app/build/openapi/openapi.yaml
# parametersResolution=Example 讓轉換器輸出 spec 內的範例值（請求 body／路徑參數），而非 <string>/<uuid> 佔位。
npx -y openapi-to-postmanv2 \
  -s app/build/openapi/openapi.yaml \
  -o docs/postman/campaign-reach.postman_collection.json -p \
  -O parametersResolution=Example
# 注入可直接打通的 collection 變數（baseUrl + Basic Auth 預設值）。
node docs/scripts/finalize-postman-collection.mjs docs/postman/campaign-reach.postman_collection.json
```
