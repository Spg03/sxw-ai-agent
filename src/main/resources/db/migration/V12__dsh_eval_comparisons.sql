-- V12: DSH sidecar A/B evaluation support. Production chat tables are untouched.

CREATE TABLE IF NOT EXISTS ai_eval_comparison (
    id                  BIGSERIAL PRIMARY KEY,
    comparison_id       VARCHAR(64) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    case_ids_json       TEXT NOT NULL,
    targets_json        TEXT NOT NULL,
    repeats             INT NOT NULL DEFAULT 3 CHECK (repeats BETWEEN 1 AND 20),
    status              VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    attempt_count       INT NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMP,
    triggered_by        VARCHAR(128),
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    error_message       TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_eval_comparison_worker
    ON ai_eval_comparison(status, next_retry_at, created_at);

CREATE TABLE IF NOT EXISTS ai_eval_target_result (
    id                          BIGSERIAL PRIMARY KEY,
    comparison_id               VARCHAR(64) NOT NULL REFERENCES ai_eval_comparison(comparison_id) ON DELETE CASCADE,
    case_id                     VARCHAR(64) NOT NULL,
    case_name                   VARCHAR(255),
    target_code                 VARCHAR(16) NOT NULL,
    target_version              VARCHAR(128) NOT NULL,
    model                       VARCHAR(128) NOT NULL,
    config_hash                 VARCHAR(64) NOT NULL,
    repeat_index                INT NOT NULL,
    passed                      BOOLEAN NOT NULL DEFAULT FALSE,
    keyword_passed              BOOLEAN,
    judge_status                VARCHAR(20),
    judge_score                 DOUBLE PRECISION,
    judge_reason                TEXT,
    actual_output               TEXT,
    expected_output             TEXT,
    stop_reason                 VARCHAR(64),
    input_tokens                INT,
    output_tokens               INT,
    latency_ms                  BIGINT NOT NULL DEFAULT 0,
    tool_call_count             INT NOT NULL DEFAULT 0,
    tool_success_rate           DOUBLE PRECISION,
    security_violation_count    INT NOT NULL DEFAULT 0,
    event_log_object_key        VARCHAR(512),
    error_category              VARCHAR(64),
    error_message               TEXT,
    metrics_json                TEXT,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (comparison_id, case_id, target_code, repeat_index)
);
CREATE INDEX IF NOT EXISTS idx_eval_target_result_comparison
    ON ai_eval_target_result(comparison_id, case_id, target_code, repeat_index);
