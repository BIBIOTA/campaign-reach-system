## ADDED Requirements

### Requirement: 活動優惠規則建立、查詢與修改
The system SHALL allow marketing users to create, query, and modify a campaign's promotion rules — including name, period (start/end), promotion type, promotion content, usage threshold, and usage limits — and SHALL persist the rule parameters as validated JSONB. (FR-001)

#### Scenario: 建立折扣活動並落為草稿
- **WHEN** 行銷人員以合法欄位呼叫建立活動 API（名稱、起訖時間、優惠類型、優惠內容、門檻、限制、觸達對象、發送計畫）
- **THEN** the system 建立活動並將狀態設為 DRAFT
- **AND** 優惠規則設定與觸達發送設定可分別儲存與修改
- **AND** 回傳活動 id 供後續操作

> See: ../../diagrams/03-class-domain-model.puml
> See: ../../diagrams/05-er-database-schema.puml

#### Scenario: 草稿需確認才排入發送
- **WHEN** 一筆活動仍為 DRAFT
- **THEN** the system 不對其執行任何觸達掃描或發送
- **AND** 僅在經確認轉為 SCHEDULED 後才排入發送

### Requirement: 優惠規則 schema 驗證與版本演進
The system SHALL validate each campaign type's RuleConfig against a strongly-typed schema before serializing to JSONB, SHALL reject invalid configurations with a reason, and SHALL store a `schema_version` field to support backward-compatible reads via an application-layer upcaster. (FR-002, FR-003, FR-005)

#### Scenario: 合法規則通過驗證後落庫
- **WHEN** 建立活動帶入合法 DiscountRuleConfig / GiftAddonRuleConfig / FlashSaleRuleConfig
- **THEN** the system 通過 schema validation 後序列化存入 JSONB
- **AND** JSONB 含固定欄位 `schema_version`

> See: ../../diagrams/03-class-domain-model.puml

#### Scenario: 不合理規則被拒絕
- **WHEN** 建立/更新活動帶入折扣為負、百分比超過 100%、或結束時間早於開始時間
- **THEN** the system schema validation 失敗
- **AND** 拒絕儲存並回報明確錯誤原因

#### Scenario: 滿額門檻設定
- **WHEN** 設定優惠規則指定「無門檻」或「滿指定金額可用」
- **THEN** the system 依設定保存門檻條件並於後續優惠計算套用

#### Scenario: 舊版 JSONB 向後相容讀取
- **WHEN** 讀取到較舊 `schema_version` 的 rule_config JSONB
- **THEN** 應用層 upcaster 將其轉換至當前 DTO 結構
- **AND** 不需要資料庫 migration

### Requirement: 優惠券三層結構與使用限制
The system SHALL model coupon rules across three tables (coupon_campaign, coupon_code, coupon_redemption), SHALL enforce per-user and total usage limits, and SHALL prevent duplicate redemption of the same code by the same user on the same order. (FR-002, FR-004)

#### Scenario: 設定共用碼與一人一碼
- **WHEN** 設定優惠券活動指定 code_type 為 SHARED_CODE 或 UNIQUE_CODE
- **THEN** the system 以 coupon_campaign 保存 total_usage_limit / per_user_limit / used_count
- **AND** SHARED_CODE 建立一筆 coupon_code，UNIQUE_CODE 建立多筆含 assigned_user_id 與 status

> See: ../../diagrams/05-er-database-schema.puml

#### Scenario: 防同單重複核銷並控總量
- **WHEN** 同一 `(coupon_code_id, user_id, order_id)` 重複核銷
- **THEN** the system 以唯一鍵阻擋第二次核銷
- **AND** used_count 以 atomic update 控制不超過 total_usage_limit

### Requirement: 活動編輯並發控制與稽核
The system SHALL use optimistic locking (version) to prevent concurrent edits from overwriting each other, and SHALL record created_by / updated_by / updated_at audit fields on writes. (FR-001)

#### Scenario: 兩名營運同時編輯
- **WHEN** 兩名營運人員讀取同一活動後先後提交修改
- **THEN** the system 以 version 樂觀鎖讓後提交者失敗並提示重載
- **AND** 成功寫入者記錄 updated_by 與 updated_at

> See: ../../diagrams/05-er-database-schema.puml

### Requirement: 活動生命週期狀態管理
The system SHALL manage campaign status transitions among DRAFT, SCHEDULED, RUNNING, PAUSED, and ENDED, SHALL only allow legal transitions, and SHALL automatically enter RUNNING at start time and ENDED at end time. (FR-011, FR-012)

#### Scenario: 合法狀態切換
- **WHEN** 請求將活動由 DRAFT→SCHEDULED→RUNNING→PAUSED 或 RUNNING→ENDED
- **THEN** the system 接受該轉換並更新狀態

> See: ../../diagrams/02-state-campaign-and-task-lifecycle.puml

#### Scenario: 不合理狀態切換被擋下
- **WHEN** 請求一個不合理的狀態切換（如 ENDED→RUNNING）
- **THEN** the system 拒絕該切換並提示原因

#### Scenario: 起訖時間自動推進
- **WHEN** 到達活動 startAt
- **THEN** the system 自動將活動轉為 RUNNING
- **AND** 到達 endAt 時自動轉為 ENDED

> See: ../../diagrams/02-state-campaign-and-task-lifecycle.puml

### Requirement: 觸達發送設定
The system SHALL allow configuring reach targets (static list, or simple conditions such as membership tier and region), send timing (scheduled or behavior-triggered), and Email as the first channel with extension points reserved for future channels. (FR-007, FR-008, FR-009, FR-010)

#### Scenario: 設定對象與時機
- **WHEN** 行銷人員設定觸達對象為指定名單或簡單條件，並設定發送時機為「排程定時」或「使用者行為觸發」
- **THEN** the system 將設定存入 targetSpec 與 reachPlan
- **AND** 以 Email 為首波通道並在設定上預留其他通道

> See: ../../diagrams/03-class-domain-model.puml

### Requirement: 優惠計算（PromotionEvaluator）
The system SHALL compute the actual promotion (discount amount, gift, add-on, flash price) for a checkout context via a per-type PromotionEvaluator strategy, and SHALL allow adding a new campaign type by adding a new evaluator without modifying existing ones. (FR-002, FR-019)

#### Scenario: 結帳時計算折扣
- **WHEN** 結帳流程帶入 CartContext 與一個 DISCOUNT 活動
- **THEN** the system 由對應的 PromotionEvaluator 算出 PromotionResult

> See: ../../diagrams/03-class-domain-model.puml

#### Scenario: 閃購售罄與已結束邊界
- **WHEN** 閃購活動庫存為 0 或活動已結束
- **THEN** the system 回傳「已售罄」或不適用，而非系統錯誤

#### Scenario: 新增活動類型不動既有程式
- **WHEN** 新增一種活動類型
- **THEN** 僅需新增對應 PromotionEvaluator 並註冊
- **AND** 既有 evaluator 不被修改（OCP）

### Requirement: 觸發判定與發出 ReachRequested
The system SHALL determine via a ReachTriggerEvaluator whether a campaign should trigger reach for a scheduled cycle or a behavior event, and SHALL emit an activity-level `ReachRequested` event (without the full recipient list) to the `reach.requested` topic; both scheduled and event paths SHALL converge to the same topic with consistent downstream tracking. (FR-008)

#### Scenario: 排程批次觸發
- **WHEN** scheduler 掃描到 status=RUNNING 的活動 AND ReachTriggerEvaluator 判定到達發送時機
- **THEN** the system 發出 `ReachRequested(triggerType=SCHEDULED_BATCH, sendCycle)` 至 `reach.requested`
- **AND** 事件不含完整收件人清單

> See: ../../diagrams/01-sequence-reach-flow.puml
> See: ../../diagrams/04-component-architecture.puml

#### Scenario: ShedLock 防同一 cycle 重複觸發
- **WHEN** 多實例部署或 scheduler 重啟補掃同一活動同一排程週期
- **THEN** the system 以 ShedLock 與確定性 `send_cycle_key`（`sched:{campaignId}:{cycleStart}`，truncate 後 ISO-8601）使該週期只發出一筆觸發
- **AND** 不遺漏也不重複

#### Scenario: 行為事件觸發
- **WHEN** `domain.events`（如 CartAbandoned）進入 AND campaign consumer 比對到 RUNNING 活動且 ReachTriggerEvaluator.shouldTrigger 命中
- **THEN** the system 發出 `ReachRequested(triggerType=EVENT, sendCycle=event:{triggerEventId})` 至同一 `reach.requested`
- **AND** 排程與行為兩種觸發的下游追蹤方式一致

> See: ../../diagrams/01-sequence-reach-flow.puml

#### Scenario: 觸發判定例外隔離
- **WHEN** 某活動的 ReachTriggerEvaluator 拋出例外
- **THEN** the system 將該筆判定記為 skipped 並記錄原因
- **AND** 不影響同批其他活動的判定

### Requirement: MVP 範圍界定
The system SHALL fully deliver the discount/coupon campaign end-to-end flow in MVP, and SHALL only reserve extension capability for gift/add-on and flash-sale types without delivering their full functionality. (FR-019)

#### Scenario: 折扣/優惠券全流程交付
- **WHEN** 行銷人員建立並啟用折扣/優惠券活動
- **THEN** the system 支援其從設定、觸發、展開到發送與成效查詢的全流程
- **AND** GIFT_ADDON / FLASH_SALE 僅保留型別與擴充點，不交付完整功能
