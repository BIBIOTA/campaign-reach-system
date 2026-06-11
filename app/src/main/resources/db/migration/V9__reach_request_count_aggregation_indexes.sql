-- Task 10.2 — supporting access path for the background reach_request counter projection.
-- The aggregation scheduler only scans active/incomplete reach_request batches, then folds their
-- reach_task statuses by reach_request_id. Keep this index narrow to support that join/group path
-- without adding any per-task counter write-back on reach_request.

CREATE INDEX idx_reach_task_request_status ON reach_task (reach_request_id, status);
