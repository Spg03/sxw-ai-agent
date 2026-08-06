CREATE TABLE IF NOT EXISTS ai_conversation (
    conversation_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sxw_users(id),
    title VARCHAR(160) NOT NULL DEFAULT '新建对话',
    profile_code VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    rolling_summary TEXT,
    summary_message_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_conversation_user_updated ON ai_conversation(user_id, pinned DESC, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_conversation_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL REFERENCES ai_conversation(conversation_id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_conversation_message_order ON ai_conversation_message(conversation_id, id);

CREATE TABLE IF NOT EXISTS ai_chat_attachment (
    attachment_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sxw_users(id),
    conversation_id VARCHAR(64) REFERENCES ai_conversation(conversation_id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    object_key VARCHAR(512),
    extracted_text TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_attachment_owner ON ai_chat_attachment(user_id, created_at DESC);

ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES sxw_users(id);
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS source_kind VARCHAR(16) NOT NULL DEFAULT 'INFERRED';
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS always_on BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ai_memory_item ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_memory_owner_active ON ai_memory_item(user_id, status, always_on DESC, activated_at DESC);
