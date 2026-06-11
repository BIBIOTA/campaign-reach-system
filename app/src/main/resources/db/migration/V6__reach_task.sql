-- Task 7.3 — per-recipient reach_task fan-out schema (ER 05-er-database-schema.puml).
-- Flyway is the schema owner (Hibernate ddl-auto: none). The reach orchestrator expands one
-- reach_request into N reach_task(PENDING) rows in pages. The four-column unique key
-- (campaign_id, user_id, send_cycle_key, channel) is the idempotency source of truth: the
-- paged INSERT uses ON CONFLICT DO NOTHING on that key, so Kafka at-least-once redelivery of a
-- half-expanded batch never double-creates a row and the fan-out converges to exactly N rows
-- (FR-014, NFR-003, US-006).
--
-- The channel enum mirrors the shared Channel wire values (EMAIL|SMS|PUSH); reach_task_status
-- carries the full task lifecycle per the ER legend. Task 7.3 only WRITES the PENDING-creation
-- columns (id, reach_request_id, campaign_id, user_id, send_cycle_key, channel, status, created_at);
-- the retry / lock / sent columns exist per the ER contract but are populated by the dispatcher
-- (sections 8/9) and are deliberately left for those tasks.

CREATE TYPE channel AS ENUM ('EMAIL', 'SMS', 'PUSH');

CREATE TYPE reach_task_status AS ENUM (
    'PENDING', 'PROCESSING', 'SENT', 'RETRY_SCHEDULED', 'FAILED', 'DLQ', 'CANCELLED'
);

CREATE TABLE reach_task (
    id                    UUID               PRIMARY KEY,
    reach_request_id      UUID               NOT NULL REFERENCES reach_request (id),
    campaign_id           UUID               NOT NULL,
    user_id               UUID               NOT NULL,
    send_cycle_key        TEXT               NOT NULL,
    channel               channel            NOT NULL,
    status                reach_task_status  NOT NULL,        -- task lifecycle status
    retry_count           INT,                                -- dispatcher (sections 8/9)
    next_retry_at         TIMESTAMPTZ,                        -- dispatcher (sections 8/9)
    processing_started_at TIMESTAMPTZ,                        -- dispatcher (sections 8/9)
    last_attempt_at       TIMESTAMPTZ,                        -- dispatcher (sections 8/9)
    locked_by             TEXT,                               -- dispatcher claim lease (sections 8/9)
    locked_until          TIMESTAMPTZ,                        -- dispatcher claim lease (sections 8/9)
    last_error            TEXT,                               -- dispatcher (sections 8/9)
    sent_at               TIMESTAMPTZ,                        -- dispatcher (sections 8/9)
    created_at            TIMESTAMPTZ        NOT NULL,
    -- 同一活動同一週期同一人同一通道只建立一筆任務：四欄 unique 是冪等的唯一真實來源（重投以 ON CONFLICT 收斂）。
    CONSTRAINT uq_reach_task_dedup UNIQUE (campaign_id, user_id, send_cycle_key, channel)
);

-- ER-suggested indexes. The first two serve the dispatcher claim scan (sections 8/9); the third
-- backs the frequency-cap EXISTS lookup (user history) that task 7.3 issues before each insert.
CREATE INDEX idx_reach_task_dispatch ON reach_task (status, next_retry_at, created_at);
CREATE INDEX idx_reach_task_campaign_status ON reach_task (campaign_id, status);
CREATE INDEX idx_reach_task_user_campaign ON reach_task (user_id, campaign_id);
