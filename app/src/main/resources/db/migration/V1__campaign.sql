-- Task 3.1 — Campaign aggregate base schema (ER 05-er-database-schema.puml).
-- Flyway is the schema owner (Hibernate ddl-auto: none). This first migration
-- creates the campaign lifecycle/type enum types and the campaign table, including
-- the optimistic-lock version column and the audit columns (FR-001).

CREATE TYPE campaign_type AS ENUM ('DISCOUNT', 'GIFT_ADDON', 'FLASH_SALE');

CREATE TYPE campaign_status AS ENUM ('DRAFT', 'SCHEDULED', 'RUNNING', 'PAUSED', 'ENDED');

CREATE TABLE campaign (
    id          UUID            PRIMARY KEY,
    name        TEXT            NOT NULL,
    type        campaign_type   NOT NULL,
    status      campaign_status NOT NULL,
    start_at    TIMESTAMPTZ     NOT NULL,
    end_at      TIMESTAMPTZ     NOT NULL,
    rule_config JSONB,                          -- offer-rule params (validated per type in task 3.2)
    target_spec JSONB,                          -- targeting conditions
    reach_plan  JSONB,                          -- channel / template / timing
    version     INT             NOT NULL,       -- optimistic-lock version (FR-001)
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL
);
