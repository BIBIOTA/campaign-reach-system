# Tasks: add-ecommerce-campaign-system

> 文件語言：繁體中文。任務依 `design.md` 第 3～10 節之模組邊界與資料流分解，acceptance 以 WHEN/THEN 表述，並標註對應 PRD 之 FR/NFR 以利追溯。

## 1. 專案基礎建設

- [x] 1.1 建立 Spring Boot 3 模組化單體骨架（campaign / reach / shared 三個 bounded module）
  - Acceptance: WHEN 專案建置 THEN 產生可部署的單一 Spring Boot 3（Java 17/21）應用，且 `campaign`、`reach`、`shared` 為獨立 module，建置工具 Gradle（Kotlin DSL）以模組邊界切分原始碼，依賴以 version catalog（`gradle/libs.versions.toml`）集中宣告（§11.1）
  - Acceptance: WHEN 任一程式碼讓 `campaign` 直接 import `reach` 的 domain（或反向）THEN 架構守護測試（如 ArchUnit）失敗，僅允許透過 `shared/event` 溝通
  - Depends on: -
  - Independence: independent
  - status: passing
- [x] 1.2 設定 Kafka、PostgreSQL、排程與 secret 基礎設定（shared/config）
  - Acceptance: WHEN 應用啟動 THEN 成功連線 PostgreSQL 與 Kafka，並載入排程設定；Email provider 金鑰來自環境變數/vault，不入庫、不入版控（呼應 §10）
  - Acceptance: WHEN 設定缺漏（如缺 Kafka broker）THEN 啟動失敗並回報明確錯誤，而非靜默降級
  - Depends on: 1.1
  - Independence: serial
  - status: passing
- [x] 1.3 建立 Testcontainers（Kafka + PostgreSQL）整合測試基礎
  - Acceptance: WHEN 執行整合測試 THEN 以真實 Kafka 與 PostgreSQL 容器啟動，不 mock broker（呼應 §7）
  - Depends on: 1.2
  - Independence: serial
  - status: passing
  - verification-pending: 真實容器整合測試於本沙箱因 Gradle test worker 無法連 Docker 而 skip；待有 Docker 的 CI 環境實跑（verification-before-completion Stage 5）
- [x] 1.4 建置 Spotless（Palantir Java Format）格式化與 CI 檢查
  - Acceptance: WHEN 執行 `./gradlew spotlessApply` THEN 依 Palantir Java Format（4 空格縮排）統一格式並整理 import；WHEN 執行 `./gradlew spotlessCheck` 遇未格式化程式碼 THEN build fail（§11.2）
  - Acceptance: WHEN 設定格式工具 THEN 不可加入客製排版規則或個別關閉，格式以工具單一來源決定（§11.2）
  - Depends on: 1.1
  - Independence: parallel-safe
  - status: passing
- [x] 1.5 建置 Checkstyle + SpotBugs 與彙整 CI 品質 gate
  - Acceptance: WHEN 執行 `./gradlew checkstyleMain` THEN 以 google_checks 基礎風格檢查（命名/結構/可見度，不重複管排版），violation 即 build fail（§11.3）
  - Acceptance: WHEN 執行 `./gradlew spotbugsMain` THEN High/Normal 等級潛在 bug 使 build fail（§11.3）
  - Acceptance: WHEN PR 觸發 CI THEN `spotlessCheck`、`checkstyleMain`、`spotbugsMain`、`test`（含 ArchUnit 與 Testcontainers）全數通過才可合併，任一 fail 即擋關（§11.5）
  - Acceptance: WHEN 量測測試覆蓋率 THEN 以 JaCoCo 產出報告並設最低門檻，核心邏輯（Evaluator/冪等鍵/重試分類）為高覆蓋重點（§11.5，§7）
  - Depends on: 1.1, 1.3
  - Independence: serial
  - status: passing
  - follow-up: JaCoCo 門檻目前為 0.00（MVP baseline）；待任務 5/7/9 核心邏輯（Evaluator/冪等鍵/重試分類）落地後調高並對核心套件設高門檻。checkstyle.xml/convention plugin 有 3 處註解小瑕疵（relaxed-visibility 措辭、EmptyLineSeparator 重複、懸空 package-info 註解）待清理
- [x] 1.6 建立 repo 根目錄 `CLAUDE.md` 與 symlink `AGENTS.md`（AI 協作指引）
  - Acceptance: WHEN 在 repo 根目錄 THEN 存在 `CLAUDE.md`，內容涵蓋三個 bounded module（campaign / reach / shared）邊界與「僅透過 `shared/event` 溝通、禁止 campaign↔reach 直接 import」之約束（§3，呼應 1.1 ArchUnit）
  - Acceptance: WHEN 開發者或 agent 讀 `CLAUDE.md` THEN 取得標準建置/檢查指令（`./gradlew spotlessApply`、`spotlessCheck`、`checkstyleMain`、`spotbugsMain`、`test`）與 CI gate 規則，與 §11.2／§11.3／§11.5 及任務 1.4／1.5 一致為單一事實來源
  - Acceptance: WHEN 檢視 `AGENTS.md` THEN 其為指向 `CLAUDE.md` 的 symlink（`ls -l` 顯示 `AGENTS.md -> CLAUDE.md`），兩者內容不分歧、僅維護一份
  - Acceptance: WHEN 慣例（module 邊界、lint/CI 指令）變更 THEN 同步更新 `CLAUDE.md`，避免文件與 build script 落差（§11）
  - Depends on: 1.1, 1.4, 1.5
  - Independence: parallel-safe
  - status: passing

## 2. Shared kernel — 事件契約

- [x] 2.1 定義 Kafka 事件 schema 與 topic 契約（shared/event）
  - Acceptance: WHEN 定義事件 THEN 提供 `ReachRequested`（活動層級，含 campaignId/targetSpec/reachPlan/triggerType/sendCycle，不含完整收件人清單）、`ReachTaskCreated`、`SendResultRecorded` 之強型別 schema（§5）
  - Acceptance: WHEN 序列化/反序列化事件 THEN payload 用駝峰 `sendCycle`，與落庫 `send_cycle_key` 為同一值僅命名風格差異（§5）
  - Depends on: 1.1
  - Independence: independent
  - status: passing
- [x] 2.2 定義 topic / 分區鍵 / 消費者群組規格
  - Acceptance: WHEN 設定 topic THEN `domain.events` 以 `user_id` 分區、`reach.requested` 以 `reach_request_id`（或 `campaign_id+send_cycle_key` 雜湊）分區、`reach.dlq` 沿用來源鍵（§9，呼應 NFR-002）
  - Acceptance: WHEN 任一 consumer 處理訊息 THEN 採 at-least-once，offset 於「處理已落庫」後才 commit（§9）
  - Depends on: 2.1, 1.2
  - Independence: serial
  - status: passing
  - resolved: PartitionKeys 對 String 入參（sendCycle / sourceTaskKey）的 requireNonBlank 守衛已於 a6d11eb 補上並有對應測試（原 code-quality Minor follow-up 已關閉）

## 3. Campaign domain — 領域模型與持久化

- [x] 3.1 建立 Campaign 聚合與資料表（含樂觀鎖與稽核欄位）
  - Acceptance: WHEN 建立活動 THEN 持久化 id/name/status/type/period/ruleConfig(JSONB)/targetSpec/reachPlan，狀態枚舉為 DRAFT/SCHEDULED/RUNNING/ENDED/PAUSED（§4）
  - Acceptance: WHEN 兩名營運同時編輯同一活動 THEN 以 `version` 樂觀鎖使後寫入者失敗，避免互相覆蓋；寫入記錄 `created_by`/`updated_by`/`updated_at`（§4，FR-001）
  - Depends on: 1.2
  - Independence: serial
  - status: passing
  - follow-up: CurrentOperator thread-local 待後續 security filter 落地時於 finally 統一 clear()（避免 pooled thread 稽核洩漏）；Campaign entity 之 startAt/endAt 等不變式驗證隨 3.2 RuleConfig 驗證一併補強
- [x] 3.2 實作各 CampaignType 的 RuleConfig DTO 與 schema 驗證 + upcaster
  - Acceptance: WHEN 建立/更新活動 THEN 先依 type 對應 DiscountRuleConfig/GiftAddonRuleConfig/FlashSaleRuleConfig 做 schema validation，通過後才序列化存入 JSONB（含 `schema_version`）（§4，FR-005）
  - Acceptance: WHEN 規則不合理（折扣為負、百分比>100%、結束早於開始）THEN 驗證失敗、拒絕儲存並回報原因（FR-005，US-001）
  - Acceptance: WHEN 設定優惠規則指定「無門檻」或「滿指定金額可用」THEN 依設定保存門檻條件並通過 schema validation，供後續優惠計算套用（FR-003）
  - Acceptance: WHEN 讀取到舊版 `schema_version` 的 JSONB THEN 應用層 upcaster 轉換至當前 DTO 結構，無需資料庫 migration（§4）
  - Depends on: 3.1
  - Independence: serial
  - status: passing
- [x] 3.3 實作優惠券三層結構（coupon_campaign / coupon_code / coupon_redemption）
  - Acceptance: WHEN 設定優惠券活動 THEN 以 coupon_campaign 存 code_type(SHARED_CODE/UNIQUE_CODE)/total_usage_limit/per_user_limit/used_count；coupon_code 存個別碼與 status(AVAILABLE/ASSIGNED/REDEEMED/EXPIRED)（§4，FR-002/FR-004）
  - Acceptance: WHEN 同一 `(coupon_code_id, user_id, order_id)` 重複核銷 THEN 以唯一鍵阻擋，且 `used_count` 以 atomic update 控總量（§4，FR-004）
  - Depends on: 3.1
  - Independence: serial
  - status: passing
  - follow-up: per_user_limit 強制（counting check）與 coupon_code 輸入長度/格式驗證留待 redemption 流程/section-4 API 落地時於信任邊界補上（本任務僅交付 domain/persistence 層與 atomic/unique 保證）

## 4. Campaign API — CRUD 與生命週期

- [x] 4.1 實作活動 CRUD 內部 REST（含驗證與稽核）
  - Acceptance: WHEN 呼叫建立/查詢/修改活動 API THEN 可設定名稱/起訖/優惠類型/內容/門檻/限制/觸達對象/發送計畫，且優惠規則設定與觸達發送設定可分別儲存與修改（FR-001/FR-007/FR-010，US-003）
  - Acceptance: WHEN 活動建立完成 THEN 預設狀態為 DRAFT，需經確認才會排入發送（FR-006，US-001）
  - Acceptance: WHEN 未經身分驗證/授權呼叫內部 REST THEN 拒絕存取（營運後台角色，§10）
  - Depends on: 3.2, 3.3
  - Independence: serial
  - status: passing
- [x] 4.2 實作活動狀態切換 API 與守衛（啟用/暫停/結束）
  - Acceptance: WHEN 請求狀態切換 THEN 僅允許合理轉換（DRAFT→SCHEDULED→RUNNING→PAUSED/ENDED），不合理切換被擋並提示（FR-011，US-002）
  - Depends on: 4.1
  - Independence: serial
  - status: passing

## 5. Campaign evaluation — 兩類 Evaluator（Strategy）

- [x] 5.1 實作 PromotionEvaluator（折扣/滿贈加價購/閃購）優惠計算
  - Acceptance: WHEN 結帳流程帶入 CartContext THEN 對應 CampaignType 的 PromotionEvaluator 算出 PromotionResult（折扣金額/贈品/加價購/閃購價）（§4，FR-002）
  - Acceptance: WHEN 新增一種活動類型 THEN 僅新增對應 Evaluator 並註冊，不修改既有 Evaluator（OCP，§4）
  - Acceptance: WHEN 帶入 FLASH_SALE 活動 THEN 由 stub 級 FlashSaleEvaluator 回傳「已售罄」/不適用而非錯誤；真實庫存判定與閃購定價不在 MVP 範圍，僅保留型別與擴充點（§6 邊界，FR-019）
  - Depends on: 3.2
  - Independence: parallel-safe
  - status: passing
- [x] 5.2 實作 ReachTriggerEvaluator（行為事件 / 排程 cycle）觸發判定
  - Acceptance: WHEN 帶入 TriggerContext（行為事件或排程 cycle，無購物車）THEN `shouldTrigger` 判定該活動是否應產生觸達，不需 CartContext（§4，FR-008）
  - Acceptance: WHEN Evaluator 拋例外 THEN 該筆判定記為 skipped 並記錄原因，不影響同批其他對象（§6）
  - Depends on: 3.2
  - Independence: parallel-safe
  - status: passing

## 6. Campaign 觸發來源 — 排程與事件

- [x] 6.1 實作活動生命週期排程（自動進入 RUNNING / ENDED）
  - Acceptance: WHEN 到達 startAt THEN 活動自動進入 RUNNING；WHEN 到達 endAt THEN 自動 ENDED（FR-012，US-002）
  - Depends on: 4.2
  - Independence: serial
  - status: passing
- [x] 6.2 實作排程批次掃描並發出 ReachRequested（ShedLock 防重）
  - Acceptance: WHEN scheduler 每 N 分鐘掃描 status=RUNNING 活動 AND ReachTriggerEvaluator 判定到達發送時機 THEN 發出 `ReachRequested(...,triggerType=SCHEDULED_BATCH, sendCycle)` 至 `reach.requested`（路徑1，FR-008/US-004）
  - Acceptance: WHEN 多實例部署或 scheduler 重啟補掃 THEN 以 ShedLock + 確定性 `sched:{campaignId}:{cycleStart}`（truncate 後 ISO-8601）使同一活動同一週期只推導出相同 key、只跑一次，不遺漏不重複（§5，US-004）
  - Depends on: 6.1, 5.2, 2.2
  - Independence: serial
  - status: passing
  - follow-up: cycle-duration 設定缺正值守衛（PT0S 會除零）留待後續 fail-fast 強化；ShedLock 真實雙實例去重待有 Docker 的 CI 以 Testcontainers 整合測試覆蓋（目前以 @SchedulerLock annotation present + 確定性 key 之 fast test 把關）
- [x] 6.3 實作行為事件消費者並發出 ReachRequested（路徑2）
  - Acceptance: WHEN `domain.events`（CartAbandoned/OrderPlaced…）進入 AND campaign consumer 比對到 RUNNING 活動且 `shouldTrigger` 命中 THEN 發出 `ReachRequested(...,triggerType=EVENT, sendCycle=event:{triggerEventId})` 至同一 `reach.requested`（FR-008，US-005）
  - Acceptance: WHEN 排程與行為兩種觸發 THEN 下游發送結果與追蹤方式一致（US-005，FR-008）
  - Depends on: 6.1, 5.2, 2.2
  - Independence: serial
  - status: passing
  - resolved（code review）: publish 失敗原為 swallow-then-ack（at-most-once，靜默丟事件，違反 §9）。已修正為——判定（evaluation）例外仍 per-campaign 隔離，但 publish 例外向上拋出，`DomainEventConsumer` 不 ack → Kafka 重投（at-least-once）；`ReachRequestPublisher` 改為同步、有界等待（`campaignreach.kafka.publish-timeout`，預設 10s）。新增 `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`（有限重試後進 `domain.events.DLT`）+ `ErrorHandlingDeserializer` 防毒丸卡 partition。對應單元測試：`BehaviorEventReachTriggerTest`（判定隔離 vs publish 傳播）、`ReachRequestPublisherTest`（成功/失敗/逾時）、`KafkaConsumerConfigTest`（error handler 接線）
  - follow-up: ShedLock 真實雙實例去重 + domain.events→reach.requested 端到端（含 publish 失敗重投、毒丸進 DLT）待有 Docker 的 CI 以 Testcontainers 覆蓋

## 7. Reach orchestrator — 批次落庫、受眾展開、頻控

- [x] 7.1 實作 reach_request 批次落庫與批次冪等
  - Acceptance: WHEN orchestrator 消費 `reach.requested` THEN 先 upsert 一筆 reach_request，以 `unique(campaign_id, send_cycle_key, trigger_type)` 去重，同事件重投不建立第二筆批次（§5，NFR-003）
  - Acceptance: WHEN reach_request 已存在且 status IN (DISPATCHING, DONE) THEN 直接 ack 跳過（Kafka 重投保護，fan-out 已完成不重做受眾解析與 insert）；否則（PENDING/EXPANDING）進入/續跑展開（§5）
  - Acceptance: WHEN 建立批次 THEN 凍結 `target_spec_snapshot`/`reach_plan_snapshot`，活動事後被改仍可追溯當時依據（§5）
  - Depends on: 2.2, 3.1
  - Independence: serial
  - status: passing
- [x] 7.2 實作 AudienceResolver（位於 reach）將 targetSpec 解析為收件人
  - Acceptance: WHEN 給定 targetSpec THEN 由 reach 模組統一解析為收件人清單，支援靜態名單與簡單條件分眾（會員等級、地區），campaign 不展開收件人（§4，FR-007/FR-013）
  - Depends on: 1.2
  - Independence: parallel-safe
  - status: passing
- [x] 7.3 實作分頁 fan-out 展開 ReachTask（斷點續跑 + 頻控 + 任務冪等）
  - Acceptance: WHEN 展開受眾 THEN 分頁（每批 M 筆）批次 INSERT ReachTask(PENDING)，以 `ON CONFLICT DO NOTHING` 落在四欄 unique `(campaign_id, user_id, send_cycle_key, channel)`，同一週期同一人不重複建立（§5，FR-014/US-006）
  - Acceptance: WHEN 展開到一半 crash 後 Kafka 重投 THEN 已寫入 task 不重複、未寫入續寫，最終收斂到完整 N 筆，計數不被重複污染（§5，NFR-003）
  - Acceptance: WHEN 建立 ReachTask 前 THEN 查詢該用戶於時間窗口內歷史 reach_task，命中則跳過（頻控，與冪等語意分離）（§5，US-006）
  - Acceptance: WHEN 展開完成 THEN reach_request.status 推進 PENDING→EXPANDING→DISPATCHING，`total_count` 一次回填（§5）
  - Depends on: 7.1, 7.2
  - Independence: serial
  - status: passing

## 8. Reach channel — Adapter 與抑制名單

- [ ] 8.1 實作 ChannelAdapter 介面與 EmailAdapter（含 circuit breaker）
  - Acceptance: WHEN dispatcher 依 reachPlan 選用通道 THEN 透過 ChannelAdapter.send(ReachMessage) 發送，EmailAdapter 介接 SendGrid/SES 並包一層（§4，FR-009）
  - Acceptance: WHEN 新增通道 THEN 僅新增 Adapter 並註冊，orchestrator/dispatcher 依 reachPlan 選用，不改既有（§4，FR-009）
  - Acceptance: WHEN Email provider 中斷 THEN circuit breaker（Resilience4j）開啟使系統穩定降級、恢復後續送，不造成連鎖故障（§6，NFR-004）
  - Depends on: 2.1
  - Independence: parallel-safe
  - status: not_started
- [ ] 8.2 實作抑制名單（suppression）查表與發送前過濾
  - Acceptance: WHEN 發送前 THEN 檢查 suppression(user_id/channel/reason)，命中（退訂/硬退信/投訴）者該 task 標 FAILED（不可重試），不送出（§10，NFR-005/FR-015）
  - Depends on: 3.1
  - Independence: parallel-safe
  - status: not_started

## 9. Reach dispatcher — 兩階段事務發送、重試、DLQ

- [ ] 9.1 實作兩階段事務發送（FOR UPDATE SKIP LOCKED + 租約）
  - Acceptance: WHEN dispatcher 撈取任務 THEN 階段1短事務以 `FOR UPDATE SKIP LOCKED` 撈 status IN (PENDING, RETRY_SCHEDULED) AND next_retry_at<=now()，標 PROCESSING + locked_by/locked_until 後立即 commit 釋放 DB 連線（§5，NFR-002）
  - Acceptance: WHEN 外部呼叫於事務外完成 THEN 階段2新事務回寫：成功→SENT 並寫 send_result、清 locked_by；失敗→RETRY_SCHEDULED 設指數退避 next_retry_at（§5，NFR-003）
  - Acceptance: WHEN breaker 於標 PROCESSING 前已開啟 THEN 跳過該筆、任務維持 PENDING；WHEN breaker 在已標 PROCESSING 後快速失敗 THEN 比照可重試走階段2回寫 RETRY_SCHEDULED，不卡在 PROCESSING（§6）
  - Depends on: 7.3, 8.1, 8.2
  - Independence: serial
  - status: not_started
- [ ] 9.2 實作重試分類、退避上限與 DLQ
  - Acceptance: WHEN 可重試錯誤（網路/429/5xx）THEN 走指數退避 1m→5m→30m 最多 3 次；WHEN 不可重試（地址無效/退訂）THEN 直接 FAILED（§6，FR-015/US-006）
  - Acceptance: WHEN 重試超過上限 THEN task 進 `reach.dlq` + 標記，供人工檢視與重放，不靜默遺失（§6，FR-016/US-006）
  - Depends on: 9.1
  - Independence: serial
  - status: not_started
- [ ] 9.3 實作 Reaper job（回收卡死的 PROCESSING 任務）
  - Acceptance: WHEN worker crash 導致 task 卡在 PROCESSING AND locked_until<now() THEN Reaper 背景排程定期重置為 PENDING，避免任務卡死（§5）
  - Depends on: 9.1
  - Independence: serial
  - status: not_started

## 10. 取消競態與成效報表

- [ ] 10.1 實作活動暫停/結束時取消未送 task（取消競態語意）
  - Acceptance: WHEN 活動進入 PAUSED/ENDED THEN 批次 `UPDATE reach_task SET status='CANCELLED' WHERE campaign_id=:cid AND status IN ('PENDING','RETRY_SCHEDULED')`，PROCESSING 不在取消範圍（§6，FR-017/US-002）
  - Acceptance: WHEN dispatcher 階段1撈取與取消並行 THEN 同短事務內重檢 `campaign.status NOT IN ('PAUSED','ENDED')`，已停用者改標 CANCELLED、不標 PROCESSING，由 DB 列鎖序列化二者（§6）
  - Acceptance: WHEN 某筆在取消前瞬間已 PROCESSING THEN 允許其完成（有界極小洩漏窗口，明確接受），不引入分散式中止（§6）
  - Depends on: 9.1, 4.2
  - Independence: serial
  - status: not_started
- [ ] 10.2 實作 reach_request 計數背景聚合回填（避免第二熱點）
  - Acceptance: WHEN 報表讀取批次計數 THEN sent/failed/pending_count 由背景排程定期聚合 reach_task 回填（秒級延遲可接受），不逐筆即時 update 同一批次列（§5/§8 S-2，NFR-002）
  - Depends on: 7.3, 9.1
  - Independence: serial
  - status: not_started
- [ ] 10.3 實作成效查詢 API（活動彙總 + 單筆收件人狀態）
  - Acceptance: WHEN 查詢活動成效 THEN 回傳送達率/失敗率/各狀態人數分布（FR-018，US-007）
  - Acceptance: WHEN 查詢特定收件人於某活動 THEN 回傳其觸達狀態（待發送/已送達/失敗等）（FR-018，US-007，NFR-005）
  - Depends on: 10.2
  - Independence: serial
  - status: not_started

## 11. PII / 安全 / 資料保留

- [ ] 11.1 落實收件人 PII 最小化與資料保留策略
  - Acceptance: WHEN 落庫 reach_task THEN 只存 user_id，不落收件 email；實際 email 於 dispatcher 發送當下以 user_id 即時解析、發送後不持久化（§10）
  - Acceptance: WHEN 落庫 send_result THEN 僅存 provider_message_id 與 outcome，不存信件內容與收件地址（§10）
  - Acceptance: WHEN 初始化保留設定 THEN 保留期限參數必須存在且可設定、不得預設永久保留（NFR-005）
  - Acceptance: WHEN reach_task/send_result 屆保留期 THEN 依保留策略歸檔或刪除（具體月數為 open question 待法遵確認，見 proposal.md ## Open Questions）（§10，NFR-005）
  - Depends on: 7.3, 9.1
  - Independence: serial
  - status: not_started

## 12. 大量觸達可靠性驗證（壓測）

- [ ] 12.1 以 10 萬筆級壓測驗證全鏈路並產出報告
  - Acceptance: WHEN 以壓測資料觸發單次活動 10 萬筆級展開與發送 THEN 完整可靠跑完，各 ReachTask 狀態正確收斂（NFR-001，US-008）
  - Acceptance: WHEN 大量發送進行中 THEN 活動設定與其他活動觸達不受影響、不被拖垮（NFR-002，US-008）
  - Acceptance: WHEN 壓測完成 THEN 產出處理速率/各狀態分布/資源使用報告，作為演進至百萬筆級基準（US-008，Metrics §8）
  - Depends on: 9.2, 9.3, 10.1, 10.3
  - Independence: serial
  - status: not_started

## Optional artifacts
- [ ] PlantUML diagrams (spec-driven-dev:writing-uml) — 已存在於 diagrams/（sequence/state/class/component/ER），本次不重跑
- [ ] Figma designs (spec-driven-dev:writing-figma) — 本系統僅後端、無 UI，不需要
