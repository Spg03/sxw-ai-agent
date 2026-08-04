-- V6: 认证相关表（用户 + Refresh Token）
-- 解决前端注册/登录时因缺失 sxw_users 表导致的 500 错误

CREATE TABLE IF NOT EXISTS sxw_users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(128) NOT NULL,
    nickname        VARCHAR(64)  NOT NULL,
    role            VARCHAR(32)  NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_refresh_token (
    id              BIGSERIAL PRIMARY KEY,
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    username        VARCHAR(64)  NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON ai_refresh_token(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_token_username ON ai_refresh_token(username);
