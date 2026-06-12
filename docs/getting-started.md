# 專案啟動方式

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

若要用腳本驗證完整本機鏈路，可執行 Newman 驗收流程。`docs/scripts/run-local-email-e2e.sh`
會先做兩步前置，再交給 Newman 跑 Postman collection 的 `local/manual EMAIL e2e acceptance` folder。

**前置（由 shell 腳本完成）**

1. 透過 `docker compose exec postgres psql` seed 一組本機 static audience list / member
   （只含 UUID，不含 email；對應 `E2E_AUDIENCE_LIST_ID` / `E2E_USER_ID`，以 upsert 方式可重複執行）。
2. 呼叫 Mailpit HTTP API `DELETE /api/v1/messages` 清空 mailbox，避免舊信污染本次驗收。

**驗收流程（Newman 依序跑 5 個請求）**

| # | 步驟 | 方法與 API | 說明 |
| --- | --- | --- | --- |
| 1 | 建立 EMAIL 活動 | `POST /internal/campaigns` | 以 static list 為 target、`reachPlan.channel=EMAIL` 建立 DRAFT 活動；`startAt` 刻意設在**過去**、`endAt` 在未來以涵蓋當下，回傳活動 `id` 與 `version`。 |
| 2 | DRAFT → SCHEDULED | `POST /internal/campaigns/{id}/status` | 帶 `targetStatus=SCHEDULED` 與樂觀鎖 `version`，推進生命週期。**這是流程中唯一的手動狀態轉換。** |
| 3 | 輪詢 metrics 直到 `SENT` | `GET /internal/reach/campaigns/{campaignId}/metrics` | 以 `E2E_MAX_POLL_ATTEMPTS` / `E2E_POLL_INTERVAL_MS` 為上限做輪詢（非一次性 sleep），等排程驅動的非同步鏈路把 EMAIL 任務推到 `SENT`。 |
| 4 | 斷言 Mailpit 收到信 | `GET /api/v1/messages`（Mailpit） | metrics 報 `SENT` 後讀 Mailpit，斷言信件 subject 同時包含 `[Local Campaign Reach]` 與本次動態 `templateRef`。 |
| 5 | 清理提示 | `GET /api/v1/messages`（Mailpit） | 非破壞性提示，提醒重跑前先清空 Mailpit inbox。 |

> **為何沒有「手動轉 RUNNING」這一步？** 因為步驟 1 的 `startAt` 設在過去，`CampaignLifecycleScheduler`
> 會在活動轉成 SCHEDULED 後**自動**把它推進到 RUNNING（這也是真實 operator 流程——沒有人會手動切 RUNNING），
> reach-scan 隨即對 RUNNING 活動觸發寄送。若再加一個手動 `SCHEDULED → RUNNING` 請求，會與排程器競爭同一條
> 狀態邊，並因 version 過期而回 409。步驟 3 的 metrics 輪詢即用來等待這條排程驅動的結果。
>
> 上述 `/internal/*` 端點皆需 operator basic auth（`OPERATOR_USERNAME` / `OPERATOR_PASSWORD`）；
> Mailpit `/api/v1/*` 為本機收信槽 API，不需認證。整條鏈路涵蓋
> 建立活動 → 手動推進 SCHEDULED → 排程器自動轉 RUNNING → 排程掃描發出 `reach.requested` →
> orchestrator 展開 → dispatcher 認領 → EmailAdapter → 本機 SMTP → Mailpit。

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

> **port 8080 被佔用時**：若 app 以 `--server.port=8081` 啟動（或其他 port），
> 需以 `BASE_URL` 告知腳本實際位址：
>
> ```bash
> BASE_URL=http://localhost:8081 docs/scripts/run-local-email-e2e.sh
> ```

其他可覆寫的參數（預設值與 `local-email-e2e.postman_environment.json` 一致）：

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
