## Why

公司推動行銷活動時，優惠設定、對象圈選與訊息發送分散在不同工具與流程，且常需工程介入：活動上線慢、行銷人員無法自助、觸達成效不可見、也難以隨活動類型與規模成長。本變更建立一套**內部行銷後台的後端系統**，以「活動模組（Campaign）決定什麼活動要觸發」與「觸達（Reach）決定要發給誰、怎麼發、何時發」兩個基底支撐，首波交付折扣/優惠券活動的全流程後端能力。

## What Changes

- **campaign**：新增活動定義與生命週期能力——活動 CRUD、每型 RuleConfig 寫入前 schema 驗證、優惠券三層結構與核銷限制、樂觀鎖與稽核、狀態機（DRAFT/SCHEDULED/RUNNING/PAUSED/ENDED）、`PromotionEvaluator`（優惠計算）與 `ReachTriggerEvaluator`（觸發判定）兩條獨立 Strategy，以及排程批次掃描與行為事件消費兩條觸發路徑收斂發出 `ReachRequested`。
- **reach**：新增受眾展開與可靠發送能力——消費 `reach.requested`、`reach_request` 批次落庫與 fan-out 冪等、`AudienceResolver` 解析受眾、分頁展開 `ReachTask` 並做頻控、`ChannelAdapter`/`EmailAdapter` 發送、dispatcher 兩階段事務 + `FOR UPDATE SKIP LOCKED` + 指數退避重試 + DLQ、活動暫停/結束取消競態、計數背景聚合、成效查詢、抑制名單與 PII 最小化。

範圍僅含**後端**（不含消費者前台與後台 UI）。技術基底為 Spring Boot 3（Java 17/21）+ PostgreSQL + Kafka，架構為模組化單體 + Kafka 內部解耦。

## Impact

- Affected specs: `specs/campaign/`、`specs/reach/`
- Affected code: 新建 `campaign/`、`reach/`、`shared/` 三個 bounded module 與其資料表、Kafka topic（`domain.events`、`reach.requested`、`reach.dlq`）。
- Breaking changes: No（全新系統，無既有相容性負擔）。

## Related Artifacts

### Design
- [design.md](./design.md)
- [tasks.md](./tasks.md)

### Diagrams
- [Sequence: 觸達流程](./diagrams/01-sequence-reach-flow.puml)
- [State: 活動與任務狀態機](./diagrams/02-state-campaign-and-task-lifecycle.puml)
- [Class: 領域模型](./diagrams/03-class-domain-model.puml)
- [Component: 整體架構](./diagrams/04-component-architecture.puml)
- [ER: 資料庫綱要](./diagrams/05-er-database-schema.puml)
