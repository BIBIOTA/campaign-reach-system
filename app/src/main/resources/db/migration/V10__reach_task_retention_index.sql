-- Task 11.1 — supporting index for the data-retention purge scan (NFR-005, spec「觸達稽核軌跡屆期歸檔或刪除」).
-- Flyway is the schema owner (Hibernate ddl-auto: none). The retention purge (ReachAuditTrailPurger)
-- deletes terminal-state reach_task rows older than a cutoff: WHERE status IN (terminal) AND created_at < cutoff.
--
-- The existing idx_reach_task_dispatch (status, next_retry_at, created_at) leads with status but then
-- orders by next_retry_at, so it cannot range-scan created_at directly for this predicate. This
-- composite (status, created_at) lets the purge probe each terminal status and range-scan created_at
-- for the aged rows, instead of a full table scan on each (coarse, hourly) sweep.
--
-- Plain (non-CONCURRENT) CREATE INDEX: this is initial schema setup — reach_task is created in the
-- V-series migrations and is empty when V10 runs, so the brief SHARE lock a plain build takes is a
-- non-event (no rows to scan, no writers to block). CONCURRENTLY was tried but it cannot run inside a
-- transaction and, in the Testcontainers integration suite (one shared Postgres, several cached Spring
-- contexts each holding a HikariCP pool on that DB), the concurrent build waits indefinitely for every
-- snapshot-holding session to finish and hangs the whole `:app:test` task. If a future migration ever
-- needs to add an index to an already-large, write-hot reach_task, build that one CONCURRENTLY in its own
-- migration with executeInTransaction=false — that is the right place for the trade-off, not this
-- empty-table bootstrap.

CREATE INDEX idx_reach_task_retention ON reach_task (status, created_at);
