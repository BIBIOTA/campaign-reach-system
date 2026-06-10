# CLAUDE.md — AI 協作與工程慣例指引

> 本檔是給 AI 助手與開發者的**單一事實來源**（single source of truth）。
> 模組邊界、建置／檢查指令、CI gate 規則皆以此為準；`AGENTS.md` 是指向本檔的 symlink，請只維護這一份。

## 專案定位

電商行銷活動系統（campaign-reach-system）：一個**模組化單體**（modular monolith）後端服務，
以 Kafka 串接觸發路徑，將「活動評估」與「觸達執行」解耦。**僅後端**，無前端。

技術主軸：Spring Boot 3 / Java 21、Gradle Kotlin DSL 多模組、Kafka、PostgreSQL（Testcontainers 整合測試）。

## 模組邊界（bounded modules）

部署單元只有一個（`:app` 持有 `bootJar` 與 `@SpringBootApplication CampaignReachApplication`），
其下含三個 bounded module，套件根一律為 `com.example.campaignreach`：

| Module | Gradle | 職責 | 套件 |
| --- | --- | --- | --- |
| **campaign** | `:campaign` | 活動 API、活動 Consumer、Scheduler、Evaluators —— 決定「誰、何時、符合什麼條件」 | `…campaignreach.campaign`（`api` / `domain` / `evaluation` / `scheduler`） |
| **reach** | `:reach` | Orchestrator、AudienceResolver、Dispatcher、Channel/Email Adapter —— 執行實際觸達 | `…campaignreach.reach`（`orchestrator` / `audience` / `channel` / `dispatcher`） |
| **shared** | `:shared` | 跨模組**穩定契約**：事件 schema 與設定 | `…campaignreach.shared`（`event` / `config`） |

### 邊界規則（硬性約束）

- **campaign 與 reach 僅透過 `shared/event`（Kafka 事件）溝通，禁止彼此直接 import domain。**
  兩條觸發路徑（API 觸發、Scheduler 觸發）最終都收斂到同一個 `reach.requested` topic。
- campaign 與 reach 皆**可**依賴 `shared`；**不可**互相依賴對方的 domain。
- `shared` 只放跨模組穩定契約（`event` / `config`），**禁止**放入 campaign / reach 的
  entity / repository / service。
- 此約束由 **ArchUnit** 強制：`:app` 的
  `com.example.campaignreach.architecture.ModuleBoundaryTest`
  驗證 `campaign ↛ reach`、`reach ↛ campaign`。違反即測試失敗、擋關。

> 修改模組邊界時，務必同步更新本節與 `ModuleBoundaryTest`，避免文件與守護測試落差。

## 建置與檢查指令

所有檢查由 buildSrc convention plugin `campaignreach.java-conventions` 集中設定並套用到各 module。

| 指令 | 用途 |
| --- | --- |
| `./gradlew spotlessApply` | **本地自動格式化**（提交前先跑這個）。 |
| `./gradlew spotlessCheck` | 排版檢查。**Spotless（Palantir Java Format）是排版的單一來源**。 |
| `./gradlew checkstyleMain` | 風格檢查（google_checks 衍生、**已移除排版類模組**，不與 Spotless 重複管排版）。`maxErrors=0`。 |
| `./gradlew spotbugsMain` | 靜態分析（`effort=MAX` / `reportLevel=MEDIUM`）。**High 與 Normal 等級的 bug 即擋關**。 |
| `./gradlew test` | 單元測試 + **ArchUnit** 邊界守護 + **Testcontainers** 整合測試。 |
| `./gradlew check` | **聚合 gate**：以上全部 + JaCoCo 覆蓋率驗證（`jacocoTestCoverageVerification`）。 |

設定檔位置：
- Checkstyle：`config/checkstyle/checkstyle.xml`
- SpotBugs 排除：`config/spotbugs/exclude.xml`
- 版本集中：`gradle/libs.versions.toml`（version catalog）

> 註：`checkstyleTest` / `spotbugsTest` 為非擋關（`ignoreFailures`），Acceptance 只指向 `*Main`。

## CI gate 規則

- 觸發：**PR** 與對 `main` 的 push（`.github/workflows/ci.yml`）。
- gate 內容：runner 跑 `./gradlew check`，等同
  `spotlessCheck + checkstyleMain + spotbugsMain + test（單元 + ArchUnit + Testcontainers）+ JaCoCo 驗證`。
- **全數通過才可合併；任一失敗即擋關**（design.md §11.5）。
- **Testcontainers 整合測試需要 Docker**：GitHub-hosted runner 有 Docker，會真正執行；
  **本機無 Docker 時會自動 skip**（`@RequiresDocker` gate），不影響本地其餘檢查。

## 維護約定

當**模組邊界**或 **lint / CI 指令**有所變更時，**務必同步更新本檔**（以及對應的 build script、
`ModuleBoundaryTest`、`ci.yml`），維持單一事實來源，避免文件與 build script 落差。
