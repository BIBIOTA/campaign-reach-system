-- Task 9.1 — send_result row written by the dispatcher stage-two write-back (ER
-- 05-er-database-schema.puml). Flyway is the schema owner (Hibernate ddl-auto: none). When the
-- two-phase dispatcher's external send succeeds, it records ONE send_result row alongside the
-- reach_task SENT transition (§5, NFR-003).
--
-- PII minimization (spec §10 / NFR-005 / task 11.1): send_result stores ONLY the provider's
-- message id + a coarse outcome (plus the task id and time). It NEVER stores the recipient email
-- or message content — those live with the upstream provider, not here.
--
-- provider_message_id is the provider's dedup handle: the partial UNIQUE index (only where the id
-- is present) lets at-least-once redelivery of the same accepted send collapse to one row, while
-- still allowing many rows that carry no provider id.

CREATE TABLE send_result (
    id                  UUID         PRIMARY KEY,
    reach_task_id       UUID         NOT NULL REFERENCES reach_task (id),
    provider_message_id TEXT,                                  -- provider message id (dedup handle)
    outcome             TEXT         NOT NULL,                  -- coarse send outcome (e.g. SENT)
    occurred_at         TIMESTAMPTZ  NOT NULL
);

-- ER-suggested access path: results for a task in time order.
CREATE INDEX idx_send_result_task ON send_result (reach_task_id, occurred_at);

-- ER-suggested dedup: at most one row per provider message id (when present).
CREATE UNIQUE INDEX uq_send_result_provider_message_id
    ON send_result (provider_message_id)
    WHERE provider_message_id IS NOT NULL;
