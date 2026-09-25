-- P3 workspace data: user-scoped notes and real treehole mood analytics.

CREATE TABLE IF NOT EXISTS ai_note (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES sxw_users(id) ON DELETE CASCADE,
    title       VARCHAR(120) NOT NULL,
    content     TEXT         NOT NULL,
    tags        TEXT         NOT NULL DEFAULT '',
    favorite    BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ai_note_user_title UNIQUE (user_id, title)
);

CREATE INDEX IF NOT EXISTS idx_ai_note_user_updated
    ON ai_note(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_note_user_favorite
    ON ai_note(user_id, favorite, updated_at DESC);

ALTER TABLE sxw_treeholes
    ADD COLUMN IF NOT EXISTS mood VARCHAR(32) NOT NULL DEFAULT '平静',
    ADD COLUMN IF NOT EXISTS favorite BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_treeholes_user_archived_created
    ON sxw_treeholes(user_id, archived, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_treeholes_user_mood_created
    ON sxw_treeholes(user_id, mood, created_at DESC);

ALTER TABLE sxw_treeholes
    DROP CONSTRAINT IF EXISTS chk_treeholes_mood;
ALTER TABLE sxw_treeholes
    ADD CONSTRAINT chk_treeholes_mood CHECK (mood IN ('开心', '平静', '疲惫', '焦虑', '难过'));
