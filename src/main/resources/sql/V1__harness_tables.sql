-- sxw-ai-agent Harness 平台建表脚本
-- 覆盖 P4/P5/P8/P9 全部新增表
-- 执行：psql -h localhost -U postgres -d sxw_ai_agent -f V1__harness_tables.sql

-- ============================================================
-- P4: Prompt 版本化
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_prompt_version (
    id              BIGSERIAL PRIMARY KEY,
    prompt_code     VARCHAR(64)  NOT NULL,
    version         INT          NOT NULL,
    section_key     VARCHAR(64)  NOT NULL,
    content         TEXT         NOT NULL,
    description     VARCHAR(512),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(prompt_code, version, section_key)
);

CREATE TABLE IF NOT EXISTS ai_prompt_run (
    id                  BIGSERIAL PRIMARY KEY,
    request_id          VARCHAR(64)  NOT NULL,
    prompt_code         VARCHAR(64)  NOT NULL,
    prompt_version      INT          NOT NULL,
    static_hash         VARCHAR(64),
    dynamic_hash        VARCHAR(64),
    rendered_hash       VARCHAR(64),
    rendered_length     INT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_prompt_run_request ON ai_prompt_run(request_id);

-- ============================================================
-- P4: Context 审计
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_context_item (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL,
    section_type    VARCHAR(64)  NOT NULL,
    item_type       VARCHAR(64),
    item_id         VARCHAR(128),
    token_count     INT,
    content_preview TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_context_item_request ON ai_context_item(request_id);

-- ============================================================
-- P5: 自研 Knowledge Base (PgVector)
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_knowledge_document (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          VARCHAR(64)  NOT NULL UNIQUE,
    title           VARCHAR(255) NOT NULL,
    source_type     VARCHAR(64)  NOT NULL,
    source_path     VARCHAR(512),
    content_hash    VARCHAR(64),
    chunk_count     INT          NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id              BIGSERIAL PRIMARY KEY,
    chunk_id        VARCHAR(64)  NOT NULL UNIQUE,
    doc_id          VARCHAR(64)  NOT NULL REFERENCES ai_knowledge_document(doc_id),
    chunk_index     INT          NOT NULL,
    content         TEXT         NOT NULL,
    token_count     INT,
    embedding       vector(1536),
    metadata        JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chunk_doc ON ai_knowledge_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON ai_knowledge_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE TABLE IF NOT EXISTS ai_knowledge_hit (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL,
    chunk_id        VARCHAR(64)  NOT NULL,
    doc_id          VARCHAR(64)  NOT NULL,
    score           DECIMAL(8,6) NOT NULL,
    rank            INT          NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_hit_request ON ai_knowledge_hit(request_id);

-- ============================================================
-- P8: Hermes 候选系统
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_hermes_candidate (
    id                  BIGSERIAL PRIMARY KEY,
    candidate_id        VARCHAR(64)  NOT NULL UNIQUE,
    source_request_id   VARCHAR(64)  NOT NULL,
    source_trace_id     VARCHAR(64),
    candidate_type      VARCHAR(64)  NOT NULL,
    title               VARCHAR(255) NOT NULL,
    content             TEXT         NOT NULL,
    target_store        VARCHAR(64)  NOT NULL,
    confidence          DECIMAL(5,4),
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    reviewed_by         VARCHAR(128),
    reviewed_at         TIMESTAMP,
    applied_at          TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_candidate_status ON ai_hermes_candidate(status);

-- ============================================================
-- P9: Trace + Eval
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_request_trace (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL UNIQUE,
    trace_id        VARCHAR(64),
    profile_code    VARCHAR(32),
    run_mode        VARCHAR(32),
    user_message    TEXT,
    final_answer    TEXT,
    total_tokens    INT,
    latency_ms      BIGINT,
    status          VARCHAR(32),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_model_call (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL,
    call_index      INT          NOT NULL,
    model_name      VARCHAR(128),
    prompt_tokens   INT,
    completion_tokens INT,
    latency_ms      BIGINT,
    has_tool_calls  BOOLEAN,
    tool_call_names TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_model_call_request ON ai_model_call(request_id);

CREATE TABLE IF NOT EXISTS ai_tool_call (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL,
    trace_id        VARCHAR(64),
    tool_name       VARCHAR(128) NOT NULL,
    arguments       TEXT,
    result_preview  TEXT,
    risk_level      VARCHAR(32),
    approved        BOOLEAN,
    latency_ms      BIGINT,
    success         BOOLEAN,
    error_message   TEXT,
    turn            INT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tool_call_request ON ai_tool_call(request_id);

CREATE TABLE IF NOT EXISTS ai_eval_case (
    id              BIGSERIAL PRIMARY KEY,
    case_id         VARCHAR(64)  NOT NULL UNIQUE,
    profile_code    VARCHAR(32),
    question        TEXT         NOT NULL,
    expected_answer TEXT,
    eval_criteria   TEXT,
    source_request_id VARCHAR(64),
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_eval_run (
    id              BIGSERIAL PRIMARY KEY,
    run_id          VARCHAR(64)  NOT NULL UNIQUE,
    case_id         VARCHAR(64)  NOT NULL,
    profile_code    VARCHAR(32),
    prompt_version  INT,
    actual_answer   TEXT,
    score           DECIMAL(5,4),
    pass            BOOLEAN,
    latency_ms      BIGINT,
    model_name      VARCHAR(128),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_eval_run_case ON ai_eval_run(case_id);
