## Why

目前 `reach` 模組已具備 `EmailAdapter` 與 `EmailProviderClient` seam，但沒有 concrete provider binding。開發者在本機只能驗證 `reach_task` 建立與 dispatcher 排程邏輯，無法完整驗證 EMAIL 任務真的透過 SMTP 被送出，也無法在本機查看信件內容。

此變更提供一條安全的本機寄信路徑：app 透過 SMTP 將信送到 Mailpit，本機 Web UI 可查看信件，且不會外寄到真實收件人。

## What Changes

- **reach**：新增 local/profile-gated SMTP `EmailProviderClient`，使用簡易內建樣板與固定本機收件信箱，並透過既有 `EmailAdapter`、circuit breaker、dispatcher retry flow 發送。
- **local development**：在 `docker-compose.yml` 加入 Mailpit，更新 `.env.example` 與 README，讓開發者可執行本機 email smoke test。
- **PII boundary**：維持既有設計，email address 只存在本機 provider 設定中，不寫入 `reach_task`、Kafka event、`ReachMessage`、metrics API 或 audit trail。

## Impact

- Affected specs: `specs/reach/`
- Affected code: `reach/src/main/java/com/example/campaignreach/reach/channel/`, `reach/src/test/java/com/example/campaignreach/reach/channel/`, `gradle/libs.versions.toml`, `reach/build.gradle.kts`, `docker-compose.yml`, `.env.example`, `README.md`
- Breaking changes: No. 本機 SMTP provider 只在 `local` profile 且 `campaignreach.email-provider.mode=smtp-local` 時啟用；未啟用時維持現有無 provider binding 的行為。

## Related Artifacts

### Design

- [design.md](./design.md)
- [tasks.md](./tasks.md)

### Diagrams

- [Sequence: 本機 Email 觸達寄送流程](./diagrams/01-sequence-local-email-delivery-flow.puml)
- [Component: 本機 SMTP Email 架構](./diagrams/02-component-local-smtp-email-architecture.puml)

### Figma Designs

- None
