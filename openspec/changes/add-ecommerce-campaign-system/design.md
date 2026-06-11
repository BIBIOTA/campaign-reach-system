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
| 建置工具 | Gradle（Kotlin DSL）+ version catalog（見 §11） |
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

JSONB 結構須含固定欄位 `schema_version`（例如 `"schema_version": 1`），以支援未來欄位演進時的向後相容讀取。當讀取到舊版 JSONB 時，應用層負責轉換至當前 DTO 結構（upcaster），無需資料庫 migration。

#### 優惠券三層結構

折扣/優惠券的「使用限制」與「一人一碼 vs 共用碼」拆成三層，避免把代碼、限制與核銷混在單一表（完整綱要見 ER 圖）：

- **coupon_campaign**：規則與總量——`code_type`(SHARED_CODE/UNIQUE_CODE)、`total_usage_limit`、`per_user_limit`、`used_count`（核銷時 atomic update 控總量。**已知瓶頸**：熱門閃購高並發下此欄為熱點 row lock，MVP 規模可接受；未來量大時需考慮引入 Redis 預扣機制）。
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

#### send_cycle_key 命名與推導（冪等命脈）

**命名對齊**：事件 payload 與領域物件用駝峰 `sendCycle`（一個字串），落庫欄位用 snake_case `send_cycle_key`。**兩者為同一個值**，僅命名風格差異；orchestrator 寫 reach_request / reach_task 時直接以事件的 `sendCycle` 值填入 `send_cycle_key`，不做任何轉換。

**推導規則**（依 `trigger_type` 不同，且必須是**確定性、可重現**的，否則 unique constraint 失效會導致重複建立 task → 重複發送）：

| trigger_type | send_cycle_key 推導 | 去重語意 |
|---|---|---|
| `SCHEDULED_BATCH` | `sched:{campaignId}:{cycleStart}`，其中 `cycleStart` 為「依活動排程週期將觸發時點向下取整（truncate）後的 ISO-8601 字串」 | 同一活動同一排程週期只會產生一把 key；scheduler 重啟、補掃、多實例（ShedLock）重跑皆推導出相同 key |
| `EVENT` | `event:{triggerEventId}`，`triggerEventId` 為來源行為事件的唯一 ID | 同一來源事件重投只去重自身；**不同事件 id 不會互相去重**（跨事件的重複觸達由「頻控」處理，非冪等） |

`cycleStart` 的 truncate 粒度由活動排程設定決定（例：每日批次 → 截到日；每小時 → 截到時）。**禁止**在 key 內放入 `now()` 級別的即時時間戳或 scheduler 啟動時間，否則同一邏輯週期會算出不同 key。

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
   ├─ upsert reach_request 批次（unique(campaign_id, send_cycle_key, trigger_type) 確保同事件只建一筆批次）
   │    └─ 已存在且 status IN (DISPATCHING, DONE) → 直接 ack 跳過（Kafka 重投保護，fan-out 已完成）；否則（PENDING/EXPANDING）進入/續跑展開
   ├─ 凍結 snapshot（target_spec / reach_plan）
   ├─ AudienceResolver 解析 targetSpec → 收件人清單
   ├─ 分頁展開（每批 M 筆），逐批：
   │    ├─ 頻控（建立 ReachTask 前查詢時間窗口，防不同事件在短時間對同人重複發）
   │    └─ 批次 INSERT ReachTask(PENDING)，ON CONFLICT DO NOTHING（task unique 防重複，支援斷點續跑）
   ├─ 更新 reach_request.status：EXPANDING → DISPATCHING；total_count 於展開完成時一次回填
   └─► 逐筆送 dispatcher

[dispatcher] 以 FOR UPDATE SKIP LOCKED 撈可發送任務（兩階段事務，避免外部 I/O 佔用 DB 連線）
   ├─ 階段 1（短事務）：撈 status IN (PENDING, RETRY_SCHEDULED) AND next_retry_at <= now()
   │    └─ 更新 PROCESSING + locked_by/locked_until（租約，如 5 min）→ 立即 commit，釋放 DB 連線
   ├─ 外部呼叫（事務外）：依 channel 選 ChannelAdapter → send()
   ├─ 階段 2（短事務）：依呼叫結果開新事務回寫
   │    ├─ 成功 → ReachTask=SENT，寫 send_result，清除 locked_by
   │    └─ 失敗 → RETRY_SCHEDULED(設 next_retry_at, 指數退避)，超限 → FAILED + 進 DLQ
   └─ Reaper job（背景排程）：定期掃描 status=PROCESSING AND locked_until < now()，重置為 PENDING，防 worker crash 導致任務卡死
```

### 關鍵設計點

- 兩條路徑收斂到同一個 `reach.requested` topic，下游受眾解析與發送邏輯只寫一次。
- 受眾解析一律在 reach 完成，campaign 不展開收件人。
- 一筆 `ReachRequested` → 一筆 `reach_request`（批次） → N 筆 `ReachTask`（任務）。
- `ReachTask` 落 DB 是發送的 source of truth，支撐重試、報表、冪等。
- **冪等與頻控分層處理**（語意不同，實作機制不同）：
  - **冪等（Idempotency）**：DB unique constraint `(campaign_id, user_id, send_cycle_key, channel)` 防止同一事件/週期重複建立 ReachTask。注意：事件觸發的 `send_cycle_key = event:{id}`，不同事件 id 不相同，unique constraint 無法跨事件去重。
  - **頻控（Frequency Capping）**：orchestrator 建立 ReachTask 前，查詢該用戶在指定時間窗口內的歷史 reach_task（`WHERE campaign_id = :cid AND user_id = :uid AND created_at > :threshold`），命中則跳過，確保短時間不重複觸達。

#### 受眾展開（fan-out）可靠性

單筆 `ReachRequested` 可能展開成 10 萬筆 task，展開過程須能承受 crash 與 Kafka at-least-once 重投：

- **批次冪等**：`reach_request` 以 `unique(campaign_id, send_cycle_key, trigger_type)` 去重，同一事件重投不會建立第二筆批次，計數也不會被重複污染。
- **斷點續跑**：展開分頁進行，task 以 `ON CONFLICT DO NOTHING` 寫入（落在 reach_task 的四欄 unique 上）。展開到一半 crash 後重投，已寫入的 task 不會重複，未寫入的續寫，最終收斂到完整 N 筆。
- **狀態推進**：reach_request 走 `PENDING → EXPANDING → DISPATCHING → DONE`。`total_count` 於展開完成、推進至 `DISPATCHING` 時一次回填，故 `DISPATCHING`/`DONE` 皆視為展開（fan-out）已完成、重投時直接跳過；只有 `PENDING`/`EXPANDING` 視為未完成，允許 orchestrator 重新接手續跑。

#### reach_request 計數欄位的維護（避免第二個熱點）

`total_count / pending_count / sent_count / failed_count` **不採「每筆 task 狀態變更即時 update 同一 reach_request row」**——否則單一批次列在 10 萬筆 task 高並發下會成為 row-lock 熱點（與 `coupon_campaign.used_count` 同類問題）。改採：

- `total_count`：展開完成時一次回填。
- `sent/failed/pending_count`：由背景排程**定期聚合 reach_task 回填**（近似即時，報表可接受秒級延遲），而非逐筆即時更新。
- 需要精確即時數字的場景（少數）才直接 `COUNT(*)` reach_task（有 `reach_task(campaign_id, status)` 索引支撐）。

詳見第 8 節 Scaling 風險清單。

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

- EmailAdapter 包 circuit breaker（Resilience4j）：breaker 開啟時，dispatcher 在**階段 1 標記 PROCESSING 前**即偵測 breaker 狀態，直接跳過該筆、任務維持 `PENDING`（未進入 PROCESSING），恢復後由 dispatcher 重掃。若 breaker 在已標 PROCESSING 後才於外部呼叫快速失敗，則比照可重試失敗走階段 2 回寫 `RETRY_SCHEDULED`，不會卡在 PROCESSING。

### 活動暫停/結束的取消競態（與 dispatcher 的互動）

活動進入 `PAUSED`/`ENDED` 時要取消未送 task（FR-017），但取消動作與 dispatcher 撈取存在競態，語意明確定義如下：

- **取消只作用於尚未進入發送的任務**：批次 `UPDATE reach_task SET status='CANCELLED' WHERE campaign_id=:cid AND status IN ('PENDING','RETRY_SCHEDULED')`。`PROCESSING` 不在取消範圍。
- **dispatcher 階段 1 設防**：撈取並標記 PROCESSING 的同一短事務內，重新檢查活動狀態（`campaign.status NOT IN ('PAUSED','ENDED')`）；活動已停用者不標 PROCESSING、改標 `CANCELLED`。如此「取消」與「撈取」由 DB 列鎖序列化，二者不會同時放行同一筆。
- **已 PROCESSING 者放行（明示邊界）**：若某筆在取消發生前的瞬間已被標 PROCESSING（外部 send 進行中），則**允許其完成**，不強制中止。這是有界且極小的洩漏窗口（單筆外部呼叫時長），本系統明確接受此語意，不為消除它而引入分散式中止協定。

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

## 8. Scaling 風險清單

集中列出已知擴展瓶頸，避免讀者誤以為僅有單一熱點。MVP（10 萬筆級）皆可接受，標註演進方向：

| # | 熱點 | 成因 | MVP 緩解 | 演進方向 |
|---|---|---|---|---|
| S-1 | `coupon_campaign.used_count` row lock | 熱門閃購高並發核銷對同一列 atomic update | 控總量正確性優先，接受序列化 | Redis 預扣 + 非同步回寫 |
| S-2 | `reach_request` 計數欄位 | 逐筆 task 變更即時 update 同一批次列 | **不即時更新**，改背景定期聚合回填（見 §5） | 計數外移至 OLAP / 物化檢視 |
| S-3 | 受眾展開 fan-out | 單事件展開 10 萬 task 的寫入與續跑 | 分頁批次 INSERT + ON CONFLICT，斷點續跑 | 拆多 partition 並行展開 |
| S-4 | Kafka 熱分區 | 單一大型活動的事件集中到同一 partition | partition key 選擇見 §9，避免以 campaign_id 單鍵分區 | 動態子分區 / 加大分區數 |
| S-5 | dispatcher DB 撈取爭用 | 大量 worker 對 reach_task 撈取 | `FOR UPDATE SKIP LOCKED` + 覆蓋索引 | 分片撈取 / 多隊列 |

## 9. Kafka 規格（topic / 分區 / 消費者）

| topic | producer | consumer group | 用途 | 訊息層級 |
|---|---|---|---|---|
| `domain.events` | 電商主站 | `campaign-trigger` | 行為事件（CartAbandoned, OrderPlaced…） | 使用者層級 |
| `domain.events.DLT` | campaign（error handler） | 人工 / 重放工具 | campaign-trigger 消費端無法處理（反序列化失敗 / publish 重試耗盡）的行為事件，避免毒丸卡 partition | 使用者層級 |
| `reach.requested` | campaign（scheduler + consumer） | `reach-orchestrator` | 觸發觸達請求 | 活動層級 |
| `reach.dlq` | reach.dispatcher | 人工 / 重放工具 | 重試耗盡的 task | 使用者層級 |

**分區鍵（partition key）**

- `domain.events`：以 `user_id` 分區。保證同一使用者的行為事件有序，且自然分散，避免熱分區。
- `domain.events.DLT`：沿用來源訊息的 partition，重放時保有 per-user 原序。
- `reach.requested`：**以 `reach_request_id`（或 `campaign_id + send_cycle_key` 的雜湊）分區，而非單純 `campaign_id`**。理由：單純以 `campaign_id` 分區會使單一大型活動的所有請求集中到一個 partition，形成熱分區並拖累其他活動（違反 NFR-002「活動間互不影響」）。本系統 `reach.requested` 為活動層級、量少（每 cycle 一筆），分散後即可避免集中。
- `reach.dlq`：沿用來源 task 的鍵即可，重放時保有原序。

**Ordering 假設**

- `domain.events` 需要 per-`user_id` 有序（同人行為先後語意）；跨 user 無序。
- `reach.requested` **不依賴跨訊息順序**——每筆活動層級請求獨立展開，冪等由 DB constraint 保證，故無嚴格 ordering 需求。

**消費者冪等**

- 所有 consumer 採 at-least-once；冪等由消費端的 DB unique constraint 達成（reach_request 批次鍵、reach_task 四欄鍵），不依賴 broker exactly-once。
- consumer offset 在「處理已落庫（含 reach_request upsert / task 寫入）」後才 commit，確保重投可安全續跑。
- `campaign-trigger` 消費端（路徑2）本身不落 DB，「處理完成」即「命中活動的 `ReachRequested` 已同步發布成功」。publish 失敗（broker 拒絕 / 逾時）會向上拋、不 ack，事件由 Kafka 重投；重投造成的重複發布由下游 reach 的 `unique(campaign_id, send_cycle_key, trigger_type)` 收斂為 effectively-once。反序列化失敗或重試耗盡的毒丸訊息經 error handler 進 `domain.events.DLT`，避免無限重投卡住 partition。

## 10. PII、安全與資料保留

Email 為個資（PII），本系統處理收件人資料，須定錨以下立場：

**收件人資料來源與儲存**

- `reach_task` 只存 `user_id`，**不落收件 email 等 PII**。實際 email 於 dispatcher 發送當下，由 user/profile 服務以 `user_id` 即時解析取得，發送後不持久化於 reach 表。
- `send_result` 僅存 `provider_message_id` 與 outcome，不存信件內容與收件地址。

**安全**

- 內部 REST（活動 CRUD、重放工具）須經身分驗證與授權（營運後台角色），非公開端點；寫入操作記入 `created_by`/`updated_by` 稽核欄位。
- 與 Email provider 的金鑰經 secret 管理（環境變數 / vault），不入庫、不入版控。
- 傳輸層 TLS；DB 連線加密。

**退訂與抑制名單（suppression）**

- 發送前檢查抑制名單（退訂、硬退信、投訴）；命中者該 task 直接標 `FAILED`（不可重試原因），不送出。退訂屬第 6 節「不可重試」分類。
- 抑制名單的維護與來源（消費者退訂入口）屬電商主站範疇，本系統僅消費其結果；MVP 以一張 `suppression`（user_id / channel / reason）查表，後續可演進。

**資料保留**

- `reach_task` / `send_result` 為觸達稽核軌跡，預設保留 N 個月（具體期限為 Open Question，待法遵確認）後歸檔或刪除。
- 任何含 PII 的衍生快照（如未來若快照收件清單）須一併納入保留策略；目前設計刻意不落 PII 快照以降低暴露面。

> 註：退訂入口 UI、抑制名單來源系統、確切保留月數的細節於 writing-spec 階段補實；本節定錨設計立場。

## 11. 工程規範與開發約定

本節定錨 Java 開發的建置、格式、靜態分析與 CI gate，使三個 bounded module（`campaign`/`reach`/`shared`）的程式碼風格、品質門檻一致且可機器強制。**原則：規範由工具強制、CI 擋關，而非靠人工 review 抓格式**，把風格爭論成本降到零。

### 11.1 建置工具：Gradle（Kotlin DSL）

採 **Gradle + Kotlin DSL（`build.gradle.kts`）**，理由與被否決方案：

- 多模組增量編譯 + build cache + 守護程序，模組數成長後 build 速度優於 Maven；契合「模組化單體、保留拆微服務縫」的演進路徑。
- 依賴以 **version catalog（`gradle/libs.versions.toml`）集中宣告**，三個 module 共用同一份版本來源，避免版本漂移。
- 被否決：**Maven** —— 零學習成本、宣告式可預測，但多模組全量建置較慢、XML 冗長；本案模組會成長且需與 ArchUnit/靜態分析緊密整合，Gradle 回報較高。
- build 邏輯約束：**禁止在 build script 寫「聰明但難維護」的命令式邏輯**；共用設定收斂到 convention plugin（`buildSrc` 或 `build-logic`），各 module 的 `build.gradle.kts` 維持薄、宣告式。

### 11.2 程式碼格式化：Spotless + Palantir Java Format

- 以 **Spotless** 掛載 **Palantir Java Format**（4 空格縮排、對 builder/fluent/stream 換行更友善），**無客製規則、不可個別關閉**——格式由工具單一來源決定。
- 同時管理 import 排序（移除未使用 import）、移除多餘空白、檔尾換行。
- 本地：`./gradlew spotlessApply` 自動修正；CI：`./gradlew spotlessCheck` 不合即 fail。
- 建議搭配 pre-commit hook，把格式修正前移到提交當下，減少 CI 來回。

### 11.3 風格檢查與靜態分析

| 工具 | 角色 | 門檻 |
|---|---|---|
| **Checkstyle** | 命名、檔案結構、可見度、Javadoc 等 google_checks 基礎風格（格式交給 Spotless，Checkstyle 不重複管排版） | violation = build fail |
| **SpotBugs** | bytecode 級潛在 bug（null、資源未關、並發誤用） | High/Normal 等級 = build fail |
| **ArchUnit** | 架構守護：`campaign` 與 `reach` 不得互相 import domain，僅透過 `shared/event` 溝通（呼應 §3 邊界規則） | 違規測試 fail |

> ArchUnit 已於 task 1.1 acceptance 要求；本節將其與格式/靜態分析併入同一道工程品質 gate。

### 11.4 命名與套件約定

- 套件根 `com.example.campaignreach`，下分 `campaign`/`reach`/`shared`，再依 §3 模組內結構（api/domain/evaluation/scheduler、orchestrator/audience/channel/dispatcher、event/config）。
- 類別命名沿用設計詞彙：Strategy 介面以角色命名（`PromotionEvaluator`、`ReachTriggerEvaluator`）、Adapter 以 `XxxAdapter`、Kafka event 以動作完成式（`ReachRequested`、`ReachTaskCreated`、`SendResultRecorded`）。
- **跨層命名對齊**：Java 物件/事件 payload 用駝峰（`sendCycle`），DB 欄位用 snake_case（`send_cycle_key`），兩者為同一值僅命名風格差異（見 §5），不做隱式轉換。
- 列舉值全大寫（活動 `status`、`trigger_type`、ReachTask 狀態機），與 §4/§5/§6 一致。

### 11.5 CI 品質 gate

每次 PR 觸發、全部通過才可合併（任一 fail 即擋）：

```
./gradlew spotlessCheck      # 格式
./gradlew checkstyleMain     # 風格
./gradlew spotbugsMain       # 靜態分析
./gradlew test               # 單元 + 整合（Testcontainers，見 §7）+ ArchUnit
```

- 測試覆蓋率以 **JaCoCo** 量測，核心邏輯（Evaluator、冪等鍵、重試分類）為高覆蓋重點（見 §7）；MVP 設一條最低門檻避免裸退，確切百分比於實作期校準。
- 上述指令亦提供單一聚合任務（如 `./gradlew check`）供本地一次跑完。

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
