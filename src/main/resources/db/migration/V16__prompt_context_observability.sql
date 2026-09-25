-- P4: make prompt/context observability match the active Tool-Use runtime.
-- Prompt contents remain versioned, while per-run context rows only keep hashes
-- and non-sensitive metadata. Exact replay input continues to live in
-- ai_context_snapshot and is protected by conversation ownership.

ALTER TABLE ai_prompt_version ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE ai_prompt_version ADD COLUMN IF NOT EXISTS template_hash VARCHAR(64);
ALTER TABLE ai_prompt_version ADD COLUMN IF NOT EXISTS status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE ai_prompt_run ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE ai_prompt_run ADD COLUMN IF NOT EXISTS conversation_id VARCHAR(64);
ALTER TABLE ai_prompt_run ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE ai_prompt_run ADD COLUMN IF NOT EXISTS call_no INT;
ALTER TABLE ai_prompt_run ADD COLUMN IF NOT EXISTS model VARCHAR(128);
ALTER TABLE ai_prompt_run ADD COLUMN IF NOT EXISTS template_hash VARCHAR(64);
ALTER TABLE ai_prompt_run ADD COLUMN IF NOT EXISTS input_tokens INT;
ALTER TABLE ai_prompt_run ADD COLUMN IF NOT EXISTS section_count INT;
ALTER TABLE ai_prompt_run ADD COLUMN IF NOT EXISTS status VARCHAR(24) NOT NULL DEFAULT 'PREPARED';
CREATE UNIQUE INDEX IF NOT EXISTS uq_prompt_run_request_call
    ON ai_prompt_run(request_id, call_no) WHERE call_no IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_prompt_run_conversation
    ON ai_prompt_run(user_id, conversation_id, created_at DESC);

ALTER TABLE ai_context_item ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE ai_context_item ADD COLUMN IF NOT EXISTS conversation_id VARCHAR(64);
ALTER TABLE ai_context_item ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE ai_context_item ADD COLUMN IF NOT EXISTS call_no INT;
ALTER TABLE ai_context_item ADD COLUMN IF NOT EXISTS section_key VARCHAR(64);
ALTER TABLE ai_context_item ADD COLUMN IF NOT EXISTS section_kind VARCHAR(16);
ALTER TABLE ai_context_item ADD COLUMN IF NOT EXISTS inclusion_status VARCHAR(16) NOT NULL DEFAULT 'INCLUDED';
ALTER TABLE ai_context_item ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE ai_context_item ADD COLUMN IF NOT EXISTS content_length INT;
ALTER TABLE ai_context_item ADD COLUMN IF NOT EXISTS source_ref VARCHAR(160);
UPDATE ai_context_item
SET section_key = COALESCE(section_key, section_type),
    section_kind = COALESCE(section_kind, 'DYNAMIC')
WHERE section_key IS NULL OR section_kind IS NULL;
CREATE INDEX IF NOT EXISTS idx_context_item_conversation
    ON ai_context_item(user_id, conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_context_item_request_call
    ON ai_context_item(request_id, call_no, id);
