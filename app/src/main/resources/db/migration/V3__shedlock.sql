-- Task 6.2 — ShedLock lock-registry table (§5, US-004). Flyway is the schema owner
-- (Hibernate ddl-auto: none). The scheduled reach-scan sweep (CampaignReachScanScheduler)
-- holds a @SchedulerLock backed by JdbcTemplateLockProvider over this standard table, so the
-- same activity + same schedule cycle runs only once across instances / restart back-scans.
-- Column layout is the canonical ShedLock JdbcTemplate schema (do not customise).

CREATE TABLE shedlock (
    name       VARCHAR(64)   NOT NULL,
    lock_until TIMESTAMP(3)  NOT NULL,
    locked_at  TIMESTAMP(3)  NOT NULL,
    locked_by  VARCHAR(255)  NOT NULL,
    PRIMARY KEY (name)
);
