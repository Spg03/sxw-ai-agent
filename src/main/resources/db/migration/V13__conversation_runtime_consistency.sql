-- P0 conversation runtime consistency and replay support.
ALTER TABLE ai_conversation ADD COLUMN IF NOT EXISTS active_turn_id VARCHAR(64);
ALTER TABLE ai_conversation ADD COLUMN IF NOT EXISTS active_request_id VARCHAR(64);
ALTER TABLE ai_conversation ADD COLUMN IF NOT EXISTS active_turn_started_at TIMESTAMP;
ALTER TABLE ai_conversation ADD COLUMN IF NOT EXISTS runtime_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE ai_agent_run ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE ai_agent_run ADD COLUMN IF NOT EXISTS error_message TEXT;
CREATE INDEX IF NOT EXISTS idx_agent_run_trace ON ai_agent_run(trace_id);

-- Backfill turn boundaries for messages created before the append-only runtime.
WITH legacy_turns AS (
    SELECT id,conversation_id,
           count(*) FILTER (WHERE role='USER') OVER (
               PARTITION BY conversation_id ORDER BY sequence_no,id
           ) AS turn_no
    FROM ai_conversation_message
)
UPDATE ai_conversation_message message
SET turn_id='legacy_turn_' || legacy.turn_no
FROM legacy_turns legacy
WHERE message.id=legacy.id AND message.turn_id IS NULL AND legacy.turn_no>0;

-- One request can emit one user event and one final assistant event. Tool events use
-- their own message_type and tool_call_id and therefore are not covered here.
WITH duplicate_finals AS (
    SELECT id, row_number() OVER (
        PARTITION BY conversation_id,request_id,message_type ORDER BY sequence_no DESC,id DESC
    ) AS duplicate_no
    FROM ai_conversation_message
    WHERE request_id IS NOT NULL AND message_type IN ('USER','ASSISTANT')
)
DELETE FROM ai_conversation_message message
USING duplicate_finals duplicate
WHERE message.id=duplicate.id AND duplicate.duplicate_no>1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_conversation_request_final
    ON ai_conversation_message(conversation_id, request_id, message_type)
    WHERE request_id IS NOT NULL AND message_type IN ('USER', 'ASSISTANT');

CREATE INDEX IF NOT EXISTS idx_conversation_processing
    ON ai_conversation_message(conversation_id, processing_status, sequence_no);

-- A crashed worker must not leave jobs RUNNING forever. The worker periodically
-- returns stale leases to PENDING before claiming the next job.
CREATE INDEX IF NOT EXISTS idx_outbox_running_lease
    ON ai_outbox_event(status, locked_at)
    WHERE status = 'RUNNING';
