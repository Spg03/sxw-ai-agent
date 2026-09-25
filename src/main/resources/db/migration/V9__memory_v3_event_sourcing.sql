-- Conversation events remain the immutable source of truth.
ALTER TABLE ai_conversation_message ADD COLUMN IF NOT EXISTS sequence_no BIGINT;
ALTER TABLE ai_conversation_message ADD COLUMN IF NOT EXISTS turn_id VARCHAR(64);
ALTER TABLE ai_conversation_message ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);
ALTER TABLE ai_conversation_message ADD COLUMN IF NOT EXISTS parent_message_id BIGINT;
ALTER TABLE ai_conversation_message ADD COLUMN IF NOT EXISTS message_type VARCHAR(32) NOT NULL DEFAULT 'CHAT';
ALTER TABLE ai_conversation_message ADD COLUMN IF NOT EXISTS tool_call_id VARCHAR(128);
ALTER TABLE ai_conversation_message ADD COLUMN IF NOT EXISTS tool_name VARCHAR(128);
ALTER TABLE ai_conversation_message ADD COLUMN IF NOT EXISTS processing_status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE ai_conversation_message ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE ai_conversation_message ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
WITH numbered AS (SELECT id, row_number() OVER (PARTITION BY conversation_id ORDER BY id) AS seq FROM ai_conversation_message)
UPDATE ai_conversation_message m SET sequence_no=numbered.seq FROM numbered WHERE m.id=numbered.id AND m.sequence_no IS NULL;
ALTER TABLE ai_conversation_message ALTER COLUMN sequence_no SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_conversation_sequence ON ai_conversation_message(conversation_id, sequence_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_conversation_request_user ON ai_conversation_message(conversation_id, request_id) WHERE request_id IS NOT NULL AND message_type='USER';
CREATE INDEX IF NOT EXISTS idx_conversation_turn ON ai_conversation_message(conversation_id, turn_id, sequence_no);

CREATE TABLE IF NOT EXISTS ai_conversation_summary_version (
 id BIGSERIAL PRIMARY KEY, conversation_id VARCHAR(64) NOT NULL REFERENCES ai_conversation(conversation_id) ON DELETE CASCADE,
 version_no INT NOT NULL, parent_version_no INT, covered_start_sequence BIGINT NOT NULL, covered_end_sequence BIGINT NOT NULL,
 delta_start_sequence BIGINT NOT NULL, delta_end_sequence BIGINT NOT NULL, content TEXT NOT NULL, prompt_version VARCHAR(64) NOT NULL,
 model VARCHAR(128), input_tokens INT, output_tokens INT, status VARCHAR(32) NOT NULL DEFAULT 'PENDING', idempotency_key VARCHAR(160) NOT NULL,
 error_message TEXT, created_at TIMESTAMP NOT NULL DEFAULT NOW(), completed_at TIMESTAMP,
 UNIQUE(conversation_id, version_no), UNIQUE(conversation_id, idempotency_key), CHECK(covered_start_sequence <= covered_end_sequence), CHECK(delta_start_sequence <= delta_end_sequence)
);
CREATE INDEX IF NOT EXISTS idx_summary_latest ON ai_conversation_summary_version(conversation_id, status, version_no DESC);

CREATE TABLE IF NOT EXISTS ai_working_memory_version (
 id BIGSERIAL PRIMARY KEY, conversation_id VARCHAR(64) NOT NULL REFERENCES ai_conversation(conversation_id) ON DELETE CASCADE,
 version_no INT NOT NULL, materialized_until_sequence BIGINT NOT NULL DEFAULT 0, goal TEXT, task_status VARCHAR(32), plan_id VARCHAR(64), plan_version INT,
 constraints JSONB NOT NULL DEFAULT '[]'::jsonb, open_questions JSONB NOT NULL DEFAULT '[]'::jsonb, next_actions JSONB NOT NULL DEFAULT '[]'::jsonb,
 patch_json JSONB NOT NULL DEFAULT '[]'::jsonb, source VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT NOW(), UNIQUE(conversation_id, version_no)
);

CREATE TABLE IF NOT EXISTS ai_agent_run (
 run_id VARCHAR(64) PRIMARY KEY, conversation_id VARCHAR(64) NOT NULL REFERENCES ai_conversation(conversation_id) ON DELETE CASCADE,
 user_id BIGINT NOT NULL REFERENCES sxw_users(id), request_id VARCHAR(64) NOT NULL, turn_id VARCHAR(64), model VARCHAR(128), temperature NUMERIC(4,3), max_output_tokens INT,
 status VARCHAR(32) NOT NULL, started_at TIMESTAMP NOT NULL DEFAULT NOW(), finished_at TIMESTAMP, UNIQUE(conversation_id, request_id)
);
CREATE TABLE IF NOT EXISTS ai_context_snapshot (
 id BIGSERIAL PRIMARY KEY, run_id VARCHAR(64) NOT NULL REFERENCES ai_agent_run(run_id) ON DELETE CASCADE,
 call_no INT NOT NULL, summary_version_no INT, working_memory_version_no INT, recent_start_sequence BIGINT, recent_end_sequence BIGINT,
 always_on_memory_ids JSONB NOT NULL DEFAULT '[]'::jsonb, relevant_memory_ids JSONB NOT NULL DEFAULT '[]'::jsonb, tool_schema_version VARCHAR(64),
 system_prompt_version VARCHAR(64), input_tokens INT NOT NULL, context_hash VARCHAR(80) NOT NULL, context_json JSONB NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT NOW(), UNIQUE(run_id, call_no)
);

CREATE TABLE IF NOT EXISTS ai_memory_candidate (
 id BIGSERIAL PRIMARY KEY, candidate_id VARCHAR(64) NOT NULL UNIQUE, user_id BIGINT NOT NULL REFERENCES sxw_users(id), conversation_id VARCHAR(64) REFERENCES ai_conversation(conversation_id) ON DELETE SET NULL,
 source_message_id BIGINT REFERENCES ai_conversation_message(id) ON DELETE SET NULL, source_kind VARCHAR(16) NOT NULL, approval_mode VARCHAR(16) NOT NULL,
 memory_type VARCHAR(64) NOT NULL, scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL', scope_id VARCHAR(64), title VARCHAR(255) NOT NULL, content TEXT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING', applied_memory_id VARCHAR(64), expires_at TIMESTAMP, reviewed_by VARCHAR(128), reviewed_at TIMESTAMP, created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_memory_candidate_owner ON ai_memory_candidate(user_id,status,created_at DESC);

ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL';
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS scope_id VARCHAR(64);
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS source_candidate_id VARCHAR(64);
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS active_embedding_id BIGINT;

CREATE TABLE IF NOT EXISTS ai_memory_embedding (
 id BIGSERIAL PRIMARY KEY, memory_id VARCHAR(64) NOT NULL REFERENCES ai_memory_item(memory_id) ON DELETE CASCADE, model VARCHAR(128) NOT NULL,
 dimension INT NOT NULL, content_hash VARCHAR(64) NOT NULL, embedding vector(1024), status VARCHAR(32) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMP NOT NULL DEFAULT NOW(),
 UNIQUE(memory_id,model,dimension,content_hash)
);

CREATE TABLE IF NOT EXISTS ai_outbox_event (
 id BIGSERIAL PRIMARY KEY, event_type VARCHAR(64) NOT NULL, aggregate_type VARCHAR(64) NOT NULL, aggregate_id VARCHAR(64) NOT NULL,
 payload JSONB NOT NULL, idempotency_key VARCHAR(160) NOT NULL UNIQUE, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', attempt_count INT NOT NULL DEFAULT 0,
 next_retry_at TIMESTAMP NOT NULL DEFAULT NOW(), locked_at TIMESTAMP, last_error TEXT, created_at TIMESTAMP NOT NULL DEFAULT NOW(), completed_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_outbox_ready ON ai_outbox_event(status,next_retry_at);
