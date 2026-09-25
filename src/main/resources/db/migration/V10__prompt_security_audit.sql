CREATE TABLE IF NOT EXISTS ai_security_event (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL UNIQUE,
  user_id BIGINT,
  conversation_id VARCHAR(128),
  request_id VARCHAR(128),
  risk_level VARCHAR(16) NOT NULL,
  action VARCHAR(32) NOT NULL,
  source VARCHAR(64) NOT NULL,
  reasons TEXT,
  content_hash VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_security_event_created ON ai_security_event(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_event_user ON ai_security_event(user_id, created_at DESC);
