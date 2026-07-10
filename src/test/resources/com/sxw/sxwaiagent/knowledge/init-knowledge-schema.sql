-- H2-compatible schema for KnowledgeRepository tests
-- Mirrors ai_knowledge_document + ai_knowledge_chunk from V1/V3/V4 migrations
-- (omits vector(1536) column since H2 does not support pgvector)

CREATE TABLE IF NOT EXISTS ai_knowledge_document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id          VARCHAR(64)  NOT NULL UNIQUE,
    title           VARCHAR(255) NOT NULL,
    source_type     VARCHAR(64),
    source_path     VARCHAR(512),
    content_hash    VARCHAR(64),
    chunk_count     INT          NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    index_fingerprint VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    chunk_id        VARCHAR(64)  NOT NULL UNIQUE,
    doc_id          VARCHAR(64)  NOT NULL,
    chunk_index     INT          NOT NULL,
    breadcrumb      VARCHAR(512),
    content         CLOB         NOT NULL,
    token_count     INT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
