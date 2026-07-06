package com.sxw.sxwaiagent.agent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Prompt 执行记录器
 * 
 * 将每次 Prompt 组装的元数据（版本号、哈希值、长度）持久化到 ai_prompt_run 表，
 * 用于后续的版本对比、回归检测和可观测性分析。
 */
@Component
public class PromptRunRecorder {
    
    private static final Logger log = LoggerFactory.getLogger(PromptRunRecorder.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public PromptRunRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 记录一次 Prompt 执行
     * 
     * @param requestId 请求 ID
     * @param promptCode Prompt 代码（如 PROFILE_CODE）
     * @param promptVersion Prompt 版本号
     * @param assembledPrompt 组装后的 Prompt
     */
    public void record(String requestId, String promptCode, int promptVersion, AssembledPrompt assembledPrompt) {
        try {
            jdbcTemplate.update("""
                INSERT INTO ai_prompt_run (
                    request_id, prompt_code, prompt_version,
                    static_hash, dynamic_hash, rendered_hash,
                    rendered_length, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId,
                promptCode,
                promptVersion,
                assembledPrompt.staticHash(),
                assembledPrompt.dynamicHash(),
                assembledPrompt.renderedHash(),
                assembledPrompt.renderedLength(),
                Timestamp.from(Instant.now())
            );
            
            log.debug("Recorded prompt run: requestId={}, code={}, version={}, length={}",
                requestId, promptCode, promptVersion, assembledPrompt.renderedLength());
        } catch (Exception e) {
            log.error("Failed to record prompt run: requestId={}, error={}", requestId, e.getMessage());
        }
    }
}
