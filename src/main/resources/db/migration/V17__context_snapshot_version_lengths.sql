-- Snapshot identifiers include their algorithm/profile prefix, not just the
-- 64 hexadecimal SHA-256 characters. Preserve complete identifiers for replay.
ALTER TABLE ai_context_snapshot ALTER COLUMN tool_schema_version TYPE VARCHAR(128);
ALTER TABLE ai_context_snapshot ALTER COLUMN system_prompt_version TYPE VARCHAR(128);
