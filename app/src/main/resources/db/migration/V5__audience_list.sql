-- Task 7.2 — static audience-list schema (ER 05-er-database-schema.puml block A).
-- Flyway is the schema owner (Hibernate ddl-auto: none). These tables back
-- TargetSpec.Kind.STATIC_LIST resolution in the reach AudienceResolver: a curated
-- audience_list and its members, looked up by list_id at audience-resolution time
-- (FR-007, FR-013). Condition-based segmentation (member level / region) is NOT a
-- local table — member master data is owned upstream and resolved via the
-- MemberDirectory port, so no members/users table is created here.

CREATE TABLE audience_list (
    id         UUID        PRIMARY KEY,
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE audience_list_member (
    list_id  UUID        NOT NULL REFERENCES audience_list (id),
    user_id  UUID        NOT NULL,                 -- upstream member identity (PII-minimized)
    added_at TIMESTAMPTZ,
    CONSTRAINT pk_audience_list_member PRIMARY KEY (list_id, user_id)
);
