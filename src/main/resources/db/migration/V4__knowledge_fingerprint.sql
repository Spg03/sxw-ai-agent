-- V4: Add index fingerprint for comprehensive change detection
-- The content_hash column already exists from V3; this adds the fingerprint
-- which combines content_hash + IndexConfig to detect config changes too.

ALTER TABLE ai_knowledge_document
    ADD COLUMN IF NOT EXISTS index_fingerprint VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_doc_fingerprint
    ON ai_knowledge_document(index_fingerprint);
