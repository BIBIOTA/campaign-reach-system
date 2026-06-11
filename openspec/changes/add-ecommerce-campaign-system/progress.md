# Progress: add-ecommerce-campaign-system

> SDD（subagent-driven-development）執行紀錄。每次狀態轉換 append 一個 Session block。

## Session 1 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.1 建立 Spring Boot 3 模組化單體骨架（campaign / reach / shared 三個 bounded module）
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 建立 Gradle Kotlin DSL 多模組骨架 + version catalog + ArchUnit 邊界守護測試。

## Session 2 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.1 建立 Spring Boot 3 模組化單體骨架（campaign / reach / shared 三個 bounded module）
- Transition: in_progress → passing
- Evidence:
  - Commits: c1e17a7 feat(foundation): scaffold Spring Boot 3 modular monolith with ArchUnit boundary guard
  - Tests: `./gradlew build` BUILD SUCCESSFUL；`:app:test` 2 ArchUnit tests pass；spec-reviewer 注入違規 import 驗證守護測試會真實 fail（已還原）
  - Spec-reviewer: ✅ Spec compliant（5/5 checks pass）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important，僅 3 項 Minor）
- Next action: dispatch implementer subagent for task 1.2（Kafka/PostgreSQL/排程/secret 基礎設定，shared/config）。

## Session 3 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.2 設定 Kafka、PostgreSQL、排程與 secret 基礎設定（shared/config）
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 設定 shared/config 的 Kafka/PostgreSQL/排程連線與環境變數金鑰載入，含缺漏即啟動失敗（fail-fast）。

## Session 4 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.2 設定 Kafka、PostgreSQL、排程與 secret 基礎設定（shared/config）
- Transition: in_progress → passing
- Evidence:
  - Commits: 8e2d288 feat(shared): add base Kafka/PostgreSQL/scheduling/secret config (task 1.2)
  - Tests: `./gradlew build`/`test` BUILD SUCCESSFUL；:shared:test 7 tests green（含 startupFailsWhenKafkaBrokerMissing/DatasourceUrlMissing/EmailApiKeyMissing），:app:test ArchUnit green
  - Spec-reviewer: ✅ Spec compliant（5/5）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important，僅 cosmetic Minor）
- Next action: dispatch implementer subagent for task 1.3（Testcontainers Kafka + PostgreSQL 整合測試基礎）。

## Session 5 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.3 建立 Testcontainers（Kafka + PostgreSQL）整合測試基礎
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 建立以真實 Kafka + PostgreSQL 容器啟動的整合測試基礎設施（不 mock broker）。

## Session 6 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.3 建立 Testcontainers（Kafka + PostgreSQL）整合測試基礎
- Transition: in_progress → passing
- Evidence:
  - Commits: b57de93 test(app): add Testcontainers Kafka+PostgreSQL integration test foundation (task 1.3)
  - Tests: `./gradlew build` BUILD SUCCESSFUL；整合冒煙測試（integrationContextStartsWithRealKafkaAndPostgres / roundTripsAMessageThroughTheRealBroker）使用真實 PostgreSQLContainer+KafkaContainer（無 @EmbeddedKafka/H2）；本沙箱 test worker 無法連 Docker → 2 tests skipped（非 fail），ArchUnit green
  - Spec-reviewer: ✅ Spec compliant（5/5，標記 live container 為 verification-pending）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important，3 項 Minor）
- Next action: dispatch implementer subagent for task 1.4（Spotless + Palantir Java Format）。
- Verification-pending: 真實容器整合測試待有 Docker 的 CI 環境實跑（verification-before-completion Stage 5）。

## Session 7 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.4 建置 Spotless（Palantir Java Format）格式化與 CI 檢查
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 以 Spotless 掛載 Palantir Java Format（spotlessApply/spotlessCheck），無客製規則。

## Session 8 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.4 建置 Spotless（Palantir Java Format）格式化與 CI 檢查
- Transition: in_progress → passing
- Evidence:
  - Commits: 52c09bf build: add Spotless with Palantir Java Format (task 1.4)
  - Tests: `./gradlew spotlessCheck` BUILD SUCCESSFUL；負向驗證（弄亂格式 → spotlessCheck BUILD FAILED → 還原）通過；`./gradlew build` 含 ArchUnit green；11 個既有檔案僅格式化、無邏輯改動
  - Spec-reviewer: ✅ Spec compliant（5/5）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important，1 項 Minor）
- Next action: dispatch implementer subagent for task 1.5（Checkstyle + SpotBugs + JaCoCo + 彙整 CI 品質 gate）。

## Session 9 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.5 建置 Checkstyle + SpotBugs 與彙整 CI 品質 gate
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 設定 Checkstyle(google_checks)、SpotBugs(High/Normal fail)、JaCoCo 門檻與 CI workflow 彙整 gate（spotlessCheck/checkstyleMain/spotbugsMain/test）。

## Session 10 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.5 建置 Checkstyle + SpotBugs 與彙整 CI 品質 gate
- Transition: in_progress → passing
- Evidence:
  - Commits: 78ee757 build: add Checkstyle + SpotBugs + JaCoCo + CI quality gate (task 1.5)
  - Tests: `./gradlew checkstyleMain spotbugsMain check` BUILD SUCCESSFUL；spec-reviewer 注入命名違規→checkstyleMain FAILED、注入 null-deref→spotbugsMain FAILED 皆驗證（已還原）；.github/workflows/ci.yml 跑 `./gradlew check`（JDK 21 + Gradle cache）
  - Spec-reviewer: ✅ Spec compliant（5/5；JaCoCo 0.00 接受為 MVP baseline，列 follow-up）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important，3 項 Minor 註解瑕疵）
- Next action: dispatch implementer subagent for task 1.6（CLAUDE.md + AGENTS.md symlink）。
- Follow-up: JaCoCo 門檻待任務 5/7/9 調高；清理 checkstyle.xml/convention plugin 3 處註解瑕疵。

## Session 11 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.6 建立 repo 根目錄 CLAUDE.md 與 symlink AGENTS.md（AI 協作指引）
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 撰寫根目錄 CLAUDE.md（模組邊界 + 建置/lint/CI 指令單一事實來源）並建立 AGENTS.md → CLAUDE.md symlink。

## Session 12 — 2026-06-10 00:00
- Stage: SDD
- Task: 1.6 建立 repo 根目錄 CLAUDE.md 與 symlink AGENTS.md（AI 協作指引）
- Transition: in_progress → passing
- Evidence:
  - Commits: 96b0a04 docs: add root CLAUDE.md and AGENTS.md symlink (task 1.6)
  - Tests: `ls -l AGENTS.md` → `AGENTS.md -> CLAUDE.md`；`git ls-files -s AGENTS.md` mode 120000；`diff CLAUDE.md AGENTS.md` 無差異；CLAUDE.md 指令/CI/模組結構逐項對齊 convention plugin 與 ci.yml（無漂移）
  - Spec-reviewer: ✅ Spec compliant（5/5，4 個 Scenario 全涵蓋）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important，3 項 Minor polish）
- Next action: Section 1（1.1–1.6）全數 passing；執行 final pass（`./gradlew check` + `openspec validate --strict`）後 invoke spec-driven-dev:verification-before-completion。

## Session 13 — 2026-06-10 14:40
- Stage: SDD
- Task: 2.1 定義 Kafka 事件 schema 與 topic 契約（shared/event）
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 於 shared/event 定義 ReachRequested / ReachTaskCreated / SendResultRecorded 強型別 schema（駝峰 sendCycle，對齊落庫 send_cycle_key）。

## Session 14 — 2026-06-10 14:55
- Stage: SDD
- Task: 2.1 定義 Kafka 事件 schema 與 topic 契約（shared/event）
- Transition: in_progress → passing
- Evidence:
  - Commits: df2222a feat(shared): add Kafka event schema contracts (task 2.1)
  - Tests: EventSchemaContractTest 6 tests green；`./gradlew :shared:test spotlessCheck checkstyleMain spotbugsMain`、`:shared:check`、`:app:test`（ArchUnit ModuleBoundaryTest）全數 BUILD SUCCESSFUL；wire 欄位斷言 camelCase `sendCycle`、ReachRequested 無收件人清單、triggerType=SCHEDULED_BATCH/EVENT
  - Spec-reviewer: ✅ Spec compliant（5/5；event 名稱/欄位對齊 01-sequence 與 05-er，無 2.2 wiring 外洩）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；1 項 Minor：outcome 仍為 String vs Channel enum 之非對稱，列為 future follow-up）
- Next action: dispatch implementer subagent for task 2.2（topic / 分區鍵 / 消費者群組規格 + at-least-once offset 語意）。

## Session 15 — 2026-06-10 15:00
- Stage: SDD
- Task: 2.2 定義 topic / 分區鍵 / 消費者群組規格
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 定義三 topic（domain.events/reach.requested/reach.dlq）名稱、分區鍵推導（user_id / reach_request_id 或 campaign_id+send_cycle_key 雜湊 / 沿用來源鍵）、消費者群組與 at-least-once（enable-auto-commit=false + 手動 ack）容器設定。

## Session 16 — 2026-06-10 15:20
- Stage: SDD
- Task: 2.2 定義 topic / 分區鍵 / 消費者群組規格
- Transition: in_progress → passing
- Evidence:
  - Commits: 314efff feat(shared): define Kafka topic/partition/consumer-group contract and at-least-once policy (task 2.2)
  - Tests: KafkaTopicContractTest 8 + KafkaConsumerConfigTest 2 green；`./gradlew :shared:test spotlessCheck checkstyleMain spotbugsMain`、`:app:test`（ArchUnit）BUILD SUCCESSFUL；斷言三 topic 名稱/兩 group id、分區鍵確定性、reach.requested key 非裸 campaignId（NFR-002）、enable.auto.commit=false + AckMode.MANUAL_IMMEDIATE
  - Spec-reviewer: ✅ Spec compliant（5/5；無 listener/producer/admin 外洩）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；1 項 Minor：PartitionKeys 未擋空字串，列 follow-up）
- Next action: Section 2（2.1–2.2）全數 passing；執行 final pass（`./gradlew check` + `openspec validate add-ecommerce-campaign-system --strict`）後 invoke spec-driven-dev:verification-before-completion。

## Session 17 — 2026-06-10 16:00
- Stage: SDD
- Task: 3.1 建立 Campaign 聚合與資料表（含樂觀鎖與稽核欄位）
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 於 campaign/domain 建立 Campaign JPA 聚合（id/name/type/status/period/ruleConfig JSONB/targetSpec/reachPlan + @Version 樂觀鎖 + created_by/updated_by/updated_at 稽核），並以 Flyway migration 建立 campaign 表（對齊 05-er-database-schema.puml）。

## Session 18 — 2026-06-10 16:40
- Stage: SDD
- Task: 3.1 建立 Campaign 聚合與資料表（含樂觀鎖與稽核欄位）
- Transition: in_progress → passing
- Evidence:
  - Commits: 7330e78 feat(campaign): add Campaign aggregate, JPA persistence and Flyway schema (task 3.1)；27d7573 docs(campaign): note CurrentOperator thread-local leak-safety contract (task 3.1 review)
  - Tests: `:campaign:spotlessCheck checkstyleMain spotbugsMain check` PASS、CampaignEnumContractTest 3 fast tests green、`:app:test` ArchUnit green；CampaignPersistenceIntegrationTest（真實 Postgres Testcontainers，涵蓋 stale-version OptimisticLock 失敗、updated_by/updated_at 稽核、5 status+3 type enum round-trip）4 tests 於本沙箱無 Docker → SKIPPED（非 fail），待 CI 實跑
  - Spec-reviewer: ✅ Spec compliant（5/5；V1__campaign.sql 與 entity 欄位/enum 對齊 05-er，未提前實作 3.2/3.3/section-4）
  - Code-quality-reviewer: ✅ Approved（無 Critical；1 項 Important = CurrentOperator thread-local 洩漏為 deferred-wiring，已補 Javadoc 契約並列 follow-up；其餘 Minor）
- Next action: dispatch implementer subagent for task 3.2（各 CampaignType RuleConfig DTO + schema 驗證 + upcaster）。

## Session 19 — 2026-06-10 16:45
- Stage: SDD
- Task: 3.2 實作各 CampaignType 的 RuleConfig DTO 與 schema 驗證 + upcaster
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 建立 DiscountRuleConfig/GiftAddonRuleConfig/FlashSaleRuleConfig DTO（含 schema_version）、依 type 路由的 schema 驗證（拒絕負折扣/百分比>100%/結束早於開始/門檻條件保存）與舊版 schema_version 的應用層 upcaster（不需 DB migration）。

## Session 20 — 2026-06-10 17:30
- Stage: SDD
- Task: 3.2 實作各 CampaignType 的 RuleConfig DTO 與 schema 驗證 + upcaster
- Transition: in_progress → passing
- Evidence:
  - Commits: ba5bbb6 feat(campaign): add RuleConfig DTOs, schema validation and upcaster (task 3.2)；1a7e5bb refactor(campaign): drop unused JSR-310, guard rule_config read boundary (task 3.2 review)
  - Tests: RuleConfigMapperTest 9 fast non-DB tests green（合法三型序列化含 schema_version、負折扣/百分比>100%/endAt<startAt 各帶 reason 拒絕、NONE/MIN_SPEND 門檻 round-trip、舊版 schema_version upcast、blank/非物件 JSON read 守衛）；`:campaign:spotlessCheck checkstyleMain spotbugsMain test`、`:app:test` ArchUnit BUILD SUCCESSFUL
  - Spec-reviewer: ✅ Spec compliant（5/5；RuleConfig sealed + 三 record 對齊 03-class，未提前實作 4.1/3.3/section-5）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；BigDecimal 金額正確、discriminator 讀取健壯、upcaster 最小化；Minor #1 dead JSR-310 與 #2 read 守衛 #3 annotation 已於 1a7e5bb 修正）
- Next action: dispatch implementer subagent for task 3.3（優惠券三層 coupon_campaign/coupon_code/coupon_redemption + 唯一鍵防重複核銷 + used_count atomic 控總量）。

## Session 21 — 2026-06-10 17:35
- Stage: SDD
- Task: 3.3 實作優惠券三層結構（coupon_campaign / coupon_code / coupon_redemption）
- Transition: not_started → in_progress
- Next action: dispatch implementer subagent 以 Flyway migration 建立三表（含 code_type/coupon_code_status enum 與 unique(coupon_code_id,user_id,order_id)），JPA 實體與 repository，並實作 used_count atomic update 控總量與重複核銷唯一鍵阻擋（對齊 05-er-database-schema.puml）。

## Session 22 — 2026-06-10 18:20
- Stage: SDD
- Task: 3.3 實作優惠券三層結構（coupon_campaign / coupon_code / coupon_redemption）
- Transition: in_progress → passing
- Evidence:
  - Commits: 34a486d feat(campaign): add coupon three-table model with atomic usage control (task 3.3)
  - Tests: CouponEnumContractTest（fast no-DB）green；`:campaign:spotlessCheck checkstyleMain spotbugsMain test`、`:app:test` ArchUnit BUILD SUCCESSFUL；CouponPersistenceIntegrationTest（真實 Postgres Testcontainers，4 tests：SHARED_CODE 單碼/UNIQUE_CODE 多碼含 assigned_user_id+status、重複核銷 unique 阻擋 DataIntegrityViolationException、atomic used_count 達上限回傳 0 拒絕且不超過 total_usage_limit）本沙箱無 Docker → SKIPPED 待 CI 實跑
  - Spec-reviewer: ✅ Spec compliant（5/5；V2__coupon.sql 三表/兩 enum/FK/unique(coupon_code_id,user_id,order_id)/unique(lower(code)) 對齊 05-er，V1 未動）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；entity 對齊 3.1 NAMED_ENUM 風格、tryIncrementUsedCount 單一條件式 UPDATE 無 lost-update、clearAutomatically=true 讀新狀態；僅 cosmetic Minor）
- Next action: Section 3（3.1–3.3）全數 passing；執行 final pass（`./gradlew check` + `openspec validate add-ecommerce-campaign-system --strict`）後 invoke spec-driven-dev:verification-before-completion。

## Session 24 — 2026-06-10 19:00
- Stage: SDD
- Task: 4.1 實作活動 CRUD 內部 REST（含驗證與稽核）
- Transition: not_started → in_progress
- Next action: 於新分支 feat/section-4-campaign-api 派發 implementer subagent 實作 Campaign CRUD 內部 REST（建立預設 DRAFT、RuleConfig 驗證落庫、樂觀鎖稽核、未驗證/未授權拒絕存取），完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 25 — 2026-06-10 19:45
- Stage: SDD
- Task: 4.1 實作活動 CRUD 內部 REST（含驗證與稽核）
- Transition: in_progress → passing
- Evidence:
  - Commits: b387797 feat(campaign): add campaign CRUD internal REST with validation, audit and operator auth (task 4.1); 23fa3aa fix(campaign): enforce read-then-update optimistic locking on campaign update (task 4.1 review)
  - Tests: CampaignControllerTest 8/8 green（建立折扣活動並落為草稿 → 201+DRAFT+id；rule/reach 設定可分別修改；不合法規則 400；stale version → 409 走真實版本檢查；GET 缺失 404；未驗證 401；非 OPERATOR 403）。完整 `./gradlew check` BUILD SUCCESSFUL（spotless/checkstyle/spotbugs/ArchUnit/JaCoCo 全綠；Docker-gated 整合測試本沙箱 skip）。
  - Spec-reviewer: ✅ Spec compliant（5/5：scenario 覆蓋、class-diagram DTO 對齊、§10 auth+稽核、無超範圍端點、無缺漏 scenario）
  - Code-quality-reviewer: ✅ Approved（首輪提出 1 Important：UpdateCampaignRequest.version 文件化為樂觀鎖守衛卻未實作；已於 23fa3aa 修正為 @NotNull 必填 + service 版本比對丟 ObjectOptimisticLockingFailureException(→409)，stale 測試改驗真實邏輯；再審 ✅）
- Next action: 接續實作 task 4.2（活動狀態切換 API 與守衛 DRAFT→SCHEDULED→RUNNING→PAUSED/ENDED），依賴 4.1。

## Session 26 — 2026-06-10 19:50
- Stage: SDD
- Task: 4.2 實作活動狀態切換 API 與守衛（啟用/暫停/結束）
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent，於 Campaign 聚合加入 transitionTo(state) 守衛（依 02-state 圖合法邊：DRAFT→SCHEDULED→RUNNING→PAUSED↔RUNNING、RUNNING/PAUSED→ENDED），新增 /internal/campaigns/{id}/status 端點（帶 version 樂觀鎖），不合法轉換回明確錯誤；完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 27 — 2026-06-10 20:15
- Stage: SDD
- Task: 4.2 實作活動狀態切換 API 與守衛（啟用/暫停/結束）
- Transition: in_progress → passing
- Evidence:
  - Commits: 0f95379 feat(campaign): add campaign status-transition API with legal-transition guard (task 4.2)
  - Tests: Domain CampaignStatusTransitionTest（合法整鏈 DRAFT→SCHEDULED→RUNNING→PAUSED→RUNNING→ENDED；ENDED→RUNNING 終態被擋；skip-ahead/self-transition 被擋）；CampaignControllerTest 新增 合法狀態切換(200)/不合理狀態切換被擋下(422 illegal_transition + from→to reason)/stale version(409 走真實版本檢查)/缺 targetStatus(400)。完整 `./gradlew check` BUILD SUCCESSFUL。
  - Spec-reviewer: ✅ Spec compliant（5/5：合法/非法 scenario 覆蓋、ALLOWED_TRANSITIONS 恰為 02-state 圖 6 條合法邊、ENDED 終態、拒絕附 from→to 原因、無越界建排程；狀態僅經 guarded transitionTo 變更，CRUD update 不動 status）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；守衛置於 aggregate 合 DDD、靜態 map 真不可變滿足 SpotBugs、422/409 語意分離、stale 測試走真實邏輯未重蹈 4.1 覆轍；僅 3 點 Minor 觀察不需修改）
- Next action: Section 4（4.1、4.2）全數 passing；執行 final pass（openspec validate --strict）後 invoke spec-driven-dev:verification-before-completion。

## Session 23 — 2026-06-10 17:00
- Stage: SDD（post-verification CI fix）
- Task: 3.1 / 3.3 — Docker 整合測試於 GitHub Actions 實跑後修正兩處缺陷
- Transition: passing → passing（CI 驗證後修正，狀態不變）
- Evidence:
  - Commits: 9121bde fix(campaign): make used_count increment transactional and fix audit timestamp assertion (tasks 3.1, 3.3)
  - CI: PR #6 首跑（run 27264181692）於 Docker runner 實跑被本地 skip 的 @RequiresDocker 測試，揭露 2 個失敗：(1) CouponPersistenceIntegrationTest.atomicUsedCountIncrementNeverExceedsTotalUsageLimit → TransactionRequiredException（@Modifying UPDATE 無交易邊界）；(2) CampaignPersistenceIntegrationTest.successfulWriteRecordsUpdatedByAndUpdatedAt → 記憶體 nanosecond Instant vs DB microsecond timestamptz 精度不一致
  - 修正: tryIncrementUsedCount 加 @Transactional（REQUIRED，自成工作單元/可參與呼叫端交易）；測試以 DB re-read 的 created_at 為比較基準
  - 重跑 run 27264878795 ✅ BUILD SUCCESSFUL（:app:test 實跑真實 PostgreSQL 容器 ~2m53s，過 JaCoCo verification）
- Next action: PR #6 CI 綠燈；待 review 合併後接 Section 4（Campaign API CRUD 與生命週期）。

## Session 28 — 2026-06-10 21:00
- Stage: SDD
- Task: 5.1 實作 PromotionEvaluator（折扣/滿贈加價購/閃購）優惠計算
- Transition: not_started → in_progress
- Next action: 於新分支 feat/section-5-evaluators 派發 implementer subagent，於 campaign/evaluation 建立 PromotionEvaluator Strategy 介面 + CartContext/PromotionResult、DiscountPromotionEvaluator（依 DiscountRuleConfig 算折扣金額與門檻）與 stub 級 FlashSalePromotionEvaluator（回傳已售罄/不適用），以 supports()→CampaignType 註冊、OCP 不改既有，完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 29 — 2026-06-10 21:30
- Stage: SDD
- Task: 5.1 實作 PromotionEvaluator（折扣/滿贈加價購/閃購）優惠計算
- Transition: in_progress → passing
- Evidence:
  - Commits: 6f1a3f6 feat(campaign): add PromotionEvaluator strategy with discount calc and flash-sale stub (task 5.1)
  - Tests: PromotionEvaluatorTest 11 tests green（@Nested 對應三 scenario：結帳時計算折扣 AMOUNT/PERCENTAGE HALF_UP scale2/AMOUNT cap/MIN_SPEND 達/未達、閃購 stub 回 not-applicable 不丟例外、registry 依 supports() 解析含未註冊型別降級）；`:campaign:spotlessCheck checkstyleMain spotbugsMain test`、`:app:test` ArchUnit BUILD SUCCESSFUL
  - Spec-reviewer: ✅ Spec compliant（5/5；介面簽章對齊 03-class，無 GIFT_ADDON/ReachTrigger 外洩，PromotionResult YAGNI）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；BigDecimal scale/rounding、負值防護、record 不可變、測試行為導向皆達標；3 項 Minor 觀察不阻擋）
- Next action: dispatch implementer subagent for task 5.2（ReachTriggerEvaluator：TriggerContext shouldTrigger 行為事件/排程 cycle 判定 + 例外隔離記 skipped）。

## Session 30 — 2026-06-10 21:35
- Stage: SDD
- Task: 5.2 實作 ReachTriggerEvaluator（行為事件 / 排程 cycle）觸發判定
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent，於 campaign/evaluation 建立 ReachTriggerEvaluator Strategy 介面 + TriggerContext（行為事件/排程 cycle，無 CartContext）、ScheduledTriggerEvaluator 與 BehaviorTriggerEvaluator 與 supports() 註冊 registry，並以例外隔離（evaluator 拋例外→該筆記 skipped + reason、不影響同批其他）封裝判定，完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 31 — 2026-06-10 22:10
- Stage: SDD
- Task: 5.2 實作 ReachTriggerEvaluator（行為事件 / 排程 cycle）觸發判定
- Transition: in_progress → passing
- Evidence:
  - Commits: 1fb2f39 feat(campaign): add ReachTriggerEvaluator strategy with scheduled/behavior triggers and exception isolation (task 5.2)；feec9de docs: document scoped checkstyle SuppressWarningsFilter policy (task 5.2 review)
  - Tests: ReachTriggerEvaluatorTest 7 tests green（@Nested 觸發判定無購物車：scheduled due/not-due + behavior match/no-match；觸發判定例外隔離：拋例外 evaluator 在三筆 batch 中只記自己 SKIPPED+reason、其餘兩筆仍 TRIGGER）；`:campaign:spotlessCheck checkstyleMain spotbugsMain test`、`:app:test` ArchUnit BUILD SUCCESSFUL
  - Spec-reviewer: ✅ Spec compliant（5/5；介面保留 supports():CampaignType + shouldTrigger(TriggerContext)，新增 kind() 判定為忠於 diagram 的最小擴充；無 section-6 Kafka/scheduler/consumer 外洩）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；TriggerContext/TriggerDecision record 不可變、catch(RuntimeException) 為 §6 例外隔離所必需且 scoped 抑制、SLF4J 首例慣例正確；Minor #3 CLAUDE.md 留註已於 feec9de 補上）
- Next action: Section 5（5.1、5.2）全數 passing；執行 final pass（`./gradlew check` + `openspec validate add-ecommerce-campaign-system --strict`）後 invoke spec-driven-dev:verification-before-completion。



## Session 32 — 2026-06-11 10:00
- Stage: SDD
- Task: 6.1 實作活動生命週期排程（自動進入 RUNNING / ENDED）
- Transition: not_started → in_progress
- Next action: 於新分支 feat/section-6-campaign-triggers 派發 implementer subagent 實作活動生命週期排程（@Scheduled 掃描：SCHEDULED 且 startAt<=now → RUNNING；RUNNING/PAUSED 且 endAt<=now → ENDED，全程經 Campaign.transitionTo 合法邊守衛），完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 33 — 2026-06-11 10:45
- Stage: SDD
- Task: 6.1 實作活動生命週期排程（自動進入 RUNNING / ENDED）
- Transition: in_progress → passing
- Evidence:
  - Commits: f1c3a26 feat(campaign): add campaign lifecycle scheduler for time-driven RUNNING/ENDED (task 6.1); cc087b0 fix(campaign): isolate lifecycle sweep per campaign in its own transaction (task 6.1 review)
  - Tests: CampaignLifecycleSchedulerTest 7 fast unit tests green（起訖時間自動推進：SCHEDULED+startAt→RUNNING、RUNNING/PAUSED+endAt→ENDED、SCHEDULED 雙過期一 tick 收斂 ENDED、未到期/DRAFT/ENDED 不動、per-campaign 例外隔離走真實 TransactionTemplate 邊界）；`:campaign:spotlessCheck checkstyleMain spotbugsMain test`、`:app:test`（ArchUnit）BUILD SUCCESSFUL
  - Spec-reviewer: ✅ Spec compliant（5/5；全程經 Campaign.transitionTo 守衛、只驅動合法邊、無 6.2/6.3 Kafka/ShedLock 外洩、app 模組未動）
  - Code-quality-reviewer: ✅ Approved（首輪 1 Important：@Transactional+broad-catch 因 deferred flush 無法隔離 save-time 樂觀鎖失敗；已於 cc087b0 改為 per-campaign TransactionTemplate + saveAndFlush 真正隔離，再審無 Critical/Important）
- Next action: dispatch implementer subagent for task 6.2（排程批次掃描並發出 ReachRequested + ShedLock 防重）。

## Session 34 — 2026-06-11 11:00
- Stage: SDD
- Task: 6.2 實作排程批次掃描並發出 ReachRequested（ShedLock 防重）
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent 實作排程批次掃描 status=RUNNING 活動、經 ReachTriggerEvaluatorRegistry 判定發送時機、以 KafkaTemplate 發出 ReachRequested(triggerType=SCHEDULED_BATCH, sendCycle=sched:{campaignId}:{cycleStart}) 至 reach.requested，並以 ShedLock + 確定性 truncate cycleStart 確保多實例/補掃同一週期只跑一次，完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 35 — 2026-06-11 11:50
- Stage: SDD
- Task: 6.2 實作排程批次掃描並發出 ReachRequested（ShedLock 防重）
- Transition: in_progress → passing
- Evidence:
  - Commits: 64f183a feat(campaign): add scheduled reach-scan emitting ReachRequested with ShedLock dedup (task 6.2)
  - Tests: CampaignReachScanSchedulerTest 10 fast unit tests green（RUNNING+due+TRIGGER 發 SCHEDULED_BATCH 活動層級事件含 targetSpec/reachPlan 無收件人清單、NO_TRIGGER/not-due/non-RUNNING/SKIPPED 不發、同 cycle 同 key/不同 cycle 不同 key 確定性、per-campaign 例外隔離、@SchedulerLock annotation 把關）；`:campaign:spotlessCheck checkstyleMain spotbugsMain test`、`:app:test`（ArchUnit）BUILD SUCCESSFUL；新增 shedlock 5.16.0 deps + V3__shedlock.sql + producer serializer 設定
  - Spec-reviewer: ✅ Spec compliant（5/5；兩 scenario 全覆蓋、PartitionKeys 複合鍵非裸 campaignId、triggerEventId=null、無 6.3/reach 外洩、campaign↛reach 邊界守住）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；floorToCycle 確定性無 now() 洩漏、隔離走 CLAUDE.md scoped 慣例、publisher seam 為 6.3 預留、ShedLock module-owned 且 canonical schema；Minor：cycle-duration 缺正值守衛、FQN 風格不一、真實 ShedLock 整合測試待 Docker，列 follow-up 不阻擋）
- Next action: dispatch implementer subagent for task 6.3（行為事件消費者並發出 ReachRequested 路徑2）。

## Session 36 — 2026-06-11 12:00
- Stage: SDD
- Task: 6.3 實作行為事件消費者並發出 ReachRequested（路徑2）
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent 實作 campaign consumer：以 at-least-once 容器消費 domain.events、比對 RUNNING 活動經 ReachTriggerEvaluatorRegistry shouldTrigger 命中、以 ReachRequestPublisher 發出 ReachRequested(triggerType=EVENT, sendCycle=event:{triggerEventId}) 至同一 reach.requested，處理落定後才 ack，完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 37 — 2026-06-11 12:45
- Stage: SDD
- Task: 6.3 實作行為事件消費者並發出 ReachRequested（路徑2）
- Transition: in_progress → passing
- Evidence:
  - Commits: 4258016 feat(campaign): add domain-event consumer emitting ReachRequested on behavior trigger (task 6.3)
  - Tests: BehaviorEventReachTriggerTest + DomainEventConsumerTest 7 fast unit tests green（CART_ABANDONED+RUNNING GIFT_ADDON 發 EVENT 活動層級事件 sendCycle=event:{id} 含 targetSpec/reachPlan 無收件人；DISCOUNT 無 EVENT evaluator→SKIPPED 不發；非命中事件→NO_TRIGGER 不發；無 RUNNING 不發；per-campaign publish 失敗隔離仍發其他；handler→ack InOrder）；用真實 registry+evaluators 跑端到端 dispatch；`:campaign:spotlessCheck checkstyleMain spotbugsMain test`、`:app:test`（ArchUnit）BUILD SUCCESSFUL
  - Spec-reviewer: ✅ Spec compliant（5/5；DE→CC→shouldTrigger→RR 同一 topic 路徑2、重用 path-1 ReachRequestPublisher 下游一致、registry 例外隔離、at-least-once 處理後才 ack、campaign↛reach 守住）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；handler/adapter 分離乾淨、例外隔離分層不重複包裹、deserializer trusted-packages 限定不放 *、gated factory 僅影響本 consumer；Minor：與 path-1 結構近似可接受、userId 為合約欄位保留、swallow-then-ack 缺 metric 觀測列 follow-up）
- Next action: Section 6（6.1–6.3）全數 passing；執行 final pass（`./gradlew check` + `openspec validate add-ecommerce-campaign-system --strict`）後 invoke spec-driven-dev:verification-before-completion。

## Session 38 — 2026-06-11 14:00
- Stage: SDD
- Task: 7.1 實作 reach_request 批次落庫與批次冪等
- Transition: not_started → in_progress
- Next action: 於新分支 feat/section-7-reach-orchestrator 派發 implementer subagent，於 reach/orchestrator 實作 ReachOrchestrator 消費 reach.requested（at-least-once）、以 unique(campaign_id, send_cycle_key, trigger_type) upsert 一筆 reach_request 去重、凍結 target_spec_snapshot/reach_plan_snapshot、已 DISPATCHING/DONE 直接 ack 跳過，並以 Flyway migration 建立 reach_request 表（對齊 05-er），完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 39 — 2026-06-11 14:45
- Stage: SDD
- Task: 7.1 實作 reach_request 批次落庫與批次冪等
- Transition: in_progress → passing
- Evidence:
  - Commits: 9ce628d feat(reach): add reach_request batch landing with fan-out idempotency (task 7.1); 0b573bb docs(reach): document deliberate omission of listener configurer in reach Kafka factory (task 7.1 review)
  - Tests: ReachRequestLanderTest 5 fast unit tests green（@Nested 對應三 scenario：首次消費插入 PENDING+凍結 target/reach 快照後進入展開；已 PENDING 續跑展開不再 insert；並發 insert 競態以 unique 約束擋下 re-read winner 不建第二筆；已 DISPATCHING/已 DONE 直接跳過 verifyNoInteractions(expander)）。完整 `./gradlew check` BUILD SUCCESSFUL（reach spotless/checkstyle/spotbugs/test + app ArchUnit + JaCoCo 全綠；Docker-gated 整合測試本沙箱無 Docker → skip）。
  - Spec-reviewer: ✅ Spec compliant（5/5；V4__reach_request.sql 16 欄+unique(campaign_id,send_cycle_key,trigger_type)+trigger_type/reach_request_status enum 對齊 05-er、消費步驟對齊 01-sequence、entity 刻意不映 7.3 的 count/timestamp 欄為合理 scope 決策、reach↛campaign 守住）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；thin-listener→Kafka-free-handler 對齊 DomainEventConsumer 範式、專屬 typed at-least-once factory 與 campaign DLT 隔離有據、lost-insert-race + @Transactional saveAndFlush 正確、NoOpAudienceExpander plain @Component 為乾淨 7.3 handoff、spotbugs exclude 窄且有據；Minor #2 reach factory 未走 listener configurer → 已於 0b573bb 文件化刻意省略，因其 raw <Object,Object> 簽章與 typed deserializer 不相容且本專案未設 listener.* 調校）
- Next action: dispatch implementer subagent for task 7.2（AudienceResolver：reach 模組將 targetSpec 解析為收件人清單，支援靜態名單與會員等級/地區條件分眾）。

## Session 40 — 2026-06-11 15:00
- Stage: SDD
- Task: 7.2 實作 AudienceResolver（位於 reach）將 targetSpec 解析為收件人
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent，於 reach/audience 實作 AudienceResolver（resolve(TargetSpec)→List<Recipient>）+ reach 自有 TargetSpec/Recipient 模型（解析事件帶的 targetSpec JSON：kind STATIC_LIST/CONDITION、listId、conditions）；STATIC_LIST 由新增 audience_list/audience_list_member 表（V5 migration，對齊 05-er 區塊 A）查詢解析，CONDITION（會員等級/地區）委派 MemberDirectory port（MVP 最小實作，會員主檔屬上游電商主站），campaign 不展開收件人，完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 41 — 2026-06-11 15:30
- Stage: SDD
- Task: 7.2 實作 AudienceResolver（位於 reach）將 targetSpec 解析為收件人
- Transition: in_progress → passing
- Evidence:
  - Commits: c6ba61b feat(reach): add AudienceResolver resolving targetSpec to recipients (task 7.2); ad886b4 docs(reach): clarify switch-dispatch rationale and import JsonProcessingException (task 7.2 review)
  - Tests: TargetSpecParserTest 8 + StrategyAudienceResolverTest 2 = 10 fast unit tests green（@Nested「受眾一律由 reach 解析」：STATIC_LIST→audienceListMemberRepository.findByListId 映射 Recipient、CONDITION→MemberDirectory port，各以 verifyNoInteractions 驗證另一 collaborator 未被呼叫；parser trust-boundary：合法 STATIC_LIST/CONDITION 解析、blank/malformed/unknown-kind/缺 listId/缺 conditions 各帶 actionable reason 拒絕）。完整 `./gradlew check` BUILD SUCCESSFUL（reach spotless/checkstyle/spotbugs/test + app ArchUnit + JaCoCo 全綠；Docker-gated 整合測試 skip）。
  - Spec-reviewer: ✅ Spec compliant（5/5；AudienceResolver 介面簽章對齊 03-class、V5__audience_list.sql 複合 PK(list_id,user_id)+FK 對齊 05-er 區塊 A、CONDITION MVP stub 比照 5.1 FlashSale 為 acceptable「支援」、無 7.3 reach_task fan-out 外洩、reach 自有 TargetSpec/Recipient 不 import campaign DTO、reach↛campaign 守住）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；module-boundary 紀律佳、trust-boundary parser 單一驗證點、PII 最小化 Recipient 僅 userId、@IdClass 複合鍵 equals/hashCode 正確、record 防禦性複製 conditions、MvpMemberDirectory 誠實標注 MVP seam、ObjectMapper.copy() 避 EI_EXPOSE_REP2、無 silent spotbugs exclude；3 Minor：switch 非 registry 之 Javadoc 誇大→已於 ad886b4 修正、inline FQN→已 import、test 子類 override 觀察不需改）
- Next action: dispatch implementer subagent for task 7.3（分頁 fan-out 展開 ReachTask：每批 M 筆 INSERT ON CONFLICT DO NOTHING 落四欄 unique、斷點續跑、頻控、status PENDING→EXPANDING→DISPATCHING + total_count 回填），依賴 7.1、7.2。

## Session 42 — 2026-06-11 15:45
- Stage: SDD
- Task: 7.3 實作分頁 fan-out 展開 ReachTask（斷點續跑 + 頻控 + 任務冪等）
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent，於 reach/orchestrator 實作真正的 AudienceExpander（取代 7.1 NoOpAudienceExpander）：reach_request PENDING→EXPANDING，呼叫 TargetSpecParser+AudienceResolver 解析收件人，分頁（每批 M 筆）以 JdbcTemplate `INSERT ... ON CONFLICT (campaign_id,user_id,send_cycle_key,channel) DO NOTHING` 批次落 ReachTask(PENDING)（斷點續跑冪等），插入前以頻控時間窗查歷史 reach_task 命中則跳過（與冪等分離），完成後 EXPANDING→DISPATCHING 並一次回填 total_count；新增 V6 migration 建 reach_task 表 + channel/reach_task_status enum + 四欄 unique + ER 建議索引，完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 43 — 2026-06-11 16:30
- Stage: SDD
- Task: 7.3 實作分頁 fan-out 展開 ReachTask（斷點續跑 + 頻控 + 任務冪等）
- Transition: in_progress → passing
- Evidence:
  - Commits: 9bff682 feat(reach): add paged ReachTask fan-out with idempotency and frequency capping (task 7.3); 7b94e97 refactor(reach): use inline @SuppressFBWarnings for expander DI fields (task 7.3 review)
  - Tests: PagedAudienceExpanderTest 6 fast unit tests green（N 收件人→分頁批次 insert + status PENDING→EXPANDING→DISPATCHING + total_count 一次回填、ON CONFLICT 四欄鍵、already-EXPANDING resume、不同 cycle 窗內頻控跳過而同 cycle 不被頻控、空頁不發 batchUpdate）+ ReachTaskFanOutIntegrationTest 3 個 @RequiresDocker Testcontainers 測試（真實 ON CONFLICT 重跑收斂 N 不重複、頻控跳過 seeded 前一 cycle user、結束 DISPATCHING+total_count=N），本沙箱無 Docker → skip 待 CI；reach 單元測試共 20 green。完整 `./gradlew check` BUILD SUCCESSFUL（spotless/checkstyle/spotbugs/ArchUnit/JaCoCo 全綠）。
  - Spec-reviewer: ✅ Spec compliant（5/5；V6__reach_task.sql 16 欄+四欄 unique+FK+channel/reach_task_status enum+3 ER 建議索引對齊 05-er、展開呼叫順序對齊 01-sequence、NoOpAudienceExpander 已刪僅 PagedAudienceExpander 一個 bean、頻控 send_cycle_key<>currentCycle 與冪等分離正確、無 section 8/9 dispatcher/retry/DLQ/suppression 外洩、reach↛campaign 守住）
  - Code-quality-reviewer: ✅ Approved（再審；首審 1 Important：EI_EXPOSE_REP2 用 whole-class exclude.xml 應比照 ReachRequestPublisher 改 inline @SuppressFBWarnings → 已於 7b94e97 改為建構子 inline 註解並移除 exclude block、spotbugs 移除 entry 後仍綠證明真有抑制；crash-resume 每頁獨立交易 + 狀態轉換各自短交易、SQL 全參數綁定無注入、ON CONFLICT 收斂、測試行為導向皆獲肯定；Minor：freq-cap 跨活動範圍已補 Javadoc、PagedAudienceExpander 216 行未來可抽 ReachTaskDao 為非阻擋 note）
- Next action: Section 7（7.1–7.3）全數 passing；執行 final pass（`./gradlew check` + `openspec validate add-ecommerce-campaign-system --strict`）後 invoke spec-driven-dev:verification-before-completion。

## Session 44 — 2026-06-11 17:00
- Stage: SDD
- Task: 8.1 實作 ChannelAdapter 介面與 EmailAdapter（含 circuit breaker）
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent，於 reach/channel 實作 ChannelAdapter 介面（channel():Channel + send(ReachMessage):SendResult，對齊 03-class）、EmailAdapter（介接 SendGrid/SES 包一層、由 ChannelAdapter registry 依 reachPlan channel 選用、OCP 新增通道不改既有），並以 Resilience4j circuit breaker 包覆 EmailAdapter（滑動窗口/失敗率門檻/冷卻/half-open 探測皆可設定，對齊 spec「外部通道中斷的穩定降級」5 scenario 與 04-component），完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 45 — 2026-06-11 17:45
- Stage: SDD
- Task: 8.1 實作 ChannelAdapter 介面與 EmailAdapter（含 circuit breaker）
- Transition: in_progress → passing
- Evidence:
  - Commits: 737ee9b feat(reach): add ChannelAdapter + EmailAdapter with Resilience4j circuit breaker (task 8.1); 96768ad fix(reach): make EmailAdapter conditional on provider + expose breaker registry (task 8.1 review)
  - Tests: EmailAdapterCircuitBreakerTest 8 + EmailChannelPropertiesTest 3 + EmailChannelContextLoadTest 2 = 13 fast 單元測試 green（breaker 驅動真實 Resilience4j 狀態機 CLOSED→OPEN→HALF_OPEN→CLOSED/OPEN、五項參數可設定且預設 window20/min20/≥50%/30s/5 可覆寫、dispatcher seam 以 isAvailable() 非消耗讀 OPEN-state + RetryableSendException.isBreakerOpen() 區分 breaker 短路 vs 真實失敗、ApplicationContextRunner 證無 provider bean 時 context 啟動且 EmailAdapter 缺席、有 provider 時啟用）。完整 `./gradlew check` BUILD SUCCESSFUL（reach spotless/checkstyle/spotbugs/test + app ArchUnit + JaCoCo 全綠；Docker-gated 整合測試本沙箱 skip）。
  - Spec-reviewer: ✅ Spec compliant（5/5；ChannelAdapter 簽章 channel():Channel+send(ReachMessage):SendResult 對齊 03-class、EmailAdapter 包外部 EmailProviderClient(SendGrid/SES) 對齊 04-component、reuse shared.event.Channel、五項 breaker 參數可設定且測試驅動真實狀態轉換非僅斷言設定值、兩個 dispatcher-side scenario 以 adapter seam 滿足未越界建 dispatcher、無 retry/DLQ/suppression/send_result 外洩、reach↛campaign 守住）
  - Code-quality-reviewer: ✅ Approved（再審；首審 1 Important：EmailAdapter 為無條件 @Component 但 EmailProviderClient 無任何 bean → Spring context 啟動失敗（CI @SpringBootTest 會破、本地 Docker-gate 遮蔽）→ 已於 96768ad 改 @ConditionalOnBean(EmailProviderClient.class) + 新增非 Docker-gated EmailChannelContextLoadTest 守 context 啟動回歸；Minor #2 丟棄的 CircuitBreakerRegistry → 改 managed @Bean(@ConditionalOnMissingBean) 使 breaker 可被 Micrometer/actuator 觀測；Minor #3 inline FQN → 改 import；fail-clear 無 silent no-op、PII 最小化 ReachMessage 僅 userId、broad catch(RuntimeException) 為 CLAUDE.md 認可隔離模式且有註解）
- Next action: dispatch implementer subagent for task 8.2（抑制名單 suppression 查表與發送前過濾：suppression(user_id/channel/reason) 表 + 發送前命中標 FAILED 不可重試、不送出），依賴 3.1。

## Session 46 — 2026-06-11 18:00
- Stage: SDD
- Task: 8.2 實作抑制名單（suppression）查表與發送前過濾
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent，新增 V7 suppression migration（user_id/channel/reason，design.md §10 MVP 單表，ER 05-er 未繪此表但 §10 與 spec「收件人 PII 最小化與抑制名單」明訂）+ SuppressionRepository 查表 + 發送前抑制守衛（命中退訂/硬退信/投訴者回報「該 task 應標 FAILED 不可重試且不送出」之結果，供 Section 9 dispatcher 於發送前呼叫），不建 dispatcher 本體，完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 47 — 2026-06-11 18:45
- Stage: SDD
- Task: 8.2 實作抑制名單（suppression）查表與發送前過濾
- Transition: in_progress → passing
- Evidence:
  - Commits: bc377a1 feat(reach): add suppression-list lookup + pre-send guard (task 8.2)
  - Tests: SuppressionGuardTest 6 fast 單元測試 green（@Nested「發送前抑制名單過濾 (FR-015)」：退訂/硬退信/投訴三種命中各回 suppressed→failedStatus()==ReachTaskStatus.FAILED+reason 帶出、不送出；miss→proceed；@Nested「抑制名單為通道限定」：SMS 抑制不擋 EMAIL、EMAIL 抑制不擋 SMS 雙向驗證，mock repository 驅動 guard 行為）。完整 `./gradlew check` BUILD SUCCESSFUL（reach spotless/checkstyle/spotbugs/test + app ArchUnit + JaCoCo 全綠；Docker-gated 整合測試本沙箱 skip）。
  - Spec-reviewer: ✅ Spec compliant（5/5；V7__suppression.sql user_id/channel/reason 對齊 design.md §10、reuse V6 channel pg enum、PII 最小化僅 user_id 無 email、composite PK(user_id,channel) channel-scoped 查表且雙向測試、SuppressionVerdict.failedStatus() 重用 ReachTaskStatus.FAILED 不增 ad-hoc 狀態、guard 僅回 verdict 不寫 reach_task 為 Section-9 dispatcher seam、無 dispatcher/retry/DLQ/retention/退訂入口外洩、reach↛campaign 守住；ER 未繪 suppression 但 §10+spec 明訂故非 scope creep）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；@IdClass + NAMED_ENUM 映射與 AudienceListMember/ReachRequest 慣例一致、PII 最小化、SuppressionVerdict record 不變式對稱(suppressed⇔reason)+NOT_SUPPRESSED singleton、evaluate 信任邊界 null 守衛適度、測試驅動真實 guard 僅 mock repository 且 channel-scoping 雙向、檔案切分 Entry/Id/Repository/Reason/Verdict/Guard 內聚；composite PK 一通道一 reason 為 consume-only MVP 可接受權衡已記；2 Minor：test inline Mockito.mock FQN、suppressed_at 存而未讀皆比照 AudienceListMember.added_at 之 audit provenance 非投機，不阻擋）
- Next action: Section 8（8.1–8.2）全數 passing；執行 final pass（`./gradlew check` + `openspec validate add-ecommerce-campaign-system --strict`）後 invoke spec-driven-dev:verification-before-completion。

## Session 48 — 2026-06-11 19:30
- Stage: SDD
- Task: 9.1 實作兩階段事務發送（FOR UPDATE SKIP LOCKED + 租約）
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent，於 reach/dispatcher 實作兩階段短事務發送（階段1 FOR UPDATE SKIP LOCKED 撈 PENDING/RETRY_SCHEDULED 且 next_retry_at<=now、breaker 未開時標 PROCESSING+locked_by/locked_until 後立即 commit；事務外 ChannelAdapter.send；階段2 新事務回寫 SENT+寫 send_result/清鎖 或 RETRY_SCHEDULED+指數退避；breaker 開於標 PROCESSING 前跳過維持 PENDING、標 PROCESSING 後快速失敗走階段2 RETRY_SCHEDULED），新增 V8 send_result migration，完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 49 — 2026-06-11 20:30
- Stage: SDD
- Task: 9.1 實作兩階段事務發送（FOR UPDATE SKIP LOCKED + 租約）
- Transition: in_progress → passing
- Evidence:
  - Commits: 7f45534 feat(reach): two-phase transactional dispatcher with claim lease + exponential backoff (task 9.1); 1280cf3 test(reach): add Testcontainers IT for dispatcher claim/write-back SQL + tidy workerId (task 9.1 review)
  - Tests: RetryBackoffScheduleTest(3) + ReachPlanTemplateExtractorTest(4) + ReachTaskDispatcherTest(8) fast 單元測試 green（撈取標 PROCESSING、可重試指數退避 1m/5m/30m、重試耗盡→FAILED、不可重試（suppression 命中）→FAILED、breaker 開啟前跳過維持 PENDING、breaker 標 PROCESSING 後快速失敗→RETRY_SCHEDULED）+ ReachTaskDispatchDaoIntegrationTest(5 @RequiresDocker：claim+lease+next_retry_at IS NULL OR <=now 分支、SKIP LOCKED 互斥、markSent 寫 send_result+ON CONFLICT dedup、scheduleRetry retry_count+next_retry_at+清鎖、markFailed 清鎖）。`./gradlew check` BUILD SUCCESSFUL（spotless/checkstyle/spotbugs/test + ArchUnit reach↛campaign + JaCoCo 全綠；Docker-gated IT 本沙箱 5 skipped，implementer 另起臨時 Postgres 實跑驗證 claim CTE/enum cast/reach_plan_snapshot join/ON CONFLICT dedup 皆符合斷言）。新增 V8__send_result.sql（id/reach_task_id FK/provider_message_id/outcome/occurred_at + idx + partial unique，PII 最小化）。
  - Spec-reviewer: ✅ Spec compliant（5/5；5 場景皆有 scenario-named 行為測試、01/02/05 三圖契約符合、兩階段為真正分離短事務且 adapter.send() 在交易外滿足 NFR-002、breaker 前置檢查留 PENDING、PROCESSING 後 breaker fast-fail→RETRY_SCHEDULED、退避 1m→5m→30m max3、reach↛campaign 守住、未越界實作 9.2 DLQ 發佈/9.3 Reaper/10.1 取消 re-check）
  - Code-quality-reviewer: ✅ Approved（再審；首審 1 Important：ReachTaskDispatchDao claim/write-back SQL 零整合測試覆蓋 → 已於 1280cf3 補 ReachTaskDispatchDaoIntegrationTest（@RequiresDocker、真 Postgres+Flyway、斷言查實際 DB row 非 mock，含 SKIP LOCKED 互斥/ON CONFLICT dedup/IS NULL OR 分支）對齊 ReachTaskFanOutIntegrationTest bar；2 Minor：workerId() 假性 Optional → 改回 plain String、unexpected-RuntimeException 路由註解收緊明示延後 9.2 皆已修；兩階段交易紀律、SQL 全參數綁定無注入、isAvailable() default method 取捨、send_result provider_message_id 可空性處理皆獲肯定）
- Next action: dispatch implementer subagent for task 9.2（重試分類 taxonomy 細化、退避上限、重試耗盡 → reach.dlq Kafka 發佈 + 標記，承接 9.1 留下的 DLQ seam），依賴 9.1。

## Session 50 — 2026-06-11 20:45
- Stage: SDD
- Task: 9.2 實作重試分類、退避上限與 DLQ
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent，新增非重試分類 seam（NonRetryableSendException，地址無效等永久錯誤 → dispatcher 立即 markFailed 不燒重試額度，EmailAdapter 不把其包成 retryable、不計入 breaker）+ 承接 9.1 writeBackRetryable 的 DLQ seam：重試耗盡時改為標記 DLQ 狀態並發佈至 reach.dlq topic（新增 reach 側 ReachDlqPublisher + shared/event DLQ 事件 schema + PartitionKeys.forReachDlq 鍵），退避 1m→5m→30m max3 已由 9.1 完成，完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 51 — 2026-06-11 21:30
- Stage: SDD
- Task: 9.2 實作重試分類、退避上限與 DLQ
- Transition: in_progress → passing
- Evidence:
  - Commits: 0223ed0 feat(reach): add retry classification + reach.dlq dead-lettering on exhaustion (task 9.2)
  - Tests: ReachTaskDispatcherTest（非重試→立即 markFailed 未燒重試/未 dead-letter、耗盡 publishesBeforeMarking InOrder、deadLetterEventCarriesIdentificationFields、publishFailureLeavesRowUnmarked 不靜默遺失）+ EmailAdapterCircuitBreakerTest（NonRetryableSendException surface 且 breaker getNumberOfFailedCalls()==0、transient 仍 retryable 且計入==1）+ ReachDlqPublisherTest（success/broker-rejection/timeout，key=campaignId:sendCycleKey）+ EventSchemaContractTest（ReachTaskDeadLettered round-trip+必填+無 email/address/content）+ ReachTaskDispatchDaoIntegrationTest（@RequiresDocker：markDeadLettered PROCESSING→DLQ+清 lease+非 PROCESSING 不動）。`./gradlew check` BUILD SUCCESSFUL（spotless/checkstyle/spotbugsMain/test + ArchUnit reach↛campaign + JaCoCo 全綠；Docker-gated IT 本沙箱 auto-skip）。
  - Spec-reviewer: ✅ Spec compliant（5/5；3 in-scope scenario 全覆蓋、單步 PROCESSING→DLQ 在 02-state MVP 子集 note 下為可辯護讀法且無非法邊+guarded WHERE status='PROCESSING'、NonRetryableSendException 先於 broad catch 立即 FAILED 不前進 retry_count、.ignoreExceptions 不污染 breaker 有真實 metric 斷言、publish-then-mark 不靜默遺失經測試實證、ReachDlqPublisher 同步有界+拋錯 at-least-once+forReachDlq 鍵、ReachTaskDeadLettered PII 最小化、reach↛campaign + shared↛reach/campaign 守住；未越界 9.3/10.1/consumer/真實 provider）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；dispatch/dispatchTask 隔離正確、publish-then-mark InOrder 有測、no-publisher 拋錯非 silent no-op、.ignoreExceptions 慣用、markDeadLettered 與 markFailed 平行 guarded+enum cast+具名欄位 row-mapper 無錯位、事件 PII 最小化；3 Minor：測試內 FQN Mockito.mock/doThrow 可改 static import、ReachDlqPublisher 與 ReachRequestPublisher try/catch 跨模組重複屬可接受（邊界使然不抽共用）、ReachTaskDispatcherTest 類別 javadoc 仍寫 task 9.1 可順手更新——皆非阻擋）
- Next action: dispatch implementer subagent for task 9.3（Reaper job：背景排程定期掃 status=PROCESSING AND locked_until<now() 重置為 PENDING，回收 worker crash 卡死任務），依賴 9.1。

## Session 52 — 2026-06-11 21:45
- Stage: SDD
- Task: 9.3 實作 Reaper job（回收卡死的 PROCESSING 任務）
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent，於 reach/dispatcher 實作 Reaper 背景排程（@Scheduled 定期 UPDATE reach_task SET status=PENDING、清 locked_by/locked_until WHERE status='PROCESSING' AND locked_until<now()，回收 worker crash 卡死任務，重置後由既有 dispatcher poll 重取），完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 53 — 2026-06-11 21:55
- Stage: SDD
- Task: 9.3 實作 Reaper job（回收卡死的 PROCESSING 任務）
- Transition: in_progress → passing
- Evidence:
  - Commits: c697fd4 feat(reach): add Reaper job to reclaim expired-lease PROCESSING tasks (task 9.3)
  - Tests: `./gradlew :reach:test --tests com.example.campaignreach.reach.dispatcher.ReachTaskReaperTest --tests com.example.campaignreach.reach.dispatcher.ReachTaskDispatcherTest --tests com.example.campaignreach.reach.dispatcher.RetryBackoffScheduleTest :app:test --tests com.example.campaignreach.integration.ReachTaskDispatchDaoIntegrationTest` BUILD SUCCESSFUL；`./gradlew :reach:check :app:test` BUILD SUCCESSFUL（spotless/checkstyle/spotbugs/Jacoco + ArchUnit app tests green；Docker-gated integration tests follow existing @RequiresDocker behavior locally）
  - Spec-reviewer: ✅ Spec compliant（5/5；scenario「回收卡死任務」由 ReachTaskReaperTest 與 ReachTaskDispatchDaoIntegrationTest 覆蓋，guarded `UPDATE ... WHERE status='PROCESSING' AND locked_until < ?` 對齊 02-state lease recovery，非 PROCESSING/未過期 lease 不動，未越界實作 10.1 取消語意）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；Reaper 僅負責 scheduled tick + logging，DAO 保持短事務與參數化 SQL，reset 操作 idempotent 且不需要 ShedLock；測試含 wrapper 行為與真 DB SQL 行為）
- Next action: Section 9（9.1–9.3）全數 passing；執行 final pass（`./gradlew check` + `openspec validate add-ecommerce-campaign-system --strict`）後 invoke spec-driven-dev:verification-before-completion。

## Session 54 — 2026-06-11 17:47
- Stage: SDD
- Task: 10.1 實作活動暫停/結束時取消未送 task（取消競態語意）
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent 實作活動 PAUSED/ENDED 時取消 PENDING/RETRY_SCHEDULED reach_task，並在 dispatcher claim 同一短事務內重檢 campaign.status 序列化取消與撈取。

## Session 55 — 2026-06-11 18:00
- Stage: SDD
- Task: 10.1 實作活動暫停/結束時取消未送 task（取消競態語意）
- Transition: in_progress → passing
- Evidence:
  - Commits: a9e289a Implement reach task cancellation on campaign deactivation
  - Tests: `./gradlew check` BUILD SUCCESSFUL；`./gradlew spotlessApply :reach:check :app:test` BUILD SUCCESSFUL；`openspec validate add-ecommerce-campaign-system --strict` valid；Docker-gated ReachTaskDispatchDaoIntegrationTest locally skipped where Docker unavailable
  - Spec-reviewer: ✅ Spec compliant（3/3 scenarios covered；diagram contract satisfied）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；2 項 Minor，其中 side-effect CTE 註解與 dispatcher 過期 out-of-scope 註解已修正並 amend）
- Next action: dispatch implementer subagent for task 10.2（reach_request 計數背景聚合回填，避免逐筆 update 同一批次列熱點）。

## Session 56 — 2026-06-11 18:00
- Stage: SDD
- Task: 10.2 實作 reach_request 計數背景聚合回填（避免第二熱點）
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent 實作背景排程定期聚合 reach_task 狀態並回填 reach_request sent/failed/pending_count，避免在每筆 task 狀態變更時更新同一批次列。

## Session 57 — 2026-06-11 22:30
- Stage: SDD
- Task: 10.2 實作 reach_request 計數背景聚合回填（避免第二熱點）
- Transition: in_progress → passing
- Evidence:
  - Commits: c7bf6a7 Implement reach request count aggregation
  - Tests: 單一 set-based `UPDATE reach_request … FROM aggregated`（active EXPANDING/DISPATCHING 批次、FILTER 折疊 reach_task 狀態為 pending/sent/failed_count、`IS DISTINCT FROM` 守衛只寫變動列）+ V9 `idx_reach_task_request_status (reach_request_id, status)` 支援聚合 join；`@Scheduled(fixedDelay :5000)` tick（@EnableScheduling 由 InfrastructureConfig 全域開啟）。測試：ReachRequestCountAggregatorTest（SQL 形狀/active 述詞）、ReachRequestCountAggregationSchedulerTest（tick 委派）、ReachTaskDispatchDaoIntegrationTest（@RequiresDocker 真 Postgres：狀態分桶 PENDING/PROCESSING/RETRY_SCHEDULED→pending、SENT→sent、FAILED/DLQ→failed、CANCELLED 排除；DONE 批次不掃；markSent 不同步更新批次列直到聚合跑）。Docker-gated IT 本沙箱依 @RequiresDocker 行為 skip。
  - Spec-reviewer: ✅ Spec compliant（5/5；in-scope 場景「計數背景聚合避免熱點」THEN+AND 兩子句皆有真 DB 覆蓋、05-ER 計數欄位/enum/FK 與 V4/V9 對齊、set-based 且 scoped active 批次、grep 確認 dispatcher markSent/markFailed/scheduleRetry 無 reach_request 逐筆 counter write-back、未越界 10.3 查詢 API）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；aggregator(SQL projector)/scheduler(tick) 職責分離對齊既有 DAO/Reaper pattern、靜態參數化 SQL 無注入、hot-row 規避正確、行為層覆蓋落在 integration 層為正確分層、javadoc 同步；3 Minor：unit test 為 SQL 形狀斷言屬專案慣例、TransactionTemplate 包裝略多於單一 statement 所需但無害且與 DAO 一致、@SuppressFBWarnings(EI_EXPOSE_REP2) 與既有 idiom 一致——皆非阻擋）
- Next action: dispatch implementer subagent for task 10.3（成效查詢 API：活動彙總送達率/失敗率/狀態分布 + 單筆收件人狀態查詢），依賴 10.2。

## Session 58 — 2026-06-11 22:40
- Stage: SDD
- Task: 10.3 實作成效查詢 API（活動彙總 + 單筆收件人狀態）
- Transition: not_started → in_progress
- Next action: 派發 implementer subagent 於 reach 模組實作唯讀成效查詢 API——活動維度彙總（送達率/失敗率/各 ReachTaskStatus 人數分布，讀自 10.2 聚合的 reach_request 計數並由 reach_task 補狀態分布）與單筆收件人狀態查詢（user_id+campaign_id → 觸達狀態，PII 最小化只回狀態不回 email），對齊既有 /internal back-office OPERATOR Basic-auth 模式，完成後接 spec-reviewer 與 code-quality-reviewer。

## Session 59 — 2026-06-11 22:55
- Stage: SDD
- Task: 10.3 實作成效查詢 API（活動彙總 + 單筆收件人狀態）
- Transition: in_progress → passing
- Evidence:
  - Commits: 9b7a8a8 Implement reach metrics query API (task 10.3)（含 package-info.java stale-javadoc Minor 修正 amend）
  - Tests: reach/metrics 唯讀查詢切片——GET /internal/reach/campaigns/{id}/metrics（CampaignReachMetrics：deliveredRate=SENT/total、failedRate=(FAILED+DLQ)/total、全 7 種 ReachTaskStatus 人數分布 grouped from reach_task；total=0 不除零）+ GET /internal/reach/campaigns/{id}/recipients/{userId}（RecipientReachView：PII 最小化僅回 campaignId/userId/channel/sendCycleKey/status）。測試：CampaignReachMetricsTest（rate math 含零分母）、ReachMetricsControllerTest（MockMvc slice：回應形狀 + email/message 缺席斷言 + auth 401/403/200）、ReachMetricsDaoIntegrationTest（@RequiresDocker 真 Postgres：跨兩批次 GROUP BY status、跨活動/他人列排除、非收件人空集）。`./gradlew check` BUILD SUCCESSFUL（spotless/checkstyle/spotbugsMain/test/ArchUnit reach↛campaign/JaCoCo 全綠；Docker-gated IT 本沙箱 auto-skip）。
  - Spec-reviewer: ✅ Spec compliant（6/6；兩 in-scope 場景三層測試 name-mapped、deliveredRate/failedRate/全狀態分布齊備且零分母守衛、單筆查詢多 cycle/channel 集合 + 非收件人空集 200、PII 最小化無 email/content、05-ER 欄位/enum/索引契約符合、未動 10.2 aggregator/dispatcher、reach↛campaign 守住 campaignId 為純 UUID）
  - Code-quality-reviewer: ✅ Approved（無 Critical/Important；controller→service→DAO 分層恰當、rate math 集中且測試覆蓋、SQL 全參數化 enum cast 對齊既有 DAO、defensive copy + null guard、PII 行為層斷言、@RequiresDocker IT 真 DB 行為涵蓋負向案例、spring-boot-starter-web 於單一 :app web context 不引入第二 component-scan；1 Minor：package-info stale javadoc 已 amend 修正；slice 鏡像 security chain 因 ArchUnit 邊界為合理做法非 smell）
- Next action: Section 10（10.1–10.3）全數 passing；剩餘 not_started 任務為 11.1（PII/資料保留）與 12.1（10 萬筆壓測），非本次「Task 10」範圍——對 add-ecommerce-campaign-system 跑 final pass（`openspec validate --strict`）後可視需要 invoke spec-driven-dev:verification-before-completion。
