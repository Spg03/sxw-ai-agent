-- sxw-ai-agent P3 增强迁移脚本
-- 新增: ai_knowledge_document 添加 content_hash 列，支持增量索引去重

-- ===== 增量索引：content_hash =====
ALTER TABLE ai_knowledge_document
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_doc_content_hash ON ai_knowledge_document(content_hash);
