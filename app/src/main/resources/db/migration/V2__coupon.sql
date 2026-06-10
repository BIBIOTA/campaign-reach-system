-- Task 3.3 — Coupon three-table model (ER 05-er-database-schema.puml; FR-002/FR-004).
-- Flyway is the schema owner (Hibernate ddl-auto: none). This migration creates the
-- coupon enum types and the coupon_campaign / coupon_code / coupon_redemption tables,
-- their FKs, the duplicate-redemption unique key, and the case-insensitive code index.

CREATE TYPE code_type AS ENUM ('SHARED_CODE', 'UNIQUE_CODE');

CREATE TYPE coupon_code_status AS ENUM ('AVAILABLE', 'ASSIGNED', 'REDEEMED', 'EXPIRED');

CREATE TABLE coupon_campaign (
    id                UUID        PRIMARY KEY,
    campaign_id       UUID        NOT NULL REFERENCES campaign (id),  -- 所屬活動 (task 3.1)
    code_type         code_type   NOT NULL,                           -- SHARED_CODE | UNIQUE_CODE
    shared_code       TEXT,                                           -- 共用碼 (SHARED_CODE 用)
    total_usage_limit INT         NOT NULL,
    per_user_limit    INT         NOT NULL,
    used_count        INT         NOT NULL DEFAULT 0                   -- atomic 控總量 (FR-004)
);

CREATE TABLE coupon_code (
    id                  UUID               PRIMARY KEY,
    coupon_campaign_id  UUID               NOT NULL REFERENCES coupon_campaign (id),
    code                TEXT               NOT NULL,
    assigned_user_id    UUID,                                          -- 一人一碼指派 (UNIQUE_CODE)
    status              coupon_code_status NOT NULL,                   -- AVAILABLE | ASSIGNED | REDEEMED | EXPIRED
    created_at          TIMESTAMPTZ        NOT NULL
);

-- Case-insensitive uniqueness on the redeemable code (ER index note).
CREATE UNIQUE INDEX ux_coupon_code_lower_code ON coupon_code (lower(code));

CREATE TABLE coupon_redemption (
    id              UUID        PRIMARY KEY,
    coupon_code_id  UUID        NOT NULL REFERENCES coupon_code (id),
    user_id         UUID        NOT NULL,
    order_id        UUID        NOT NULL,
    redeemed_at     TIMESTAMPTZ NOT NULL,
    -- Blocks the same user redeeming the same code on the same order twice (FR-004).
    CONSTRAINT ux_coupon_redemption_code_user_order UNIQUE (coupon_code_id, user_id, order_id)
);
