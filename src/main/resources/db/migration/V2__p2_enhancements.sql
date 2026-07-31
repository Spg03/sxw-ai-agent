-- sxw-ai-agent P2 增强迁移脚本
-- 新增: ChatMemory 持久化、JWT Refresh Token、Token 黑名单、Knowledge Hit 索引、Eval 系统重建

-- ===== 1. ChatMemory 持久化 =====
CREATE TABLE IF NOT EXISTS ai_chat_memory (
    conversation_id VARCHAR(100) NOT NULL,
    content         TEXT        NOT NULL,
    type            VARCHAR(32) NOT NULL,
    "timestamp"     TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chat_memory_conv ON ai_chat_memory(conversation_id, "timestamp");

-- ===== 2. JWT Refresh Token =====
CREATE TABLE IF NOT EXISTS ai_refresh_token (
    id              BIGSERIAL PRIMARY KEY,
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    username        VARCHAR(64)  NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_refresh_token_username ON ai_refresh_token(username);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expires ON ai_refresh_token(expires_at);

-- ===== 3. JWT Token 黑名单 =====
CREATE TABLE IF NOT EXISTS ai_token_blacklist (
    id              BIGSERIAL PRIMARY KEY,
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    expires_at      TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_blacklist_expires ON ai_token_blacklist(expires_at);

-- ===== 4. Knowledge Hit 索引（表已在 V1 中创建） =====
CREATE INDEX IF NOT EXISTS idx_hit_chunk ON ai_knowledge_hit(chunk_id);
CREATE INDEX IF NOT EXISTS idx_hit_doc ON ai_knowledge_hit(doc_id);

-- ===== 5. 合并 Eval 系统 - 重建 ai_eval_case / ai_eval_run 对齐 evaluation 包 =====
DROP TABLE IF EXISTS ai_eval_result;  -- 删除旧系统逐条结果表（如存在）
DROP TABLE IF EXISTS ai_eval_run;     -- 先删 run（依赖 case）
DROP TABLE IF EXISTS ai_eval_case;    -- 重建以对齐新模型

CREATE TABLE ai_eval_case (
    id               BIGSERIAL PRIMARY KEY,
    case_id          VARCHAR(64)  NOT NULL UNIQUE,
    case_name        VARCHAR(256) NOT NULL,
    case_type        VARCHAR(32)  NOT NULL DEFAULT 'FUNCTIONAL',
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    profile_code     VARCHAR(32),
    input_prompt     TEXT         NOT NULL,
    expected_output  TEXT,
    validation_rules TEXT,
    tags             VARCHAR(512),
    priority         INTEGER      NOT NULL DEFAULT 0,
    created_by       VARCHAR(64),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_eval_run (
    id              BIGSERIAL PRIMARY KEY,
    run_id          VARCHAR(64)  NOT NULL UNIQUE,
    run_name        VARCHAR(256) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    profile_code    VARCHAR(32),
    case_ids        TEXT,
    total_cases     INTEGER      NOT NULL DEFAULT 0,
    passed_cases    INTEGER      NOT NULL DEFAULT 0,
    failed_cases    INTEGER      NOT NULL DEFAULT 0,
    skipped_cases   INTEGER      NOT NULL DEFAULT 0,
    pass_rate       DECIMAL(5,2),
    duration_ms     BIGINT,
    triggered_by    VARCHAR(64),
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    report_path     VARCHAR(512),
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
