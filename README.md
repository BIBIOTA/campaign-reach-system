# campaign-reach-system

> 讓電商行銷團隊**自己設定活動、自己決定發給誰、由系統可靠地把訊息送出去**，並看得到成效的行銷活動平台後端。

[![CI](https://github.com/BIBIOTA/campaign-reach-system/actions/workflows/ci.yml/badge.svg)](https://github.com/BIBIOTA/campaign-reach-system/actions/workflows/ci.yml)
[![API Docs](https://img.shields.io/badge/API%20Docs-GitHub%20Pages-blue)](https://bibiota.github.io/campaign-reach-system/)

---

## 1. 這個專案是什麼

### 解決什麼問題

過去公司推行銷活動時，**優惠設定、對象圈選、訊息發送分散在不同工具與流程，且常需要工程介入**。
結果是：活動上線慢、行銷人員無法自助操作、觸達分散難以管理、發送成效也看不見。

campaign-reach-system 為電商建立一套**統一的行銷活動平台**，把這些能力收斂到一個後端服務，
讓行銷／營運人員不靠工程就能把活動做完整、把訊息穩定送達、並回頭檢視成效。

### 帶來的商業價值

- **行銷自助、縮短上線前置時間** — 行銷人員自助上線折扣／優惠券活動（首波），不再每檔活動都排工程。
- **統一且可靠的觸達** — 可**排程定時發送**，也可**依使用者行為即時觸發**（如棄購提醒），訊息穩定送出。
- **成效全程可追蹤** — 每位收件人是否送達、活動整體送達率／失敗率一目了然，做為活動歸因依據。
- **能隨規模成長** — 設計上可演進至單次百萬筆級觸達；MVP 已以 10 萬筆級壓測驗證流程可靠跑通。
- **以營收為終點** — 最終以活動帶來的轉換／營收衡量成效，系統提供可歸因的活動與觸達資料。

### 服務對象

- **主要：行銷／營運人員** — 設定活動內容、優惠規則、觸達對象與發送時機，並查看成效。
- **次要：內部系統與團隊** — 電商主站（提供使用者行為以觸發活動）、後台前端團隊（呼叫本系統 API）。

### 範圍

本期交付**後端能力**（提供 API 供後台前端與其他內部系統介接），**不含**消費者前台與後台操作介面。
活動由「優惠規則設定」與「觸達發送設定」兩部分組成，可各自獨立演進；首波聚焦折扣／優惠券活動與 Email 通道，
其餘活動類型與通道在設計上預留擴充。

### MVP 已交付的功能範圍

MVP 完整交付**折扣／優惠券活動的全流程**（建立 → 觸達 → 追蹤）。下表對應 PRD 的功能需求（FR）：

| 領域 | 已實作功能 |
| --- | --- |
| **優惠規則設定** | 活動 CRUD（名稱、起訖時間、優惠類型、內容、門檻、使用限制）；支援**固定金額折抵／百分比折扣／優惠券代碼**三種優惠類型；滿額門檻（無門檻／滿額可用）；使用限制（每人限用次數、活動總次數上限）；儲存時驗證合理性（折扣非負、百分比 ≤100%、結束不早於開始），不通過則拒絕並回報原因。 |
| **觸達發送設定** | 設定觸達對象（指定名單，或會員等級／地區等簡單條件圈選）；**排程定時**與**使用者行為觸發**兩種時機；Email 為首波通道，介面預留其他通道；優惠規則與觸達設定可分別儲存／修改。 |
| **活動生命週期** | 狀態機（草稿／排程／進行中／暫停／結束）只允許合理切換；依起訖時間**自動進入「進行中」與「結束」**；活動暫停／結束時**取消尚未送出的觸達**。 |
| **可靠送達** | 圈選對象**展開為個別收件人**逐一發送並記錄每筆狀態；同活動對同人同週期**不重複發送**；暫時性失敗**自動重試**、確定失敗標記不再重試；重試耗盡仍失敗者進**DLQ 獨立保留**供人工檢視重送；Email 服務中斷時 circuit breaker **穩定降級**。 |
| **成效追蹤** | 活動維度彙總（送達率／失敗率／各狀態人數分布）與單筆收件人狀態查詢。 |
| **大量觸達驗證** | 以壓測驗證單次活動 **10 萬筆級**全鏈路可靠跑完，且大量發送不拖垮其他活動，並產出壓測報告（見 [§7](#7-測試覆蓋率與壓測報告)）。 |

**本期不做（保留擴充能力）**：滿額贈禮／加價購、限時特賣／閃購等其他活動類型；Email 以外通道（簡訊／App 推播／LINE）的實際發送；進階分眾／標籤引擎；點數／會員集點活動；消費者前台與後台操作介面；百萬筆級實測（屬演進目標，非 MVP 驗收）。

> 設計上的一句話分工：**「活動」決定什麼活動要觸發；「觸達」決定要發給誰、怎麼發、何時發。**
> 詳細需求見 [prd.md](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/prd.md)，技術架構見 [§4 技術棧](#4-技術棧) 與 [design.md](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/design.md)。

---

## 2. 文件連結

本專案採 **SDD（Spec-Driven Development）** 流程，所有設計與規格文件都隨 change 保存在 `openspec/` 下。

| 文件 | 連結 |
| --- | --- |
| 📐 **設計文件（design.md）** | [design.md](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/design.md) |
| 📋 **產品需求（prd.md）** | [prd.md](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/prd.md) |
| 🌐 **API 文件（Swagger UI / OpenAPI）** | <https://bibiota.github.io/campaign-reach-system/> |

### UML diagrams

| 圖 | 類型 | 連結 |
| --- | --- | --- |
| 觸達全鏈路流程 | Sequence | [01-sequence-reach-flow.puml](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/diagrams/01-sequence-reach-flow.puml) · [png](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/diagrams/01-sequence-reach-flow.png) |
| 活動 / Task 生命週期 | State | [02-state-campaign-and-task-lifecycle.puml](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/diagrams/02-state-campaign-and-task-lifecycle.puml) · [png](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/diagrams/02-state-campaign-and-task-lifecycle.png) |
| Domain Model | Class | [03-class-domain-model.puml](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/diagrams/03-class-domain-model.puml) · [png](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/diagrams/03-class-domain-model.png) |
| 元件架構 | Component | [04-component-architecture.puml](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/diagrams/04-component-architecture.puml) · [png](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/diagrams/04-component-architecture.png) |
| 資料庫 schema | ER | [05-er-database-schema.puml](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/diagrams/05-er-database-schema.puml) · [png](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/diagrams/05-er-database-schema.png) |

> 其他規格：[proposal.md](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/proposal.md) ·
> [tasks.md](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/tasks.md) ·
> [specs/](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/specs/) ·
> [verification-report.md](openspec/changes/archive/2026-06-11-add-ecommerce-campaign-system/verification-report.md)

---

## 3. 開發方式（SDD 流程）

本專案以一套**自訂的 Spec-Driven Development 流程**開發 —
參見 [BIBIOTA/yuki-marketplace](https://github.com/BIBIOTA/yuki-marketplace)。

每個變更（change）都以 `openspec/changes/{change-id}/` 為單位，依序產出並驗證 artifact：

```
brainstorm → prd.md → design.md → tasks.md → UML/diagrams →
proposal + specs/ → 實作（TDD / subagent-driven）→ verification-report → archive
```

核心原則：

- **先寫 spec、再寫 code**：design.md / prd.md / specs 為單一事實來源；實作必須對齊 scenario 與 UML 契約。
- **增量交付**：以「每 section 一個 PR」推進（本期 Sections 1–12 對應 PR #4–#14）。
- **驗證前不宣稱完成**：合併前跑五階段 `verification-before-completion`（lint+test、`openspec validate --strict`、
  diagram 契約一致性、Figma 狀態一致性、deferred 項），全通過才可 `openspec archive`。
- **文件即治理**：模組邊界 / lint / CI 規則變動時，[`CLAUDE.md`](CLAUDE.md)、build script、`ModuleBoundaryTest`、
  `ci.yml` 必須同步更新，避免文件與建置漂移。

---

## 4. 技術棧

| 面向 | 選型 |
| --- | --- |
| 語言 / 執行環境 | **Java 21**（JVM toolchain 鎖定 21） |
| 框架 | **Spring Boot 3.4**（Web / Data JPA / Security / Validation / Kafka） |
| 建置工具 | **Gradle（Kotlin DSL）** 多模組 + version catalog（`gradle/libs.versions.toml`） |
| 訊息佇列 | **Apache Kafka**（`spring-kafka`；模組間事件邊界，永不 mock broker） |
| 資料庫 | **PostgreSQL**，schema 由 **Flyway** migration 擁有（Hibernate `ddl-auto: none`） |
| 排程鎖 | **ShedLock**（JdbcTemplate LockProvider，去重多實例 `@Scheduled` 掃描） |
| 韌性 | **Resilience4j**（circuit breaker 包住 EmailAdapter，供應商不可用時穩定降級） |
| API 文件 | **springdoc-openapi 2.7**（`/v3/api-docs` + `/swagger-ui`）+ 繁中 `@Operation`/`@Schema` 標註 |
| 格式化 | **Spotless + Palantir Java Format**（單一 formatting 事實來源） |
| 靜態檢查 | **Checkstyle**（google_checks 去除 layout）、**SpotBugs**（effort=MAX，High/Normal 阻擋） |
| 架構守門 | **ArchUnit**（模組邊界 `ModuleBoundaryTest`） |
| 測試 | **JUnit 5** + **Testcontainers**（真實 Kafka + PostgreSQL 整合測試） |
| 覆蓋率 | **JaCoCo**（`jacocoTestCoverageVerification` 納入 gate） |

---

## 5. 專案啟動方式

### 先決條件

- JDK 21（專案已用 Gradle toolchain 鎖定；只需本機可取得 JDK 21）
- **Docker**（本機開發以 `docker-compose.yml` 起 PostgreSQL + Kafka；整合測試的 Testcontainers 也需要）

### 1. 啟動本機基礎設施（PostgreSQL + Kafka）

repo 根目錄附帶 `docker-compose.yml`，一鍵起好 app 執行期需要的兩個後端服務
（PostgreSQL 與 KRaft 模式的 Kafka，免 ZooKeeper）。資料存放於具名 volume，
`restart: unless-stopped` 會在重開機後自動拉起，直到你明確 `down`。

```bash
docker compose up -d        # 啟動（資料持久化於具名 volume）
docker compose down         # 停止，保留資料
docker compose down -v      # 停止並清除資料
```

> 此 compose 僅供**本機開發**；整合測試走 Testcontainers，不使用本檔。

### 2. 設定環境變數

`application.yml` 不含任何預設機密，缺值即 fail-fast。專案提供 `.env.example` 範本，
其預設值與上面的 compose 對齊；複製為 `.env`（已被 gitignore）後再載入 shell：

```bash
cp .env.example .env
# 若預設 port（5432 / 9092）已被占用，改 .env 內的 POSTGRES_PORT / KAFKA_PORT
# 與對應的 DB_URL / KAFKA_BOOTSTRAP_SERVERS 即可

set -a; source .env; set +a   # 將 .env 載入當前 shell
```

`.env` 涵蓋 DB（`DB_URL` / `DB_USERNAME` / `DB_PASSWORD`）、Kafka
（`KAFKA_BOOTSTRAP_SERVERS`）、Email provider（`EMAIL_PROVIDER_API_KEY`，本機 smoke 測試給
dummy 值即可）與後台 operator 帳號（`OPERATOR_USERNAME` / `OPERATOR_PASSWORD` / `OPERATOR_ID`）。

### 3. 啟動應用

```bash
# 開發模式（hot run）
./gradlew :app:bootRun

# 或先打包再執行
./gradlew :app:bootJar
java -jar app/build/libs/app-*.jar
```

啟動後（預設 `:8080`；若 8080 已被占用可加 `--args='--server.port=8081'`）：

- 內部 REST API：`http://localhost:8080/internal/campaigns`（HTTP Basic，需 `OPERATOR` 角色）
- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI 規格：`http://localhost:8080/v3/api-docs`

快速 smoke test（建立一筆草稿活動，預期回 `201`）：

```bash
curl -u "$OPERATOR_USERNAME:$OPERATOR_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"name":"夏季全館 9 折","type":"DISCOUNT","startAt":"2026-07-01T00:00:00Z","endAt":"2026-07-31T23:59:59Z","ruleConfig":{"ruleType":"DISCOUNT","schema_version":2,"kind":"PERCENTAGE","percentage":10,"thresholdMode":"NONE"},"targetSpec":{"kind":"CONDITION","conditions":{"memberTier":"GOLD"}},"reachPlan":{"channel":"EMAIL","templateRef":"summer-sale-email","timing":"SCHEDULED"}}' \
  http://localhost:8080/internal/campaigns
```

> 線上版 API 文件（GitHub Pages）：<https://bibiota.github.io/campaign-reach-system/>

### 4. 本機寄信 smoke test（Mailpit）

走完整 EMAIL 觸達鏈路（建立活動 → 推進為進行中 → 排程掃描發出 `reach.requested` → orchestrator 展開 →
dispatcher 認領 → EmailAdapter → 本機 SMTP provider → Mailpit），最後在 Mailpit UI 看到攔截下來的信。

`docker-compose.yml` 已內含 **Mailpit**（本機 SMTP 收信槽）；`.env.example` 也已備妥本機寄信所需變數。

> **本機寄信的限制（務必知道）**：Mailpit 只在本機**攔截**所有信件，**永不轉發給任何真實收件人**，
> 可安心反覆測試而不會打擾任何人。此外，本機模式下每封 EMAIL 觸達都固定寄到
> `LOCAL_SMTP_RECIPIENT`（預設 `local-inbox@local.test`）這一個 smoke-test 信箱，
> **不論活動實際圈到誰**——這是刻意的本機開發簡化，**非正式環境行為**。

**1. 啟動 compose（含 Mailpit）並載入 `.env`**

依本節 [步驟 1](#1-啟動本機基礎設施postgresql--kafka)／[步驟 2](#2-設定環境變數) 起好基礎設施並載入環境變數；
`docker compose up -d` 會一併起 Mailpit。`.env.example` 已預設 `SPRING_PROFILES_ACTIVE=local` 與
`EMAIL_PROVIDER_MODE=smtp-local`，並把 `LOCAL_SMTP_*` 指向 Mailpit（`localhost:1025`）——
本機 SMTP provider 唯有在 `local` profile **且** mode=`smtp-local` 同時成立時才會接線，故其他環境永不漏信。

```bash
docker compose up -d            # 起 PostgreSQL + Kafka + Mailpit
set -a; source .env; set +a     # 載入本機寄信所需變數（含 local profile / smtp-local）
```

**2. 以本機 SMTP 模式啟動應用**

```bash
./gradlew :app:bootRun          # 沿用已載入的 local profile + smtp-local
```

**3. 觸發一筆 EMAIL 觸達**

EMAIL 觸達由「排程掃描」驅動：reach-scan 掃描器每分鐘掃一次 **RUNNING** 活動，當下時間落在活動
`[startAt, endAt)` 窗內就發出一筆 `reach.requested`。因此需建立一個 **reachPlan 通道為 EMAIL、且時間窗涵蓋當下**的活動，
再把它推進到 RUNNING。下列 curl 用 `OPERATOR` 帳號（HTTP Basic，見 `.env` 的 `OPERATOR_USERNAME`/`OPERATOR_PASSWORD`），
也可改用 Postman collection（`docs/postman/campaign-reach.postman_collection.json`）的「建立活動」與「活動狀態轉換」請求。

```bash
# 建立活動（DRAFT）；起訖時間需涵蓋當下，回 201 並帶新活動 id
CAMPAIGN_ID=$(curl -s -u "$OPERATOR_USERNAME:$OPERATOR_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"name":"本機寄信 smoke test","type":"DISCOUNT","startAt":"2026-01-01T00:00:00Z","endAt":"2030-12-31T23:59:59Z","ruleConfig":{"ruleType":"DISCOUNT","schema_version":2,"kind":"PERCENTAGE","percentage":10,"thresholdMode":"NONE"},"targetSpec":{"kind":"CONDITION","conditions":{"memberTier":"GOLD"}},"reachPlan":{"channel":"EMAIL","templateRef":"summer-sale-email","timing":"SCHEDULED"}}' \
  http://localhost:8080/internal/campaigns | sed -E 's/.*"id":"([^"]+)".*/\1/')

# 推進 DRAFT → SCHEDULED（version 為上一步讀到的版本，初始為 0）
curl -u "$OPERATOR_USERNAME:$OPERATOR_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"targetStatus":"SCHEDULED","version":0}' \
  http://localhost:8080/internal/campaigns/$CAMPAIGN_ID/status

# 推進 SCHEDULED → RUNNING（version 隨上一步 +1 為 1）
curl -u "$OPERATOR_USERNAME:$OPERATOR_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"targetStatus":"RUNNING","version":1}' \
  http://localhost:8080/internal/campaigns/$CAMPAIGN_ID/status
```

活動進入 RUNNING 後，**觸達是非同步的**（reach-scan 掃描 → Kafka → orchestrator → dispatcher），
信件會在下一次掃描（預設每分鐘一次）後**稍候才**送進 Mailpit，並非即時。可輪詢成效彙總端點確認觸達已落地：

```bash
# 活動維度成效彙總；SENT 數出現即代表信已送進 Mailpit
curl -u "$OPERATOR_USERNAME:$OPERATOR_PASSWORD" \
  http://localhost:8080/internal/reach/campaigns/$CAMPAIGN_ID/metrics
```

**4. 在 Mailpit 檢視攔截到的信**

打開 <http://localhost:8025>（Mailpit UI，port 由 `.env` 的 `MAILPIT_UI_PORT` 決定，預設 8025），
即可看到剛剛這封寄到 `local-inbox@local.test` 的 EMAIL 觸達內容；若還沒出現，稍等一個掃描週期再重新整理。

### 5. 本機 EMAIL 端到端驗收（Newman + Mailpit API）

若要用腳本驗證完整本機鏈路，可執行 Newman 驗收流程。這條流程會：

1. seed 一組本機 static audience list / member（只含 UUID，不含 email）。
2. 清空 Mailpit mailbox，避免舊信污染本次驗收。
3. 跑 Postman collection 的 `local/manual EMAIL e2e acceptance` folder：
   建立 EMAIL 活動 → 推進到 RUNNING → 輪詢 metrics 直到 `SENT` → 呼叫 Mailpit HTTP API
   `GET /api/v1/messages` 斷言信件 subject 包含 `[Local Campaign Reach]` 與本次 `templateRef`。

先啟動本機 stack，載入 `.env`，並以 `local` profile / `smtp-local` 啟動 app：

```bash
docker compose up -d
cp .env.example .env        # 若尚未建立 .env
set -a; source .env; set +a
./gradlew :app:bootRun
```

在另一個 shell 載入相同 `.env` 後執行：

```bash
set -a; source .env; set +a
docs/scripts/run-local-email-e2e.sh
```

腳本會使用 `docs/postman/local-email-e2e.postman_environment.json` 的本機預設值，並可用環境變數覆寫：

```bash
BASE_URL=http://localhost:8080 \
MAILPIT_BASE_URL=http://localhost:8025 \
E2E_MAX_POLL_ATTEMPTS=60 \
E2E_POLL_INTERVAL_MS=2000 \
docs/scripts/run-local-email-e2e.sh
```

> 這是**本機 / 手動端到端驗收**，不是 CI gate 的一部分；CI 仍跑 `./gradlew check`。
> 驗收依賴本機 Mailpit，且 EMAIL provider 固定寄到 `LOCAL_SMTP_RECIPIENT`
> （預設 `local-inbox@local.test`），不會寄到真實外部收件人。

---

## 6. 測試方式

```bash
# 自動格式化（commit 前先跑）
./gradlew spotlessApply

# 個別檢查
./gradlew spotlessCheck      # 格式
./gradlew checkstyleMain     # 風格（maxErrors=0）
./gradlew spotbugsMain       # 靜態分析（High/Normal 阻擋）
./gradlew test               # 單元 + ArchUnit 邊界 + Testcontainers 整合測試

# 聚合 gate（CI 等效）
./gradlew check              # 上述全部 + JaCoCo 覆蓋率驗證
```

**CI gate**（`.github/workflows/ci.yml`，PR 與 push `main` 觸發）執行 `./gradlew check`，
任一項失敗即阻擋合併。

> **Docker 與整合測試**：Testcontainers 整合測試需 Docker。GitHub runner 具 Docker 會完整執行；
> 本機無 Docker 時 `@RequiresDocker` 自動 skip，不影響其他檢查。

---

## 7. 測試覆蓋率與壓測報告

### 覆蓋率（JaCoCo）

每個模組透過 convention plugin `campaignreach.java-conventions` 套用 JaCoCo report +
最低覆蓋率 gate（`jacocoTestCoverageVerification`），並納入 `./gradlew check`。
各模組可用 `jacocoMinCoverage` property 設定 instruction-coverage 下限（例如 `:shared` 設為 **0.70**）。

本機以 `./gradlew jacocoTestReport` 產出（各模組 `build/reports/jacoco/test/`）。實測各模組覆蓋率：

| 模組 | Instruction | Branch | Line | covered/total（instr） |
| --- | --- | --- | --- | --- |
| `:campaign` | 80.6% | 66.5% | 78.9% | 2302 / 2856 |
| `:reach` | 68.1% | 59.9% | 71.1% | 2409 / 3536 |
| `:shared` | 89.3% | 75.9% | 87.0% | 459 / 514 |
| `:app` | 94.2% | — | 89.5% | 81 / 86 |
| **合計** | **75.1%** | **64.0%** | **75.8%** | **5251 / 6992** |

> 數據為各模組 `jacocoTestReport` 之 instruction/branch/line 覆蓋率。
> `:reach` 數字僅反映 `:reach` 自身測試任務；部分跨模組行為（如 10 萬筆級全鏈路）由 `:app` 整合測試驅動，
> 其執行未計入 `:reach` 的 per-module 報告，故 `:reach` 實際被測程度高於上表。

### 壓測報告（Task 12.1 — 10 萬筆級全鏈路可靠性）

以 `ReachLoadReliabilityIntegrationTest` 在真實 Testcontainers（PostgreSQL）上驅動完整鏈路
（landing → fan-out `ON CONFLICT` → dispatcher `FOR UPDATE SKIP LOCKED` claim + write-back），
斷言以 DB `GROUP BY` 查回最終狀態分布。

| 指標 | 數值 |
| --- | --- |
| 收件人規模（N） | **100,000** |
| Fan-out 吞吐 | ~37,900 tasks/sec |
| Dispatch 吞吐 | ~1,130 tasks/sec |
| Wall time（fan-out + dispatch） | ~91 s |
| 最終狀態 | **SENT = 100,000**；PENDING/PROCESSING/RETRY/FAILED/DLQ = 0（全收斂） |
| Used heap（粗略快照） | ~173 MB |

**隔離性（NFR-002，大量發送不拖垮其他活動）**：DB dispatch 層以 channel-wide FIFO + `SKIP LOCKED`
非阻塞並發 claim，重活動不會卡住他活動的 claim；request 層則以 `reach.requested` 依 `reach_request_id`
分區規避 per-campaign 熱分區。由 `heavySendDoesNotStarveOtherCampaigns`（兩 worker 並發 drain）證實。

> 完整報告：[`app/build/reports/load-test/task-12-reach-load-test.md`](app/build/reports/load-test/task-12-reach-load-test.md)（執行壓測後產生）。
> 註：MVP 以 10 萬筆級壓測驗收；sustained 百萬筆級屬設計演進目標，留作獨立 capacity exercise。
