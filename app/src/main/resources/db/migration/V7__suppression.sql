-- Task 8.2 — suppression-list lookup schema (design.md §10「退訂與抑制名單（suppression）」; FR-015,
-- NFR-005). Flyway is the schema owner (Hibernate ddl-auto: none). Before sending, the reach send
-- path checks this table by (user_id, channel): a hit (退訂 UNSUBSCRIBE / 硬退信 HARD_BOUNCE /
-- 投訴 COMPLAINT) means the task is marked FAILED (a non-retryable reason) and NOT sent.
--
-- The maintenance and source of suppression entries (the consumer unsubscribe entry-point) belong to
-- the upstream e-commerce site; this system only CONSUMES the result. MVP models it as a single
-- (user_id, channel, reason) lookup table, consistent with the other reach tables: a UUID user_id
-- (PII-minimized — no email is stored, NFR-005) and the shared `channel` enum value space
-- (EMAIL|SMS|PUSH). The `channel` Postgres enum type already exists from migration V6 — reuse it.

CREATE TYPE suppression_reason AS ENUM ('UNSUBSCRIBE', 'HARD_BOUNCE', 'COMPLAINT');

CREATE TABLE suppression (
    user_id       UUID                NOT NULL,                 -- upstream member identity (PII-minimized)
    channel       channel             NOT NULL,                 -- shared Channel value space (reuses V6 enum)
    reason        suppression_reason  NOT NULL,                 -- 退訂 / 硬退信 / 投訴
    -- Set by the upstream suppression sync that owns these rows (no DB DEFAULT): this system only
    -- READS the column, so the write side intentionally lives outside the reach service.
    suppressed_at TIMESTAMPTZ         NOT NULL,
    -- One suppression entry per (user_id, channel): a given user is suppressed on a channel at most once;
    -- the lookup is keyed on exactly this pair (channel-scoped — suppressing SMS leaves EMAIL sendable).
    CONSTRAINT pk_suppression PRIMARY KEY (user_id, channel)
);
