---
change_id: add-ecommerce-campaign-system
doc_language: 繁體中文
---

# 設計文件：電商行銷活動系統（活動模組 × 觸達）

## 1. 背景與目標

為電商建立一套**內部行銷後台的後端系統**，以兩個基底支撐：

- **活動模組（Campaign）**：定義行銷活動與其優惠規則，並判定活動是否應觸發。
- **觸達（Reach）**：依活動觸發結果，解析對象、展開收件人，並透過通道送達。

責任分工的一句話原則：

> **Campaign 決定「什麼活動要觸發」；Reach 決定「要發給誰、怎麼發、何時發」。**

本變更僅涵蓋**後端**（不含前台消費者頁面、不含後台 UI 實作）。技術基底為 Spring Boot 3 + Java、PostgreSQL、Kafka。

### 範圍

| 面向 | 決定 |
|---|---|
| 系統定位 | 內部行銷後台，僅後端 |
| 框架 | Spring Boot 3（Java 17/21） |
| 儲存 | PostgreSQL |
| 訊息佇列 | Kafka |
| 活動類型 | 折扣/優惠券、滿額贈禮/加價購、限時特賣/閃購 |
| 觸達通道 | Email 優先，介面預留可擴充其他通道 |
| 觸達時機 | 排程批次發送 + 事件觸發（混合） |

### 不在範圍（YAGNI）

- 點數/會員集點活動（本期不做）
- 消費者前台頁面、後台管理 UI
- 進階 CDP / 標籤分眾（先做靜態名單 + 簡單條件）

## 2. 架構選型

採 **方案 A：模組化單體（Modular Monolith）+ Kafka 內部解耦**。

單一可部署的 Spring Boot 應用，內部切成清楚的 bounded module，`campaign` 與 `reach` 不互相依賴 domain，僅透過 Kafka 事件溝通。保留日後拆分為微服務的縫，但目前以單一部署降低開發與維運成本。

### 為什麼單體內仍使用 Kafka

雖然系統初期為單一部署，但觸達發送具有**非同步、可重試、可延遲、可削峰、可重放**的需求，因此核心流程不採同步呼叫。Kafka 作為模組間的事件邊界，讓 Campaign 不需等待 Reach 發送完成，也保留未來將 Reach 拆成獨立服務的演進路徑。若改用模組間直接方法呼叫，會把觸達的耗時與失敗耦合進活動判定流程，違背上述需求。

### 被否決的方案

- 方案 B（微服務）：對單一團隊與「先以 Email」規模屬過度設計，徒增分散式交易與維運成本。
- 方案 C（純分層、不用內部事件）：事件觸發 + 可重試發送以同步呼叫會阻塞主流程，且與「使用 Kafka」需求不符。

## 3. 模組邊界

```
campaign-reach-system (single deployable)
│
├── campaign/                  ← 活動模組（定義 + 觸發判定）
│   ├── api          活動 CRUD、啟用/停用（內部 REST）
│   ├── domain       Campaign 聚合、RuleConfig DTO
│   ├── evaluation   PromotionEvaluator / ReachTriggerEvaluator（Strategy）
│   └── scheduler    活動生命週期排程（開跑、結束、批次掃描）
│
├── reach/                     ← 觸達模組（受眾 + 編排 + 發送）
│   ├── orchestrator 消費 ReachRequested → 解析受眾 → 產生 ReachTask
│   ├── audience     AudienceResolver：targetSpec → 收件人清單
│   ├── channel      ChannelAdapter 介面 + EmailAdapter（先行）
│   └── dispatcher   非同步發送、重試、結果回寫
│
└── shared/                    ← 共用 kernel（穩定契約）
    ├── event        Kafka event schema (ReachRequested, ReachTaskCreated...)
    └── config       Kafka / DB / 排程設定
```

### 邊界規則

- `campaign` 與 `reach` 不互相 import domain，只透過 `shared/event` 的 Kafka 訊息溝通。
- **`shared` 只放跨模組穩定契約**：event schema、common exception、基礎 config。**不得放** campaign/reach 的 domain entity、repository、service，避免 `shared` 退化成耦合中心、破壞 bounded module。
- **受眾解析（audience）一律由 `reach` 負責**，`campaign` 不展開收件人清單（見第 4、5 節）。
- 兩種觸發來源最終都收斂到同一個 Kafka topic `reach.requested`：
  - 排程批次 → scheduler 掃描符合條件的活動 → 發出 `ReachRequested`
  - 事件觸發 → 外部行為事件進來 → campaign 判定 → 發出 `ReachRequested`
- `reach.orchestrator` 是唯一的 `reach.requested` 消費者，統一處理受眾解析與發送。

## 4. 核心元件與領域模型

### Campaign 聚合

```
Campaign
├── id, name, status (DRAFT/SCHEDULED/RUNNING/ENDED/PAUSED)
├── type (DISCOUNT / GIFT_ADDON / FLASH_SALE)
├── period (startAt, endAt)
├── ruleConfig (JSONB — 隨 type 不同的規則參數)
├── targetSpec (對象條件：名單ID / 分眾條件 / 全體)
└── reachPlan (要用哪些通道、模板、發送時機)
```

#### ruleConfig 與 schema 驗證

每種 `CampaignType` 對應一個強型別 RuleConfig DTO，**建立/更新活動時先做 schema validation，通過後才序列化存入 JSONB**，把錯誤設定從執行期提前到設定期：

```
DISCOUNT    → DiscountRuleConfig
GIFT_ADDON  → GiftAddonRuleConfig
FLASH_SALE  → FlashSaleRuleConfig
```

JSONB 提供彈性儲存，DTO 提供型別安全與驗證，兩者互補。

#### 優惠券三層結構

折扣/優惠券的「使用限制」與「一人一碼 vs 共用碼」拆成三層，避免把代碼、限制與核銷混在單一表（完整綱要見 ER 圖）：

- **coupon_campaign**：規則與總量——`code_type`(SHARED_CODE/UNIQUE_CODE)、`total_usage_limit`、`per_user_limit`、`used_count`（核銷時 atomic update 控總量）。
- **coupon_code**：個別優惠碼——共用碼一筆；一人一碼多筆，含 `assigned_user_id` 與 `status`(AVAILABLE/ASSIGNED/REDEEMED/EXPIRED)。
- **coupon_redemption**：核銷紀錄——唯一鍵 `(coupon_code_id, user_id, order_id)` 防同單重複核銷；每人限用 N 次以 transaction + 計數檢查。

#### 活動稽核與並發

campaign 表含 `version`（樂觀鎖，防兩名營運同時編輯互相覆蓋）與 `created_by`/`updated_by`/`updated_at`（稽核）。

### 規則計算分兩類（重要區分）

活動規則有兩種語意不同的計算，**不可混為一談**：

| 類型 | 介面 | 用途 | 輸入情境 |
|---|---|---|---|
| **優惠計算** | `PromotionEvaluator` | 算出實際優惠（折扣金額、贈品、加價購、閃購價） | 購物車 / 訂單 ctx（結帳流程） |
| **觸達觸發判定** | `ReachTriggerEvaluator` | 判斷活動是否應對某情境產生觸達事件 | 行為事件 / 排程 cycle（行銷觸達流程） |

```java
interface PromotionEvaluator {
    CampaignType supports();
    PromotionResult evaluate(CartContext ctx);   // 結帳時計算優惠
}

interface ReachTriggerEvaluator {
    CampaignType supports();
    boolean shouldTrigger(TriggerContext ctx);   // 判斷是否觸發觸達
}
```

說明：使用者尚未進購物車時（如棄購提醒、活動開跑通知、沉睡會員召回），由 `ReachTriggerEvaluator` 依行為事件或排程 cycle 判定是否觸發，**不需要購物車 ctx**；優惠金額的實際計算則在結帳流程由 `PromotionEvaluator` 處理。兩者皆採 Strategy，新增活動類型 = 新增對應 Evaluator，不動既有程式（OCP）。

### 觸達通道 → Adapter

```java
interface ChannelAdapter {
    Channel channel();                 // EMAIL, SMS, PUSH...
    SendResult send(ReachMessage msg); // 同步回傳結果，失敗拋可重試例外
}
```

- `EmailAdapter` 先行（介接 SendGrid/SES，介面包一層）
- 新通道 = 新增 Adapter + 註冊，orchestrator 依 `reachPlan` 選用

### 對象解析（Audience Resolver，位於 reach）

`AudienceResolver` 介面，將 `targetSpec` 解析成實際收件人清單。**統一由 reach 模組執行**。先支援：靜態名單、簡單條件分眾（會員等級、地區）。日後可接更複雜的標籤系統。

## 5. 事件模型與資料流

### 兩層事件

事件分為**活動層級**與**使用者層級**，語意嚴格區分：

```
Campaign            → ReachRequested      (活動層級：一筆)
Reach.orchestrator  → ReachTaskCreated    (使用者層級：展開成 N 筆)
Reach.dispatcher    → SendResultRecorded  (使用者層級：發送結果)
```

`ReachRequested` 是**活動層級事件**，**不帶完整收件人清單**：

```json
{
  "campaignId": "...",
  "targetSpec": "...",
  "reachPlan": "...",
  "triggerType": "SCHEDULED_BATCH | EVENT",
  "sendCycle": "2026-06-09T10:00"
}
```

`reach.orchestrator` 消費後才解析 `targetSpec`、展開收件人，將「一筆 `ReachRequested`」展開成「N 筆 `ReachTask`」。

#### 觸達批次落庫（reach_request）

`ReachRequested` 一進 reach 即先落為一筆 **reach_request**（活動層級批次紀錄），再展開 reach_task：

- 凍結 `target_spec_snapshot` / `reach_plan_snapshot`：活動事後被改，仍可追溯當時的發送依據。
- 維護 `total_count` / `pending_count` / `sent_count` / `failed_count`：活動報表直接讀批次計數，免每次聚合 reach_task。
- `status`（PENDING/EXPANDING/DISPATCHING/DONE/FAILED/CANCELLED）+ `send_cycle_key`：支撐「批次重跑」與排程「已處理 cycle」補償。
- 觸發語意以 `trigger_type`（SCHEDULED_BATCH/EVENT）與 `trigger_event_id` 表達，取代以 free-text 表示 cycle。

### 路徑 1：排程批次發送

```
[Scheduler] 每 N 分鐘掃描 status=RUNNING 的活動
   ├─ ReachTriggerEvaluator 判定到達「發送時機」的活動
   └─► 發出 ReachRequested(campaignId, targetSpec, reachPlan, sendCycle)
        到 Kafka topic「reach.requested」
```

### 路徑 2：事件觸發

```
[外部行為事件] 進 Kafka topic「domain.events」(CartAbandoned, OrderPlaced...)
   ├─ [campaign consumer] 比對 RUNNING 活動
   ├─ ReachTriggerEvaluator.shouldTrigger(ctx) → 命中
   └─► 發出 ReachRequested(...) 到 同一個 topic「reach.requested」
```

### 共同下游：受眾解析與發送

```
[reach.orchestrator] 消費 reach.requested（活動層級）
   ├─ 建立 reach_request 批次（凍結 snapshot）
   ├─ AudienceResolver 解析 targetSpec → 收件人清單
   ├─ 去重/頻控（同人同活動短時間內不重複發）
   ├─ 為每個收件人建立 ReachTask(PENDING) 寫入 DB（使用者層級）
   └─► 逐筆送 dispatcher

[dispatcher] 以 FOR UPDATE SKIP LOCKED 撈可發送任務
   ├─ 條件：status IN (PENDING, RETRY_SCHEDULED) AND next_retry_at <= now()
   ├─ 標記 PROCESSING（locked_by / locked_until 防多 worker 搶同一筆）
   ├─ 依 channel 選 ChannelAdapter → send()
   ├─ 成功 → ReachTask=SENT，寫 send_result
   └─ 失敗 → RETRY_SCHEDULED(設 next_retry_at, 指數退避)，超限 → FAILED + 進 DLQ
```

### 關鍵設計點

- 兩條路徑收斂到同一個 `reach.requested` topic，下游受眾解析與發送邏輯只寫一次。
- 受眾解析一律在 reach 完成，campaign 不展開收件人。
- 一筆 `ReachRequested` → 一筆 `reach_request`（批次） → N 筆 `ReachTask`（任務）。
- `ReachTask` 落 DB 是發送的 source of truth，支撐重試、報表、冪等。
- 冪等鍵：`(campaign_id, user_id, send_cycle_key, channel)`——含 channel 以支援未來同週期多通道；`send_cycle_key` 排程為時間鍵、事件為 `event:{id}`。

## 6. 錯誤處理與韌性

### 發送層

- **重試**：可重試錯誤（網路、429、5xx）走指數退避（1m→5m→30m，最多 3 次），期間狀態為 `RETRY_SCHEDULED`；不可重試（地址無效、退訂）直接標 `FAILED`。
- **DLQ**：超過重試上限的 `ReachTask` 進 dead-letter topic + 標記，供人工檢視與重放。
- **冪等與發送語意**：Kafka 採 **at-least-once 消費語意**，消費端透過 DB unique constraint 與 idempotency key `(campaign_id, user_id, send_cycle_key, channel)` 避免重複建立 `ReachTask`，達成**業務層級的 effectively-once task creation**。
  - 注意：這不是對外部 Email provider 的嚴格 exactly-once delivery。即使只建立一筆 `ReachTask`，仍可能發生「Email 已送出但更新 `SENT` 前 crash → 重試重送」。
  - 緩解：以 provider message id 或本地 send lock 降低重複發送機率。**本系統目標是避免重複建立任務並降低重複發送機率，而非宣稱對外部 provider 達成嚴格 exactly-once delivery。**

### 規則計算層

- Evaluator 拋例外 → 該筆判定記為 skipped 並記錄原因，不影響同批其他對象。
- 限時閃購庫存扣減用 DB 原子操作/樂觀鎖，扣減失敗回傳「已售罄」而非錯誤。

### 排程層

- Scheduler 採「掃描 + 標記已處理 cycle」，漏跑可補掃；多實例部署用 ShedLock 確保同一 cycle 只跑一次。

### 外部通道服務中斷

- EmailAdapter 包 circuit breaker（Resilience4j）：服務掛掉時快速失敗並讓 ReachTask 留在 PENDING，恢復後由 dispatcher 重掃。

### ReachTask 狀態機

設計層保留完整狀態，MVP 可先實作子集（PENDING/PROCESSING/SENT/FAILED/DLQ）：

```
PENDING          初建立，待處理
PROCESSING       worker 取走處理中（防多 worker 搶同一筆）
SENT             發送成功
RETRY_SCHEDULED  發送失敗，等待下一次重試
FAILED           不可重試或重試耗盡
DLQ              進入 dead-letter，待人工檢視/重放
CANCELLED        活動暫停或結束，未發送任務取消
```

worker 友善欄位：`next_retry_at`、`processing_started_at`、`last_attempt_at`、`locked_by`、`locked_until`——支撐 `FOR UPDATE SKIP LOCKED` 並行撈取，避免多 worker 互搶（呼應大量觸達壓測需求）。

### 可觀測性

- 每個 ReachTask 全程狀態可查；活動維度彙總送達率、失敗率。

## 7. 測試策略

### 單元測試（核心邏輯，最高覆蓋）

- 每個 `PromotionEvaluator`：折扣/滿贈加價購/閃購規則，含邊界（剛好滿額、未達、庫存=0、活動已結束）。
- 每個 `ReachTriggerEvaluator`：行為事件與排程 cycle 的觸發判定。
- 各 RuleConfig DTO 的 schema 驗證（合法/非法設定）。
- 冪等鍵產生、頻控去重、重試分類（可重試 vs 不可重試）。
- `AudienceResolver` 各 targetSpec 解析。

### 整合測試（模組邊界，Testcontainers Kafka + PostgreSQL）

- 發 `domain.events` → 驗證命中活動 → 確認 `reach.requested` 被產生（活動層級，不含收件人）。
- 消費 `reach.requested` → 驗證受眾展開與 `ReachTask` 落庫狀態正確。
- `ChannelAdapter` 用 wiremock 模擬第三方成功、429、5xx、逾時。

### 契約 / 韌性測試

- 重試到上限 → 驗證進 DLQ。
- 冪等：同事件重送兩次 → 只建立一筆 `ReachTask`。
- ShedLock：模擬雙實例 → 同 cycle 只執行一次。
- 一筆 `ReachRequested` → 正確展開成 N 筆 `ReachTask`。

### 端到端冒煙（backend-only）

- 內部 REST 建立活動 → 觸發 → 查 `ReachTask` 與彙總報表，驗證全鏈路。

**原則**：規則計算與冪等/重試用快速單元測試密集覆蓋；跨 Kafka/DB 行為用 Testcontainers 少量但真實地驗證，不 mock 掉 broker。

## Diagrams

- [Sequence: 觸達流程](./diagrams/01-sequence-reach-flow.puml) — 排程與事件兩條觸發路徑收斂、受眾展開、發送與重試/DLQ
- [State: 活動與任務狀態機](./diagrams/02-state-campaign-and-task-lifecycle.puml) — Campaign 生命週期與 ReachTask 狀態機
- [Class: 領域模型](./diagrams/03-class-domain-model.puml) — Campaign 聚合、兩類 Evaluator (Strategy)、ChannelAdapter (Adapter)、受眾展開
- [Component: 整體架構](./diagrams/04-component-architecture.puml) — campaign/reach/shared 模組與 Kafka、PostgreSQL、Email provider 的關係
- [ER: 資料庫綱要](./diagrams/05-er-database-schema.puml) — 活動、名單、觸達批次/任務、發送結果、優惠券三層與索引建議

## Probable next steps

本章節同時作為 spec-driven-dev pipeline 的後續交付物清單與下游 skill 串接依據：

- **PRD（`spec-driven-dev:prd`）**：定義產品需求、User Stories、Acceptance Criteria。使用者已於原始需求明確要求 PRD。
- **UML（`spec-driven-dev:writing-uml`）**：補充 component、sequence、state、class diagram。本系統具備複雜元件互動（兩條觸發路徑收斂）、狀態機（Campaign 生命週期、ReachTask 狀態機）、Strategy/Adapter 類別結構，建議產出。使用者已明確要求 UML。
- **OpenSpec Spec（`spec-driven-dev:writing-spec`）**：定義 API、資料模型、行為規格與測試情境。使用者已明確要求 OpenSpec 產出。
- **Figma**：本期不需要，因本系統僅涵蓋後端、無 UI。
