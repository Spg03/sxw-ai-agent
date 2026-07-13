-- V5: LLM-as-Judge integration for eval system

ALTER TABLE ai_eval_case
    ADD COLUMN IF NOT EXISTS judge_criteria TEXT;
ALTER TABLE ai_eval_case
    ADD COLUMN IF NOT EXISTS validation_mode VARCHAR(20) DEFAULT 'KEYWORD_ONLY';

CREATE TABLE IF NOT EXISTS ai_eval_result (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              VARCHAR(64)  NOT NULL,
    case_id             VARCHAR(64)  NOT NULL,
    case_name           VARCHAR(255),
    passed              BOOLEAN      NOT NULL DEFAULT FALSE,
    keyword_passed      BOOLEAN,
    judge_status        VARCHAR(20),
    judge_model         VARCHAR(128),
    judge_prompt_version VARCHAR(20),
    judge_score         DOUBLE PRECISION,
    judge_reason        TEXT,
    score               DOUBLE PRECISION,
    actual_output       TEXT,
    expected_output     TEXT,
    validation_details  TEXT,
    validation_mode     VARCHAR(20),
    duration_ms         BIGINT       NOT NULL DEFAULT 0,
    error_message       TEXT,
    attempt_no          INT          NOT NULL DEFAULT 1,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, case_id, attempt_no)
);

CREATE INDEX IF NOT EXISTS idx_eval_result_run ON ai_eval_result(run_id);
CREATE INDEX IF NOT EXISTS idx_eval_result_case ON ai_eval_result(case_id);
