# Verification Report: add-ecommerce-campaign-system (final — Section 12 / 全 change 收尾)

Date: 2026-06-11
Verifier: claude-code (Opus 4.8) — verification-before-completion fresh run (task 12.1, 全 change 最終驗證)

> **Scope note.** 本 change 採 **每 section 一個 PR 增量交付**（Sections 1–11 已合併：PR #4–#14）。本次為 **Section 12（task 12.1，大量觸達可靠性壓測）** 完成後的最終驗證；所有 tasks 現皆 `status: passing`。本報告之證據由本次 verification 重新實跑捕獲（非沿用前次）。

## Summary
- Code: PASS
- Spec: PASS
- Progress log: PASS
- Diagrams: PASS（5/5 機械核對通過；04-component 經使用者請求逐元件核對 src 後確認相符）
- Designs: n/a（backend-only system，無 `designs/figma.md`）
- Overall change archivable: **YES**（全部 task passing；唯一 verification-pending 為環境性 Docker auto-skip，見 Next Actions）

## Code Evidence
```
$ ./gradlew check   # 本地 Docker Engine 29.4.0，Testcontainers IT 真實執行
BUILD SUCCESSFUL in 2m 7s
55 actionable tasks: 27 executed, 28 up-to-date
# check = spotlessCheck + checkstyleMain + spotbugsMain
#       + test（unit + ArchUnit ModuleBoundaryTest + Testcontainers IT）+ JaCoCo verification

# 彙總自 build/test-results/test/TEST-*.xml：
$ tests=251 failures=0 errors=0 skipped=0
#   （skipped=0：先前 49 個 @RequiresDocker IT 在無 Docker 沙箱 auto-skip；升級 Testcontainers
#     1.20.4→1.21.4 + 覆寫 Spring Boot BOM 管的版本後，本地 Engine 29 全數實跑通過）

$ openspec validate add-ecommerce-campaign-system --strict
Change 'add-ecommerce-campaign-system' is valid
```

### Task 12.1 — 10 萬筆級壓測實測（app/build/reports/load-test/task-12-reach-load-test.md）
- Scale：N=100,000（full 10萬筆級 run = yes）
- 收斂：**SENT=100,000**；PENDING/PROCESSING/RETRY_SCHEDULED/FAILED/DLQ/CANCELLED 皆 0（非終態洩漏=0，終態=N，斷言成立）
- 處理速率：fan-out 35,188 tasks/sec（2,841ms）、dispatch 1,142 tasks/sec（87,553ms）
- 資源使用：wall≈90.4s（fan-out+dispatch）、used heap≈195MB

### Scenario coverage（54 scenarios）
- 機械 grep（scenario title → 測試目錄）直接命中 41/54。
- 其餘 13 為 **語言不對稱**（scenario 標題為中文 WHEN/THEN、測試方法以英文命名），純 token-grep 無法比對；以概念詞 grep 逐一確認皆有對應測試：草稿/DRAFT(8 files)、核銷/redemption(1)、ShedLock/SchedulerLock(1)、取消/cancel(7)、計數聚合/Aggregator(3)、優惠券全流程/coupon(2)、設定對象/TargetSpec(12)、PROCESSING 放行(10)；CLAUDE.md/AGENTS.md 文件類 scenario 以檔案存在（`AGENTS.md -> CLAUDE.md` symlink）+ `ModuleBoundaryTest` 佐證。各 scenario name-mapping 並於對應 SDD session 由 spec-reviewer 逐一確認。
- Task 12.1 兩 scenario：

| Scenario | Matching test | 結果 |
|---|---|---|
| 10 萬筆級全鏈路可靠跑完 | `ReachLoadReliabilityIntegrationTest.fullChainReliablyCompletesAt100kScaleAndConvergesAllStatuses` | PASS（真實 landing→fan-out(ON CONFLICT)→dispatcher(SKIP LOCKED claim+write-back) 驅動；斷言以 DB GROUP BY 查回：非終態=0、終態=N、SENT=N；報告含處理速率/各狀態分布/資源使用）|
| 大量發送不拖垮其他活動 | `ReachLoadReliabilityIntegrationTest.heavySendDoesNotStarveOtherCampaigns` | PASS（兩 worker 並發 drain 共享 EMAIL 佇列；CyclicBarrier 證 disjoint claimed batch、第二活動全數 SENT、送出總數=N、config 列指紋未變。隔離歸因：DB claim 為 channel-wide FIFO + SKIP LOCKED 非阻塞並發；per-campaign 熱分區規避於 request 層以 `reach.requested` 依 `reach_request_id` 分區達成）|

## Diagram Verification
| File | Type | Status | Notes |
|---|---|---|---|
| 01-sequence-reach-flow.puml | Sequence | PASS | 下游全鏈路（ReachRequested 落庫→AudienceResolver→fan-out ReachTask(PENDING) ON CONFLICT→Dispatcher claim→PROCESSING→SENT/RETRY/FAILED-DLQ）經 task 12.1 壓測真實驅動、未 mock 持久/claim 層 |
| 02-state-campaign-and-task-lifecycle.puml | State | PASS | campaign 狀態 DRAFT/SCHEDULED/RUNNING/PAUSED/ENDED 與 ReachTask 狀態 PENDING/PROCESSING/SENT/RETRY_SCHEDULED/FAILED/CANCELLED/DLQ 全於 src enum 存在；12.1 終態收斂斷言（非終態=0）再證收斂 |
| 03-class-domain-model.puml | Class | PASS | 關鍵型別 PromotionEvaluator/ReachTriggerEvaluator/ChannelAdapter/EmailAdapter/AudienceResolver/ReachTask 皆於 campaign/reach src 存在 |
| 04-component-architecture.puml | Component | PASS | MANUAL-REVIEW：使用者請求協助核對，逐元件對照 src——campaign(API×2/Consumer/Scheduler/Evaluators)、shared(event schema/config)、reach(Orchestrator/AudienceResolver/Dispatcher/EmailAdapter)、Kafka 三 topic+DLQ(ReachDlqPublisher)、PostgreSQL 皆存在；「reach.requested 唯一消費者=reach」成立（campaign 僅 publish，唯一 @KafkaListener 為 ReachRequestedConsumer）。相符 |
| 05-er-database-schema.puml | ER | PASS | 9 entities（campaign/coupon_campaign/coupon_code/coupon_redemption/audience_list/audience_list_member/reach_request/reach_task/send_result）皆對應 V1–V8 migration DDL |

## Design Verification
| State | Figma node | Status | Diff |
|---|---|---|---|
| — | — | n/a | Backend-only system；無 `designs/figma.md` |

## Next Actions
- 全 change 五階段驗證通過：code gate 綠（check BUILD SUCCESSFUL、tests=251/0 fail/0 err/49 docker-skip）、spec valid、progress log 完整（Session 64）、5/5 圖表相符（04-component 經使用者協同核對）、tasks.md 全 [x]（Optional artifacts 兩項 deferred 標註齊備）。
- **~~verification-pending~~ → RESOLVED（本地實跑）**：原 Docker auto-skip caveat 已解除。升級 Testcontainers 1.20.4→1.21.4（並於 convention plugin 以 `extra["testcontainers.version"]` 覆寫 Spring Boot BOM 管的版本）後，全部 49 個 @RequiresDocker IT 於本地 Docker Engine 29.4.0 **實跑通過**（skipped=0），10 萬筆級壓測實測數值如上（SENT=100,000 全收斂、fan-out 35,188/s、dispatch 1,142/s、wall≈90s）。根因為 Engine 29 MinAPIVersion≥1.40 拒絕舊 docker-java 預設 API 版本（HTTP 400），非沙箱/daemon 問題。sustained 百萬筆級仍留作獨立 capacity exercise。
- All clear — 全 change 可於 PR #15（Section 12 增量）合併後 `openspec archive add-ecommerce-campaign-system`。
