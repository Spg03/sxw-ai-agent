-- Chat core: knowledge documents are private to the authenticated user.
ALTER TABLE ai_knowledge_document ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- Legacy documents in this installation belong to the bootstrap administrator.
UPDATE ai_knowledge_document
SET user_id = (SELECT id FROM sxw_users WHERE username = 'admin' LIMIT 1)
WHERE user_id IS NULL;

ALTER TABLE ai_knowledge_document ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE ai_knowledge_document
    ADD CONSTRAINT fk_knowledge_document_user
    FOREIGN KEY (user_id) REFERENCES sxw_users(id);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_user_created
    ON ai_knowledge_document(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_user_hash
    ON ai_knowledge_document(user_id, content_hash);
