-- No conversation FK: cleanup jobs must survive conversation deletion.
CREATE TABLE IF NOT EXISTS ai_attachment_cleanup (
    object_key TEXT PRIMARY KEY,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_attachment_cleanup_due ON ai_attachment_cleanup(available_at);
