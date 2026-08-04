-- V7: 树洞（Treehole）+ Token 黑名单表
-- 解决前端访问树洞/Dashboard 时因缺失 sxw_treeholes 导致的 500 错误

CREATE TABLE IF NOT EXISTS sxw_treeholes (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(120) NOT NULL,
    content         TEXT         NOT NULL,
    emotion_tag     VARCHAR(64)  NOT NULL,
    hermes_summary  TEXT         NOT NULL,
    hermes_reply    TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_treeholes_user_created ON sxw_treeholes(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_token_blacklist (
    id              BIGSERIAL PRIMARY KEY,
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    expires_at      TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_token_blacklist_hash ON ai_token_blacklist(token_hash);
