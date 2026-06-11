-- Task 11.1 — supporting index for the data-retention purge scan (NFR-005, spec「觸達稽核軌跡屆期歸檔或刪除」).
-- Flyway is the schema owner (Hibernate ddl-auto: none). The retention purge (ReachAuditTrailPurger)
-- deletes terminal-state reach_task rows older than a cutoff: WHERE status IN (terminal) AND created_at < cutoff.
--
-- The existing idx_reach_task_dispatch (status, next_retry_at, created_at) leads with status but then
-- orders by next_retry_at, so it cannot range-scan created_at directly for this predicate. This
-- composite (status, created_at) lets the purge probe each terminal status and range-scan created_at
-- for the aged rows, instead of a full table scan on each (coarse, hourly) sweep.
--
-- CONCURRENTLY: reach_task is a hot, high-volume table. A plain CREATE INDEX takes a SHARE lock that
-- blocks all writes to reach_task until the build finishes — unacceptable downtime on an environment
-- that already holds data. CONCURRENTLY builds without blocking writers. It cannot run inside a
-- transaction, so the companion V10__reach_task_retention_index.sql.conf sets executeInTransaction=false.
-- Trade-off: a CONCURRENTLY build that fails leaves an INVALID index and marks this migration failed;
-- recovery is `DROP INDEX idx_reach_task_retention;` then `flyway repair` + re-migrate.

CREATE INDEX CONCURRENTLY idx_reach_task_retention ON reach_task (status, created_at);
