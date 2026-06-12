# Task 12.1 — Reach full-chain load-test report

> 提交進版控的**範例壓測報告**，內容為 `ReachLoadReliabilityIntegrationTest` 一次實際執行的輸出
> （真實 Testcontainers PostgreSQL，2 tests passed，主測試 wall 96.5s）。
> 每次本機重跑會把最新結果寫到 `app/build/reports/load-test/`（gitignored）；以該產出覆蓋本檔即可更新。

Baseline for evolving toward million-scale (NFR-001 / NFR-002, US-008).

## Scale
- Recipients (N): 100000
- Full 10萬筆級 run: yes

## Throughput (處理速率)
- Fan-out: 35488 tasks/sec (elapsed 2817ms)
- Dispatch: 1081 tasks/sec (elapsed 92486ms)

## Status distribution (各狀態分布)
- PENDING: 0
- PROCESSING: 0
- RETRY_SCHEDULED: 0
- SENT: 100000
- FAILED: 0
- DLQ: 0
- CANCELLED: 0

## Resource usage (資源使用)
- Wall time (fan-out + dispatch): 95304ms
- Used heap (coarse Runtime snapshot): 187MB

## Isolation (互不影響 / NFR-002)
- DB dispatch layer: concurrent workers claim disjoint rows via FOR UPDATE SKIP LOCKED — non-blocking, so a heavy campaign does not stall another's claims. The claim is channel-wide and FIFO-by-created_at; it does NOT partition by campaign. Proven by heavySendDoesNotStarveOtherCampaigns (concurrent two-worker drain).
- Request layer: per-campaign hot-partitioning is avoided by partitioning reach.requested on reach_request_id (not campaign_id) — Kafka config, design.md §9; not exercised by this DB-level load test.
