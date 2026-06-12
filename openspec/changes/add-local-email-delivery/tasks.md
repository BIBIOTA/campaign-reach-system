# Tasks: add-local-email-delivery

## 1. 本機 SMTP 設定與依賴
- [ ] 1.1 加入 Spring Mail 依賴與本機 SMTP 設定模型
  - Acceptance: WHEN `reach` 模組需要建構本機 SMTP provider THEN build classpath 包含 Spring Mail 能力，且 `LocalSmtpEmailProperties` 可綁定 host、port、from、recipient、timeout 等本機設定
  - Acceptance: WHEN host、from、recipient 缺漏或 port 超出合法範圍 THEN Spring context 在設定綁定階段 fail fast
  - Depends on: -
  - Independence: independent
  - status: not_started
- [ ] 1.2 加入本機 provider 啟用條件
  - Acceptance: WHEN 沒有啟用 `local` profile THEN 本機 `EmailProviderClient` 不會註冊
  - Acceptance: WHEN 啟用 `local` profile 但 `campaignreach.email-provider.mode` 不是 `smtp-local` THEN 本機 `EmailProviderClient` 不會註冊
  - Acceptance: WHEN 啟用 `local` profile 且 `campaignreach.email-provider.mode=smtp-local` 並提供完整設定 THEN 本機 `EmailProviderClient` 會註冊，既有 `EmailAdapter` 也會被啟用
  - Depends on: 1.1
  - Independence: serial
  - status: not_started

## 2. 信件樣板與 SMTP provider
- [ ] 2.1 實作本機簡易信件樣板 renderer
  - Acceptance: WHEN renderer 收到 `ReachMessage(userId, EMAIL, templateRef)` THEN subject 包含 `[Local Campaign Reach]` 與 `templateRef`
  - Acceptance: WHEN renderer 產生 body THEN body 包含本機測試信提示、`templateRef`、`userId`、`channel` 與發送時間
  - Acceptance: WHEN `templateRef` 是任意非空字串 THEN renderer 使用通用本機樣板並成功產生內容，不因未知 templateRef 失敗
  - Depends on: -
  - Independence: independent
  - status: not_started
- [ ] 2.2 實作 `LocalSmtpEmailProviderClient`
  - Acceptance: WHEN provider 收到 EMAIL `ReachMessage` THEN 它使用固定本機 recipient、configured from address 與 renderer 內容透過 SMTP client 發送信件
  - Acceptance: WHEN SMTP 發送成功 THEN provider 回傳包含 provider message id 的 `SendResult`
  - Acceptance: WHEN SMTP client 丟出暫時性 transport failure THEN provider 讓既有 `EmailAdapter` 能將失敗轉成 retryable provider failure
  - Depends on: 1.1, 2.1
  - Independence: serial
  - status: not_started
- [ ] 2.3 維持 PII 最小化與 provider 邊界
  - Acceptance: WHEN 本機 provider 發送信件 THEN email address 只來自本機 provider 設定，不寫入 `reach_task`、Kafka event、`ReachMessage`、metrics API 或 audit trail
  - Acceptance: WHEN provider 記錄 log THEN 不輸出完整信件內容，必要時只記錄 provider message id 與 task/user 識別資訊
  - Depends on: 2.2
  - Independence: serial
  - status: not_started

## 3. 本機基礎設施與文件
- [ ] 3.1 將 Mailpit 加入本機 `docker-compose.yml`
  - Acceptance: WHEN 開發者執行 `docker compose up -d` THEN PostgreSQL、Kafka、Mailpit 會一起啟動
  - Acceptance: WHEN Mailpit 啟動 THEN SMTP port `1025` 可供 app 連線，Web UI port `8025` 可供開發者查看信件
  - Depends on: -
  - Independence: independent
  - status: not_started
- [ ] 3.2 更新 `.env.example` 的本機 email 設定
  - Acceptance: WHEN 開發者複製 `.env.example` 到 `.env` THEN 檔案包含啟用 local SMTP provider 所需的 mode、SMTP host、SMTP port、from、recipient 與 profile 設定提示
  - Acceptance: WHEN 使用 `.env.example` 預設值搭配 `docker compose up -d` THEN app 設定指向本機 Mailpit 而非真實外部 provider
  - Depends on: 1.1, 3.1
  - Independence: parallel-safe
  - status: not_started
- [ ] 3.3 更新 README 本機寄信 smoke test
  - Acceptance: WHEN 開發者閱讀 README THEN 能依步驟啟動 compose、載入 `.env`、啟動 app、觸發一筆 EMAIL reach，並在 `http://localhost:8025` 查看信件
  - Acceptance: WHEN README 描述本機寄信限制 THEN 它明確說明 Mailpit 不會外寄、固定 recipient 只供本機 smoke test 使用
  - Depends on: 3.1, 3.2
  - Independence: serial
  - status: not_started

## 4. 測試與驗證
- [ ] 4.1 加入設定與 context 測試
  - Acceptance: WHEN 設定缺漏或非法 THEN 測試證明 Spring context fail fast
  - Acceptance: WHEN profile 或 mode 不符合本機條件 THEN 測試證明本機 `EmailProviderClient` 不註冊
  - Acceptance: WHEN `local + smtp-local` 且設定完整 THEN 測試證明本機 `EmailProviderClient` 與既有 `EmailAdapter` 會啟用
  - Depends on: 1.2
  - Independence: serial
  - status: not_started
- [ ] 4.2 加入 renderer 與 provider 單元測試
  - Acceptance: WHEN renderer 收到有效 `ReachMessage` THEN 測試證明 subject/body 包含指定欄位與本機測試提示
  - Acceptance: WHEN provider 發送成功 THEN 測試證明 SMTP message 使用固定 recipient、configured from、renderer 內容，且回傳 `SendResult`
  - Acceptance: WHEN SMTP client 暫時性失敗 THEN 測試證明失敗會沿著既有 retryable provider path 傳遞
  - Depends on: 2.1, 2.2
  - Independence: serial
  - status: not_started
- [ ] 4.3 跑本 change 的品質 gate
  - Acceptance: WHEN 實作完成 THEN `./gradlew spotlessCheck checkstyleMain spotbugsMain test` 通過，或若本機缺少 Java 21 / Docker 等環境條件，驗證報告需明確記錄無法執行的原因與替代證據
  - Depends on: 1.1, 1.2, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 4.1, 4.2
  - Independence: serial
  - status: not_started

## Optional artifacts
- [x] PlantUML diagrams:
  - [01-sequence-local-email-delivery-flow.puml](./diagrams/01-sequence-local-email-delivery-flow.puml)
  - [02-component-local-smtp-email-architecture.puml](./diagrams/02-component-local-smtp-email-architecture.puml)
- [ ] Figma designs (spec-driven-dev:writing-figma)
