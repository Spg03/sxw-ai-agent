-- sxw-ai-agent Harness 平台建表脚本
-- 覆盖 P4/P5/P8/P9/P10/P11 全部新增表
-- 执行：psql -h localhost -U postgres -d sxw_ai_agent -f V1__harness_tables.sql

-- ============================================================
-- P4: Prompt 版本化
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_prompt_version (
    id              BIGSERIAL PRIMARY KEY,
    prompt_code     VARCHAR(64)  NOT NULL,
    version         INT          NOT NULL,
    section_key     VARCHAR(64)  NOT NULL,
    content         TEXT         NOT NULL,
    description     VARCHAR(512),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(prompt_code, version, section_key)
);

CREATE TABLE IF NOT EXISTS ai_prompt_run (
    id                  BIGSERIAL PRIMARY KEY,
    request_id          VARCHAR(64)  NOT NULL,
    prompt_code         VARCHAR(64)  NOT NULL,
    prompt_version      INT          NOT NULL,
    static_hash         VARCHAR(64),
    dynamic_hash        VARCHAR(64),
    rendered_hash       VARCHAR(64),
    rendered_length     INT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_prompt_run_request ON ai_prompt_run(request_id);

-- ============================================================
-- P4: Context 审计
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_context_item (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL,
    section_type    VARCHAR(64)  NOT NULL,
    item_type       VARCHAR(64),
    item_id         VARCHAR(128),
    token_count     INT,
    content_preview TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_context_item_request ON ai_context_item(request_id);

-- ============================================================
-- P5: 自研 Knowledge Base (PgVector)
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_knowledge_document (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          VARCHAR(64)  NOT NULL UNIQUE,
    title           VARCHAR(255) NOT NULL,
    source_type     VARCHAR(64),
    source_path     VARCHAR(512),
    content_hash    VARCHAR(64),
    chunk_count     INT          NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON COLUMN ai_knowledge_document.source_type IS '文档来源类型（可选）';

CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id              BIGSERIAL PRIMARY KEY,
    chunk_id        VARCHAR(64)  NOT NULL UNIQUE,
    doc_id          VARCHAR(64)  NOT NULL REFERENCES ai_knowledge_document(doc_id),
    chunk_index     INT          NOT NULL,
    breadcrumb      VARCHAR(512),
    content         TEXT         NOT NULL,
    token_count     INT,
    embedding       vector(1536),
    metadata        JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON COLUMN ai_knowledge_chunk.breadcrumb IS '文档块面包屑路径（如 ## Heading > ### Sub）';
CREATE INDEX IF NOT EXISTS idx_chunk_doc ON ai_knowledge_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON ai_knowledge_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE TABLE IF NOT EXISTS ai_knowledge_hit (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL,
    chunk_id        VARCHAR(64)  NOT NULL,
    doc_id          VARCHAR(64)  NOT NULL,
    score           DECIMAL(8,6) NOT NULL,
    rank            INT          NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_hit_request ON ai_knowledge_hit(request_id);

-- ============================================================
-- P8: Hermes 候选系统
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_hermes_candidate (
    id                  BIGSERIAL PRIMARY KEY,
    candidate_id        VARCHAR(64)  NOT NULL UNIQUE,
    run_id              VARCHAR(64)  NOT NULL,
    chat_id             VARCHAR(64)  NOT NULL,
    type                VARCHAR(64)  NOT NULL,
    title               VARCHAR(255) NOT NULL,
    content             TEXT         NOT NULL,
    metadata            TEXT,
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    reviewed_by         VARCHAR(128),
    reviewed_at         TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON COLUMN ai_hermes_candidate.run_id IS '产生该候选的 Agent 运行 ID';
COMMENT ON COLUMN ai_hermes_candidate.chat_id IS '关联的会话 ID';
COMMENT ON COLUMN ai_hermes_candidate.type IS '候选类型（CandidateType 枚举）';
COMMENT ON COLUMN ai_hermes_candidate.metadata IS '附加元数据（JSON 字符串）';
CREATE INDEX IF NOT EXISTS idx_candidate_status ON ai_hermes_candidate(status);
CREATE INDEX IF NOT EXISTS idx_candidate_run ON ai_hermes_candidate(run_id);

-- ============================================================
-- P9: Trace + Eval
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_request_trace (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL UNIQUE,
    trace_id        VARCHAR(64),
    chat_id         VARCHAR(64),
    profile_code    VARCHAR(32),
    run_mode        VARCHAR(32),
    user_message    TEXT,
    final_answer    TEXT,
    total_tokens    INT,
    latency_ms      BIGINT,
    status          VARCHAR(32),
    started_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMP,
    events_json     TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_request_trace_chat_id ON ai_request_trace(chat_id);
CREATE INDEX IF NOT EXISTS idx_request_trace_trace_id ON ai_request_trace(trace_id);
CREATE INDEX IF NOT EXISTS idx_request_trace_created_at ON ai_request_trace(created_at);

CREATE TABLE IF NOT EXISTS ai_model_call (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL,
    call_index      INT          NOT NULL,
    model_name      VARCHAR(128),
    prompt_tokens   INT,
    completion_tokens INT,
    latency_ms      BIGINT,
    has_tool_calls  BOOLEAN,
    tool_call_names TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_model_call_request ON ai_model_call(request_id);

CREATE TABLE IF NOT EXISTS ai_tool_call (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL,
    trace_id        VARCHAR(64),
    tool_name       VARCHAR(128) NOT NULL,
    arguments       TEXT,
    result_preview  TEXT,
    risk_level      VARCHAR(32),
    approved        BOOLEAN,
    latency_ms      BIGINT,
    success         BOOLEAN,
    error_message   TEXT,
    turn            INT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tool_call_request ON ai_tool_call(request_id);

CREATE TABLE IF NOT EXISTS ai_eval_case (
    id              BIGSERIAL PRIMARY KEY,
    case_id         VARCHAR(64)  NOT NULL UNIQUE,
    profile_code    VARCHAR(32),
    question        TEXT         NOT NULL,
    expected_answer TEXT,
    eval_criteria   TEXT,
    source_request_id VARCHAR(64),
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_eval_run (
    id              BIGSERIAL PRIMARY KEY,
    run_id          VARCHAR(64)  NOT NULL UNIQUE,
    case_id         VARCHAR(64)  NOT NULL,
    profile_code    VARCHAR(32),
    prompt_version  INT,
    actual_answer   TEXT,
    score           DECIMAL(5,4),
    pass            BOOLEAN,
    latency_ms      BIGINT,
    model_name      VARCHAR(128),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_eval_run_case ON ai_eval_run(case_id);

-- ============================================================
-- P10: Memory 结构化记忆
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_memory_item (
    id                  BIGSERIAL PRIMARY KEY,
    memory_id           VARCHAR(64)  NOT NULL UNIQUE,
    memory_type         VARCHAR(64)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    rule_text           TEXT,
    why_text            TEXT,
    apply_text          TEXT,
    source_trace_id     VARCHAR(64),
    confidence          DECIMAL(5,4),
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    expired_at          TIMESTAMP,
    reviewed_at         TIMESTAMP,
    reviewed_by         VARCHAR(128)
);
COMMENT ON COLUMN ai_memory_item.memory_id IS '记忆项唯一业务 ID';
COMMENT ON COLUMN ai_memory_item.memory_type IS '记忆类型（MemoryType 枚举：USER/FEEDBACK/PROJECT/REFERENCE）';
COMMENT ON COLUMN ai_memory_item.rule_text IS '规则描述（Detail 部分）';
COMMENT ON COLUMN ai_memory_item.why_text IS '原因说明（Detail 部分）';
COMMENT ON COLUMN ai_memory_item.apply_text IS '应用建议（Detail 部分）';
COMMENT ON COLUMN ai_memory_item.confidence IS '置信度 0~1';
COMMENT ON COLUMN ai_memory_item.expired_at IS '过期时间，NULL 表示永不过期';
CREATE INDEX IF NOT EXISTS idx_memory_type ON ai_memory_item(memory_type);
CREATE INDEX IF NOT EXISTS idx_memory_status ON ai_memory_item(status);

-- ============================================================
-- P11: Plan 执行计划
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_plan (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         VARCHAR(64)  NOT NULL UNIQUE,
    chat_id         VARCHAR(64)  NOT NULL,
    goal            TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    created_by      VARCHAR(128),
    reviewed_by     VARCHAR(128),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    reviewed_at     TIMESTAMP
);
COMMENT ON COLUMN ai_plan.plan_id IS '计划唯一业务 ID';
COMMENT ON COLUMN ai_plan.chat_id IS '关联的会话 ID';
COMMENT ON COLUMN ai_plan.goal IS '计划目标描述';
COMMENT ON COLUMN ai_plan.status IS '计划状态（PlanStatus 枚举：DRAFT/PENDING/APPROVED/REJECTED/COMPLETED）';
CREATE INDEX IF NOT EXISTS idx_plan_chat ON ai_plan(chat_id);
CREATE INDEX IF NOT EXISTS idx_plan_status ON ai_plan(status);

CREATE TABLE IF NOT EXISTS ai_plan_step (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         VARCHAR(64)  NOT NULL REFERENCES ai_plan(plan_id),
    step_index      INT          NOT NULL,
    description     TEXT         NOT NULL,
    tool_name       VARCHAR(128),
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    UNIQUE(plan_id, step_index)
);
COMMENT ON COLUMN ai_plan_step.step_index IS '步骤序号（从 0 开始）';
COMMENT ON COLUMN ai_plan_step.tool_name IS '推荐使用的工具名称';
COMMENT ON COLUMN ai_plan_step.status IS '步骤状态（StepStatus 枚举：PENDING/IN_PROGRESS/COMPLETED/FAILED）';
CREATE INDEX IF NOT EXISTS idx_plan_step_plan ON ai_plan_step(plan_id);

-- ============================================================
-- P12: Tool Audit Log（从内存迁移到 PostgreSQL）
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_tool_audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    request_id          VARCHAR(64)  NOT NULL,
    trace_id            VARCHAR(64),
    turn                INT          NOT NULL DEFAULT 0,
    tool_name           VARCHAR(128) NOT NULL,
    risk_level          VARCHAR(32),
    arguments           TEXT,
    result              TEXT,
    status              VARCHAR(32),
    latency_ms          BIGINT,
    approved            BOOLEAN      NOT NULL DEFAULT FALSE,
    approval_id         VARCHAR(128),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON COLUMN ai_tool_audit_log.request_id IS '请求 ID，用于按请求分组审计';
COMMENT ON COLUMN ai_tool_audit_log.trace_id IS '追踪 ID，用于端到端追踪';
COMMENT ON COLUMN ai_tool_audit_log.turn IS 'Agent 执行轮次';
COMMENT ON COLUMN ai_tool_audit_log.risk_level IS '工具风险等级（ToolRiskLevel 枚举）';
COMMENT ON COLUMN ai_tool_audit_log.status IS '执行状态（SUCCESS/FAILURE/SKIPPED 等）';
COMMENT ON COLUMN ai_tool_audit_log.approved IS '是否经过审批';
COMMENT ON COLUMN ai_tool_audit_log.approval_id IS '关联的审批请求 ID';
CREATE INDEX IF NOT EXISTS idx_tool_audit_log_request ON ai_tool_audit_log(request_id);
CREATE INDEX IF NOT EXISTS idx_tool_audit_log_trace ON ai_tool_audit_log(trace_id);
CREATE INDEX IF NOT EXISTS idx_tool_audit_log_tool_name ON ai_tool_audit_log(tool_name);
CREATE INDEX IF NOT EXISTS idx_tool_audit_log_created_at ON ai_tool_audit_log(created_at DESC);

-- ============================================================
-- P12: Approval Request（从内存迁移到 PostgreSQL）
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_approval_request (
    id                  BIGSERIAL PRIMARY KEY,
    approval_id         VARCHAR(128) NOT NULL UNIQUE,
    request_id          VARCHAR(64)  NOT NULL,
    trace_id            VARCHAR(64),
    tool_name           VARCHAR(128) NOT NULL,
    arguments           TEXT,
    reason              TEXT,
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    approved_by         VARCHAR(128),
    comment             TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP
);
COMMENT ON COLUMN ai_approval_request.approval_id IS '审批请求唯一业务 ID';
COMMENT ON COLUMN ai_approval_request.request_id IS '关联的 Agent 请求 ID';
COMMENT ON COLUMN ai_approval_request.trace_id IS '关联的追踪 ID';
COMMENT ON COLUMN ai_approval_request.status IS '审批状态（PENDING/APPROVED/REJECTED/EXPIRED）';
COMMENT ON COLUMN ai_approval_request.approved_by IS '审批人标识';
COMMENT ON COLUMN ai_approval_request.comment IS '审批意见';
COMMENT ON COLUMN ai_approval_request.expires_at IS '过期时间，NULL 表示永不过期';
CREATE INDEX IF NOT EXISTS idx_approval_request_request ON ai_approval_request(request_id);
CREATE INDEX IF NOT EXISTS idx_approval_request_status ON ai_approval_request(status);
CREATE INDEX IF NOT EXISTS idx_approval_request_tool_name ON ai_approval_request(tool_name);
CREATE INDEX IF NOT EXISTS idx_approval_request_expires_at ON ai_approval_request(expires_at);
