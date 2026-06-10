# Verification Report: add-ecommerce-campaign-system

Date: 2026-06-10
Verifier: claude-code (Opus 4.8) — SDD Section 3 increment

> **Scope note:** This is an **incremental section verification** for **Section 3 — Campaign domain（領域模型與持久化），tasks 3.1–3.3**, implemented via subagent-driven-development on branch `feat/section-3-campaign-domain`. Sections 1 and 2 were verified in prior increments. Sections 4–12 remain `not_started` by plan, so this run does **NOT** assert whole-change completion and does **NOT** suggest `openspec archive`.

## Summary
- Code: PASS
- Spec: PASS (verified scope)
- Progress log: PASS
- Diagrams: PASS (Section-3 structural entities) / behavioral diagrams pending their sections
- Designs: n/a (backend only — no `designs/figma.md`)

## Code Evidence

`./gradlew spotlessCheck checkstyleMain spotbugsMain` (lint / format / static analysis — *Main gate):
```
BUILD SUCCESSFUL in 819ms
34 actionable tasks: 34 up-to-date
```

`./gradlew test` (unit + ArchUnit + Testcontainers integration):
```
BUILD SUCCESSFUL in 695ms
23 actionable tasks: 23 up-to-date
```
Note: `CampaignPersistenceIntegrationTest` and `CouponPersistenceIntegrationTest` are REAL PostgreSQL Testcontainers tests gated by `@RequiresDocker`; they auto-skip in this Docker-less sandbox (expected per CLAUDE.md) and run fully on Docker-enabled CI. See Next Actions.

`openspec validate add-ecommerce-campaign-system --strict`:
```
Change 'add-ecommerce-campaign-system' is valid
exit=0
```

### Scenario coverage (Section 3 requirements → tests)
| Spec scenario | Requirement | Test method |
|---|---|---|
| 兩名營運同時編輯 | 活動編輯並發控制與稽核 (3.1) | `twoOperatorsEditingSameCampaignLaterWriterFailsOnStaleVersion`, `successfulWriteRecordsUpdatedByAndUpdatedAt` |
| (status/type enum persistence) | Campaign 聚合 (3.1) | `allStatusEnumValuesPersistExactly`, `allTypeEnumValuesPersistExactly`, `statusEnumHasExactlyTheFiveSpecValues`, `typeEnumHasExactlyTheThreeSpecValues` |
| 合法規則通過驗證後落庫 | 優惠規則 schema 驗證與版本演進 (3.2) | `validDiscountConfigPassesValidationAndSerializesWithSchemaVersion` (+ GiftAddon/FlashSale variants) |
| 不合理規則被拒絕 | 同上 (3.2) | `negativeDiscountRejectedWithReason`, `percentageOverHundredRejectedWithReason`, `endAtBeforeStartAtRejectedWithReason` |
| 滿額門檻設定 | 同上 (3.2) | `noThresholdModePersistsAndPassesValidation`, `minSpendThresholdModePersistsConfiguredThresholdAndPassesValidation` |
| 舊版 JSONB 向後相容讀取 | 同上 (3.2) | `readingOlderSchemaVersionUpcastsToCurrentDtoStructure` (+ read-boundary guard `blankOrNonObjectJsonRejectedWithReason`) |
| 設定共用碼與一人一碼 | 優惠券三層結構與使用限制 (3.3) | `sharedCodeCampaignPersistsLimitsAndSingleCode`, `uniqueCodeCampaignPersistsMultipleAssignedCodes` |
| 防同單重複核銷並控總量 | 同上 (3.3) | `duplicateRedemptionOnSameCodeUserOrderIsBlocked`, `atomicUsedCountIncrementNeverExceedsTotalUsageLimit` |

All Section 3 scenarios have matching tests. Scenarios for sections 4–12 (建立折扣活動並落為草稿、草稿需確認才排入發送、活動生命週期狀態管理、優惠計算 PromotionEvaluator、觸發判定與發出 ReachRequested、MVP 範圍界定 等) map to `not_started` tasks and are out of scope for this increment.

## Diagram Verification
| File | Type | Status | Notes |
|---|---|---|---|
| 03-class-domain-model.puml | Class | PASS (campaign portion) | `Campaign`, `CampaignType`(DISCOUNT/GIFT_ADDON/FLASH_SALE), `CampaignStatus`(DRAFT/SCHEDULED/RUNNING/PAUSED/ENDED), `RuleConfig` interface + `DiscountRuleConfig`/`GiftAddonRuleConfig`/`FlashSaleRuleConfig` all implemented and match. Evaluators (§5), rich TargetSpec/ReachPlan (§4) and the reach-module half (§7–9) are `not_started` — not contradicted. |
| 05-er-database-schema.puml | ER | PASS (campaign+coupon portion) | `campaign` + `campaign_type`/`campaign_status` enums (V1), `coupon_campaign`/`coupon_code`/`coupon_redemption` + `code_type`/`coupon_code_status` enums, FKs, `UNIQUE(coupon_code_id,user_id,order_id)`, `UNIQUE(lower(code))` (V2) — all match. `reach_request`/`reach_task`/`send_result`/`audience_list`/`audience_list_member` are §7–11 `not_started`. |
| 01-sequence-reach-flow.puml | Sequence | MANUAL-REVIEW (pending) | Describes the reach-request → fan-out → dispatch flow of §6–9; not implemented in this increment. No code contradiction. Defer verification to those sections. |
| 02-state-campaign-and-task-lifecycle.puml | State | MANUAL-REVIEW (pending) | Status enums exist, but the transition guards / auto start-end advance live in §4.2/§6.1; not implemented yet. |
| 04-component-architecture.puml | Component | MANUAL-REVIEW (pending) | Whole-system component wiring; spans §4–12. |

## Design Verification
| State | Figma node | Status | Diff |
|---|---|---|---|
| — | — | n/a | Backend-only system; no `designs/figma.md`. |

## Next Actions
- **Section 3 is complete and verified** at the code/spec/structural-diagram level: lint+static-analysis gate green, full `./gradlew test` green, all 7 Section-3 scenarios covered, `openspec validate --strict` clean, class/ER diagram entities conform.
- **Docker integration tests — RESOLVED on CI.** PR #6's first GitHub Actions run (Docker-enabled) actually executed the previously-skipped `@RequiresDocker` tests and surfaced two real defects that the local skip had hidden:
  1. `CouponPersistenceIntegrationTest.atomicUsedCountIncrementNeverExceedsTotalUsageLimit` → `TransactionRequiredException`: the `@Modifying` `tryIncrementUsedCount` had no transactional boundary. Fixed by annotating it `@Transactional` (propagation REQUIRED) — commit `9121bde`.
  2. `CampaignPersistenceIntegrationTest.successfulWriteRecordsUpdatedByAndUpdatedAt` → assertion failure: compared an in-memory nanosecond `Instant` against a DB-roundtripped microsecond `timestamptz`. Fixed by baselining `created_at` from the DB re-read — commit `9121bde`.
  Re-run (`27264878795`) is **green** (`:app:test` ran the real PostgreSQL containers in ~2m53s, through JaCoCo verification). This confirms `@RequiresDocker` truly runs on GitHub Actions.
- **Do NOT `openspec archive`** — the change is intentionally incomplete (Sections 4–12 `not_started`). Continue the SDD pipeline with Section 4 (Campaign API CRUD & lifecycle) when ready.
- Tracked follow-ups recorded in tasks.md: `CurrentOperator` thread-local finally-clear when the security filter lands (3.1); Campaign period/`endAt<startAt` invariants on the aggregate (3.1/3.2); `per_user_limit` enforcement + coupon code input validation at the redemption/API trust boundary (3.3).
