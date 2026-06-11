-- Task 11.1 — supporting index for the data-retention purge scan (NFR-005, spec「觸達稽核軌跡屆期歸檔或刪除」).
-- Flyway is the schema owner (Hibernate ddl-auto: none). The retention purge (ReachAuditTrailPurger)
-- deletes terminal-state reach_task rows older than a cutoff: WHERE status IN (terminal) AND created_at < cutoff.
--
-- The existing idx_reach_task_dispatch (status, next_retry_at, created_at) leads with status but then
-- orders by next_retry_at, so it cannot range-scan created_at directly for this predicate. This
-- composite (status, created_at) lets the purge probe each terminal status and range-scan created_at
-- for the aged rows, instead of a full table scan on each (coarse, hourly) sweep.

CREATE INDEX idx_reach_task_retention ON reach_task (status, created_at);
