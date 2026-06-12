# reach Specification

## Purpose
TBD - created by archiving change add-ecommerce-campaign-system. Update Purpose after archive.
## Requirements
### Requirement: 觸達批次落庫與 fan-out 冪等
The system SHALL, upon consuming a `ReachRequested` event, first upsert a single activity-level `reach_request` record deduplicated by `unique(campaign_id, send_cycle_key, trigger_type)`, freeze the target_spec / reach_plan snapshots, and SHALL skip re-expansion when the batch is already in DISPATCHING or DONE (fan-out completed, total_count backfilled) so that Kafka at-least-once redelivery does not re-resolve the audience or re-run inserts; only PENDING/EXPANDING batches are resumed. (FR-013, NFR-003)

#### Scenario: 同事件重投只建一筆批次
- **WHEN** orchestrator 消費 `reach.requested` 且同一 `(campaign_id, send_cycle_key, trigger_type)` 已存在
- **THEN** the system 不建立第二筆 reach_request
- **AND** 計數不被重複污染

> See: ../../diagrams/01-sequence-reach-flow.puml
> See: ../../diagrams/05-er-database-schema.puml

#### Scenario: 已展開完成的批次直接跳過
- **WHEN** 對應 reach_request 已存在且 status IN (DISPATCHING, DONE)
- **THEN** the system 直接 ack 並跳過展開（fan-out 已完成、total_count 已回填，避免重投時重做受眾解析與 insert）
- **AND** 僅 PENDING/EXPANDING 批次進入/續跑展開

#### Scenario: 凍結快照以利追溯
- **WHEN** 建立 reach_request 批次
- **THEN** the system 凍結 target_spec_snapshot 與 reach_plan_snapshot
- **AND** 活動事後被修改仍可追溯當時的發送依據

> See: ../../diagrams/05-er-database-schema.puml

### Requirement: 受眾解析與分頁展開為 ReachTask
The system SHALL resolve `targetSpec` into a recipient list within the reach module via `AudienceResolver`, and SHALL expand one `reach_request` into N `ReachTask(PENDING)` rows in pages with `ON CONFLICT DO NOTHING` on `unique(campaign_id, user_id, send_cycle_key, channel)`, supporting crash-resumable fan-out. (FR-013, FR-014)

#### Scenario: 受眾一律由 reach 解析
- **WHEN** orchestrator 取得活動層級的 targetSpec
- **THEN** the system 由 reach 模組的 AudienceResolver 解析為收件人清單（支援靜態名單與會員等級/地區條件）
- **AND** campaign 模組不展開收件人

> See: ../../diagrams/03-class-domain-model.puml

#### Scenario: 一筆請求展開成 N 筆任務
- **WHEN** 受眾清單有 N 位收件人
- **THEN** the system 分頁批次 INSERT N 筆 ReachTask(PENDING)
- **AND** 展開完成後將 reach_request.status 由 EXPANDING 推進至 DISPATCHING 並一次回填 total_count

> See: ../../diagrams/01-sequence-reach-flow.puml

#### Scenario: 斷點續跑
- **WHEN** 展開到一半 crash 後 Kafka 重投同一事件
- **THEN** the system 對已寫入的 ReachTask 以 ON CONFLICT DO NOTHING 不重複建立
- **AND** 續寫未完成部分，最終收斂到完整 N 筆

### Requirement: 同週期不重複發送（冪等與頻控）
The system SHALL prevent sending more than once to the same user in the same send cycle for the same campaign via the four-column unique constraint (idempotency), and SHALL additionally skip creating a task when the user has already been reached **by the same campaign in a different send cycle** within a configured time window (frequency capping is same-campaign scoped, not a cross-campaign global cap). (FR-014, NFR-003)

#### Scenario: 同一人同週期只建立一筆任務
- **WHEN** 同一活動同一 send_cycle_key 對同一 user 同一 channel 重複展開
- **THEN** the system 以四欄 unique constraint 阻擋，只保留一筆 ReachTask

> See: ../../diagrams/05-er-database-schema.puml

#### Scenario: 短時間頻控跳過
- **WHEN** 建立 ReachTask 前查詢到該 user 在**同一活動**、不同 send_cycle_key、指定時間窗口內已有歷史 reach_task
- **THEN** the system 跳過建立該筆，避免同一活動不同事件在短時間對同人重複觸達（範圍為同活動，當前週期由四欄 unique constraint 負責，不納入頻控比對）

### Requirement: 可靠發送與重試
The system SHALL dispatch each `ReachTask` via the matching `ChannelAdapter` using a two-phase short-transaction model (claim with `FOR UPDATE SKIP LOCKED` + lease, external call outside the transaction, then write back), SHALL retry transient failures with exponential backoff up to a bounded number of attempts, and SHALL mark non-retryable failures as FAILED. (FR-015, NFR-002, NFR-003)

#### Scenario: 撈取並標記 PROCESSING（短事務）
- **WHEN** dispatcher 階段一撈取 status IN (PENDING, RETRY_SCHEDULED) AND next_retry_at <= now()
- **THEN** the system 以 `FOR UPDATE SKIP LOCKED` 撈取、標記 PROCESSING 並寫入 locked_by / locked_until 後立即 commit 釋放 DB 連線

> See: ../../diagrams/01-sequence-reach-flow.puml
> See: ../../diagrams/02-state-campaign-and-task-lifecycle.puml

#### Scenario: 可重試失敗走指數退避
- **WHEN** 外部 Email 呼叫回傳可重試錯誤（網路 / 429 / 5xx / timeout）
- **THEN** the system 在階段二將任務標為 RETRY_SCHEDULED 並設指數退避 next_retry_at（1m→5m→30m，最多 3 次）

#### Scenario: 不可重試直接失敗
- **WHEN** 外部呼叫回傳不可重試原因（地址無效 / 退訂）
- **THEN** the system 將任務標為 FAILED 且不再重試

#### Scenario: 回收卡死任務
- **WHEN** worker crash 導致任務停留 PROCESSING AND locked_until < now()
- **THEN** Reaper 背景排程定期將其重置為 PENDING

### Requirement: 失敗保留與 DLQ
The system SHALL move tasks that exhaust retries into a dead-letter topic with a marker so they are preserved for human inspection and replay, and SHALL NOT silently drop them. (FR-016)

#### Scenario: 重試耗盡進 DLQ
- **WHEN** 一筆任務重試超過上限
- **THEN** the system 將其送入 `reach.dlq` 並標記
- **AND** 保留供人工檢視與重放，不靜默遺失

> See: ../../diagrams/02-state-campaign-and-task-lifecycle.puml

### Requirement: 活動暫停/結束取消未送任務
The system SHALL cancel not-yet-sent tasks (status IN PENDING, RETRY_SCHEDULED) when a campaign enters PAUSED or ENDED, SHALL serialize cancellation against dispatcher claiming via a re-check of campaign status inside the claim transaction, and SHALL allow already-PROCESSING tasks to complete (a bounded, accepted leakage window). (FR-017)

#### Scenario: 取消尚未發送的任務
- **WHEN** 活動進入 PAUSED 或 ENDED
- **THEN** the system 將 status IN (PENDING, RETRY_SCHEDULED) 的 reach_task 批次標為 CANCELLED
- **AND** PROCESSING 不在取消範圍

> See: ../../diagrams/02-state-campaign-and-task-lifecycle.puml

#### Scenario: 取消與撈取序列化
- **WHEN** dispatcher 在取消同時撈取任務
- **THEN** the system 於標記 PROCESSING 的同一短事務內重檢 `campaign.status NOT IN (PAUSED, ENDED)`
- **AND** 已停用者改標 CANCELLED、不標 PROCESSING，由 DB 列鎖序列化二者

#### Scenario: 已 PROCESSING 者放行
- **WHEN** 某筆任務在取消發生前的瞬間已被標為 PROCESSING
- **THEN** the system 允許其完成，不強制中止（有界且極小的洩漏窗口，明確接受）

### Requirement: 外部通道中斷的穩定降級
The system SHALL wrap the EmailAdapter with a circuit breaker so that when the external Email provider is unavailable the system degrades stably and resumes after recovery without cascading failure. The breaker SHALL open on a measurable, configurable failure threshold, stay open for a configurable cool-down, and recover via half-open probing, so that recovery behaviour is verifiable rather than descriptive. (NFR-004)

#### Scenario: 失敗率達門檻時 breaker 開啟
- **WHEN** 於滑動窗口（預設最近 20 次呼叫）內失敗率達到可設定門檻（預設 ≥ 50%）
- **THEN** the system 在 1 秒內將 breaker 轉為 OPEN
- **AND** 上述窗口大小、失敗率門檻、最小取樣數皆可由設定調整

#### Scenario: 冷卻後以 half-open 探測恢復
- **WHEN** breaker 進入 OPEN 並經過可設定冷卻時間（預設 30 秒）
- **THEN** the system 轉為 HALF_OPEN 並放行可設定筆數（預設 5 筆）作為探測
- **AND** 探測全數成功才轉回 CLOSED、恢復正常發送；任一探測失敗則重新回到 OPEN 並重啟冷卻

#### Scenario: breaker 開啟時不卡任務
- **WHEN** circuit breaker 於 dispatcher 標記 PROCESSING 前已開啟
- **THEN** the system 跳過該筆、任務維持 PENDING
- **AND** 恢復後由 dispatcher 重掃發送

> See: ../../diagrams/04-component-architecture.puml

#### Scenario: 已 PROCESSING 後 breaker 失敗
- **WHEN** breaker 在任務已標 PROCESSING 後於外部呼叫快速失敗
- **THEN** the system 比照可重試失敗走階段二回寫 RETRY_SCHEDULED，不卡在 PROCESSING

### Requirement: 觸達成效查詢
The system SHALL provide campaign-level reach metrics (delivered rate, failed rate, status distribution) and per-recipient status lookup, with batch counts maintained by periodic background aggregation rather than per-task inline updates. (FR-018, NFR-002, NFR-005)

#### Scenario: 活動維度彙總
- **WHEN** 查詢單一活動的觸達成效
- **THEN** the system 回傳送達率、失敗率與各狀態人數分布

> See: ../../diagrams/05-er-database-schema.puml

#### Scenario: 單筆收件人狀態
- **WHEN** 查詢特定收件人於某活動的觸達狀態
- **THEN** the system 回傳其狀態（待發送 / 已送達 / 失敗等）

#### Scenario: 計數背景聚合避免熱點
- **WHEN** 大量 ReachTask 狀態變更
- **THEN** the system 以背景排程定期聚合 reach_task 回填 sent/failed/pending_count（秒級延遲可接受）
- **AND** 不對同一 reach_request 列逐筆即時 update

### Requirement: 大量觸達可靠性與互不影響
The system SHALL reliably complete a single campaign's 100k-scale fan-out and dispatch, SHALL NOT block campaign configuration or other campaigns' reach during heavy sending, and SHALL produce a load-test report as a baseline for evolving toward million-scale. (NFR-001, NFR-002)

#### Scenario: 10 萬筆級全鏈路可靠跑完
- **WHEN** 以壓測資料觸發單次活動 10 萬筆級展開與發送
- **THEN** the system 完整可靠跑完，各 ReachTask 狀態正確收斂
- **AND** 產出處理速率 / 各狀態分布 / 資源使用報告

> See: ../../diagrams/01-sequence-reach-flow.puml

#### Scenario: 大量發送不拖垮其他活動
- **WHEN** 某大型活動正在大量發送
- **THEN** the system 透過非同步處理與分區策略，使活動設定與其他活動觸達不受影響、不被拖垮

### Requirement: 收件人 PII 最小化與抑制名單
The system SHALL store only `user_id` on `reach_task` (resolving the actual email at send time and not persisting it), SHALL store only provider_message_id and outcome on `send_result`, SHALL check a suppression list before sending and mark hits as FAILED, and SHALL apply a configurable data-retention policy to reach audit trails. The retention period SHALL exist and be configurable; the exact duration is an open question pending legal/compliance confirmation (see proposal.md ## Open Questions). (NFR-005, FR-015)

#### Scenario: 不落收件 PII
- **WHEN** 建立 reach_task 與寫入 send_result
- **THEN** the system 於 reach_task 只存 user_id、不落收件 email；send_result 僅存 provider_message_id 與 outcome
- **AND** 實際 email 於 dispatcher 發送當下以 user_id 即時解析、發送後不持久化

> See: ../../diagrams/05-er-database-schema.puml

#### Scenario: 發送前抑制名單過濾 (FR-015)
- **WHEN** 發送前命中 suppression（退訂 / 硬退信 / 投訴）
- **THEN** the system 將該 task 標為 FAILED（不可重試原因）且不送出

#### Scenario: 保留策略存在且可設定
- **WHEN** 系統初始化資料保留設定
- **THEN** the system 必須提供一個可設定的保留期限參數（驗收以「參數存在且可調整」為準）
- **AND** 未設定時不得預設為「永久保留」

#### Scenario: 觸達稽核軌跡屆期歸檔或刪除
- **WHEN** reach_task / send_result 屆滿設定的保留期限
- **THEN** the system 依資料保留策略歸檔或刪除（具體月數為 open question，待法遵確認，見 proposal.md ## Open Questions）

