-- P1 long-term memory governance, deletion semantics and audit trail.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE ai_memory_candidate ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE ai_memory_candidate ADD COLUMN IF NOT EXISTS source_conversation_id VARCHAR(64);

ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS source_conversation_id VARCHAR(64);
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(255);
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS purge_requested_at TIMESTAMP;
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS purged_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS ai_memory_candidate_event (
    id BIGSERIAL PRIMARY KEY,
    candidate_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES sxw_users(id),
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_memory_candidate_event
    ON ai_memory_candidate_event(candidate_id, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_memory_candidate_content
    ON ai_memory_candidate(user_id, content_hash)
    WHERE status = 'PENDING' AND content_hash IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_memory_content
    ON ai_memory_item(user_id, content_hash, scope_type, COALESCE(scope_id, ''))
    WHERE status = 'ACTIVE' AND deleted_at IS NULL AND content_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_memory_scope_active
    ON ai_memory_item(user_id, scope_type, scope_id, status, always_on);

CREATE INDEX IF NOT EXISTS idx_memory_text_trgm
    ON ai_memory_item USING gin ((COALESCE(name, '') || ' ' || COALESCE(description, '')) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_memory_source_conversation
    ON ai_memory_item(user_id, source_conversation_id)
    WHERE source_conversation_id IS NOT NULL;
