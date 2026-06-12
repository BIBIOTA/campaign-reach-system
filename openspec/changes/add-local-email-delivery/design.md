---
change_id: add-local-email-delivery
doc_language: 繁體中文
---

# 本機 Email 寄送設計

## 背景

目前 `reach` 模組已經有 `EmailAdapter` 與 `EmailProviderClient` seam，但尚未提供 concrete provider binding。也就是說，觸達流程可以建立 EMAIL `reach_task`，但在沒有 provider bean 的本機環境中，dispatcher 不會 claim EMAIL 任務進行實際寄送。

這個 change 的目標是在本機開發環境提供一條可實際驗證的 Email 發送路徑：app 透過 SMTP 把信送到本機捕捉信箱，開發者可在瀏覽器中查看信件內容，同時保持 production 不會誤用本機 provider。

## 目標

- 在本機環境能以 SMTP 發送 EMAIL reach task。
- 使用 Mailpit 作為本機 SMTP server 與 Web UI，避免任何真實外寄風險。
- 提供簡易示意信件樣板，不引入完整模板系統。
- 固定寄到本機測試收件信箱，降低設定成本。
- 維持既有 PII 最小化：事件、`reach_task`、`ReachMessage` 仍不保存 email address。
- 更新本機開發文件，讓開發者可以跑一次 smoke test 並在 Mailpit 看到信。

## 非目標

- 不接 SendGrid、SES、Gmail SMTP 或任何真實外部寄信服務。
- 不新增會員或聯絡資料表。
- 不把 email address 寫入 `reach_task`、Kafka event、metrics API 或 audit trail。
- 不實作可編輯的正式行銷模板系統。
- 不新增後台 UI。

## 已選方案

採用 Mailpit + Spring Mail SMTP provider。

`docker-compose.yml` 增加 Mailpit 服務：

- SMTP port：`1025`，供 app 寄信。
- Web UI port：`8025`，供開發者查看信件。

`reach/channel` 新增本機 `EmailProviderClient` binding。它只在明確本機設定下註冊，並交由既有 `EmailAdapter` 包上 circuit breaker 與 dispatcher retry flow。這延續目前架構：dispatcher 只認得 `ChannelAdapter`，`EmailAdapter` 只認得 `EmailProviderClient`，本機 SMTP 細節不外洩到 orchestrator、dispatcher 或 campaign 模組。

## 架構

新增元件：

- `LocalSmtpEmailProviderClient`
  - 實作 `EmailProviderClient`。
  - 使用 Spring Mail 的 `JavaMailSender` 或等價 SMTP client 將信送到 Mailpit。
  - 收件人固定讀取本機設定，例如 `campaignreach.email-provider.local.recipient`。
  - 回傳 `SendResult`，provider message id 使用 SMTP message id 或本機產生的 id。

- `LocalEmailTemplateRenderer`
  - 接收 `ReachMessage`。
  - 產生簡易 subject/body。
  - 不查資料庫、不解析會員資料、不接外部服務。

- `LocalSmtpEmailProperties`
  - 綁定本機 SMTP 設定。
  - 驗證 host、port、from、recipient。

- `LocalSmtpEmailConfig`
  - 只在 `local` profile 且 `campaignreach.email-provider.mode=smtp-local` 時註冊。
  - 提供 `JavaMailSender` 與 `EmailProviderClient` bean。

設定邊界：

- `app/src/main/resources/application.yml` 不放真實秘密，也不提供會讓 production 誤用的本機寄信預設。
- `.env.example` 增加本機 SMTP 變數，供 `local` profile 使用。
- production profile 若沒有 concrete provider binding，仍維持目前行為：不註冊 `EmailAdapter`，EMAIL 任務不會被 dispatch。

## 資料流

1. 開發者執行 `docker compose up -d`，啟動 PostgreSQL、Kafka、Mailpit。
2. 開發者載入 `.env`，以 `local` profile 啟動 app，並設定 `campaignreach.email-provider.mode=smtp-local`。
3. campaign API 或排程 / 行為事件發出 `ReachRequested`。
4. reach orchestrator 消費 `reach.requested`，解析 audience 並建立 EMAIL `reach_task`。
5. dispatcher claim EMAIL task，建立 `ReachMessage(userId, EMAIL, templateRef)`。
6. `EmailAdapter` 呼叫 `LocalSmtpEmailProviderClient.deliver(message)`。
7. provider 使用 `LocalEmailTemplateRenderer` 產生信件內容，固定送到本機測試 recipient。
8. Mailpit 接收信件，開發者在 `http://localhost:8025` 查看。
9. SMTP 送出成功後，dispatcher 將 `reach_task` 標記為 `SENT`，並照既有流程記錄 send result。

## 信件樣板

第一版只提供內建示意樣板。未知 `templateRef` 不視為錯誤，而是 render 成通用本機測試信。

Subject 範例：

```text
[Local Campaign Reach] summer-sale-email
```

Body 內容至少包含：

- 本機測試信提示。
- `templateRef`。
- `userId`。
- `channel`。
- 發送時間。

這個樣板只服務本機 smoke test，不代表正式行銷模板格式。

## 錯誤處理

- SMTP host 缺漏、port 非合法值、from/recipient 格式錯誤：設定綁定階段 fail fast。
- Mailpit 未啟動、SMTP 連線 timeout、暫時性 transport error：視為 retryable provider failure，交由既有 `EmailAdapter` / dispatcher retry / circuit breaker 流程處理。
- `templateRef` 未登錄：不失敗，使用通用本機樣板。
- production 未啟用 local mode：不註冊本機 provider，避免固定收件信箱誤用。

## 測試策略

單元測試：

- `LocalSmtpEmailProperties` 驗證缺漏或非法設定會失敗。
- `LocalEmailTemplateRenderer` 驗證 subject/body 包含 `templateRef`、`userId`、`channel` 與本機測試提示。
- `LocalSmtpEmailProviderClient` 使用 mock/stub mail sender 驗證固定 recipient、from、subject/body 與 `SendResult`。

Spring context 測試：

- 沒有 `local` profile 時，不註冊本機 `EmailProviderClient`。
- `local` profile 但 mode 不是 `smtp-local` 時，不註冊本機 `EmailProviderClient`。
- `local + smtp-local` 且設定完整時，註冊 `EmailProviderClient`，並讓既有 `EmailAdapter` 啟用。
- 設定缺漏時 context 啟動失敗。

文件 / smoke test：

- README 增加 Mailpit 啟動與查看信件流程。
- `.env.example` 增加本機 SMTP 設定。
- smoke test 驗收方式：觸發一筆 EMAIL reach，Mailpit UI 看得到信，DB 中對應 task 進入 `SENT`。

第一版不把 Mailpit UI 驗證納入 CI gate。若後續需要自動化端到端驗證，可用 Mailpit HTTP API 補一個 Docker-gated integration test。

## 模組邊界

- 主要變更在 `reach` 模組的 `reach/channel` package。
- `app` 只負責 runtime wiring、設定與 compose/docs 支援。
- `campaign` 不需要直接依賴任何 reach email provider。
- `shared` 不新增 email provider implementation，也不存放 reach-specific 設定類別。
- ArchUnit 邊界規則不需要放寬。

## 可觀測性與操作

- 本機 provider 成功送出時可用既有 send result / task status 查詢驗證。
- Mailpit Web UI 是開發者主要查看信件內容的工具。
- provider 不應在 log 中輸出完整信件內容；本機固定 recipient 不是 production PII，但仍維持保守 log，必要時只記錄 provider message id 與 task/user 識別資訊。

## 取捨

選擇 Mailpit 的好處是能測到真 SMTP 行為，也能用 Web UI 直接查看 MIME 內容。代價是本機 compose 多一個服務，以及 app 需要新增 Spring Mail 依賴與 local profile 設定。

選擇固定測試 recipient 的好處是簡單、穩定、不需要新增會員聯絡資料來源。代價是不能在本機直接驗證不同 userId 對應不同 email；這是刻意取捨，因為目前目標是驗證本機寄送能力，而不是會員資料整合。

不引入正式模板引擎能讓第一版範圍保持小。未來若要支援正式模板，可在 `LocalEmailTemplateRenderer` 之外新增 production template renderer 或 provider-specific renderer，不需要改 dispatcher。

## Probable next steps

- 後續 `writing-plans` 可標記需要更新既有 sequence/component UML，補上 local SMTP provider 與 Mailpit 的本機開發流。
- 不需要 Figma，因為本 change 沒有前端 UI。
