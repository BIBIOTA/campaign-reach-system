-- Task 7.1 — reach_request batch landing schema (ER 05-er-database-schema.puml).
-- Flyway is the schema owner (Hibernate ddl-auto: none). The reach orchestrator upserts
-- one activity-level reach_request per campaign trigger, deduplicated by the
-- (campaign_id, send_cycle_key, trigger_type) unique key so Kafka at-least-once
-- redelivery never creates a second batch nor re-runs fan-out (FR-013, NFR-003).
--
-- The trigger_type enum mirrors the shared ReachRequested.TriggerType wire values
-- (SCHEDULED_BATCH | EVENT); the campaign module only carries trigger_type as an event
-- field, so this is the first place it becomes a PG enum (per the ER contract).
-- Only the reach_request table lands here; reach_task (fan-out, task 7.3) is out of scope.

CREATE TYPE trigger_type AS ENUM ('SCHEDULED_BATCH', 'EVENT');

CREATE TYPE reach_request_status AS ENUM (
    'PENDING', 'EXPANDING', 'DISPATCHING', 'DONE', 'FAILED', 'CANCELLED'
);

CREATE TABLE reach_request (
    id                   UUID                 PRIMARY KEY,
    campaign_id          UUID                 NOT NULL,
    trigger_type         trigger_type         NOT NULL,
    trigger_event_id     TEXT,                              -- source event id (EVENT triggers only)
    send_cycle_key       TEXT                 NOT NULL,      -- send-cycle key (idempotency / compensation)
    target_spec_snapshot JSONB,                             -- frozen targeting conditions at trigger time
    reach_plan_snapshot  JSONB,                             -- frozen channel/template/timing at trigger time
    status               reach_request_status NOT NULL,     -- batch status
    total_count          INT,                               -- expanded total (backfilled by fan-out, task 7.3)
    pending_count        INT,
    sent_count           INT,
    failed_count         INT,
    created_at           TIMESTAMPTZ          NOT NULL,
    started_at           TIMESTAMPTZ,                       -- expansion start time
    finished_at          TIMESTAMPTZ,                       -- completion time
    CONSTRAINT uq_reach_request_dedup UNIQUE (campaign_id, send_cycle_key, trigger_type)
);
