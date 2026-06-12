# API 文件（GitHub Pages）

本目錄是 campaign-reach-system **繁體中文 OpenAPI 文件**的來源。

## 內容

| 檔案 | 說明 |
| --- | --- |
| `index.html` | GitHub Pages 首頁（landing），連到 Swagger UI、OpenAPI 規格與 Postman collection。 |
| `postman/campaign-reach.postman_collection.json` | 由 OpenAPI 規格自動產生的 Postman collection；匯入後填入 `basicAuthUsername` / `basicAuthPassword` 即可操作 `/internal` API。 |

`openapi.yaml` / `openapi.json` 與 Swagger UI 靜態資產**不提交到 repo**，由 `.github/workflows/docs.yml` 在
部署時產生（spec 來自 `OpenApiSpecExportTest`，Swagger UI 來自版本鎖定的 `swagger-ui-dist`，不依賴 CDN）。

## 一次性手動設定（無法以程式碼完成）

GitHub Pages 的部署來源必須手動設定一次：

> Repo **Settings → Pages → Build and deployment → Source** 設為 **「GitHub Actions」**。

設定後，每次 push 到 `main`（或手動觸發 `workflow_dispatch`）即會重新部署文件。此 workflow 與品質 gate
（`ci.yml`）**分離**，部署成功與否不影響 PR 合併。

## 本機重新產生 Postman collection

```bash
./gradlew :app:test --tests "*OpenApiSpecExportTest"   # 產生 app/build/openapi/openapi.yaml
npx -y openapi-to-postmanv2 \
  -s app/build/openapi/openapi.yaml \
  -o docs/postman/campaign-reach.postman_collection.json -p
```
