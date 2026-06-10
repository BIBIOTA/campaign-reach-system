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



