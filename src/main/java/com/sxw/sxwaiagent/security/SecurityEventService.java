package com.sxw.sxwaiagent.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class SecurityEventService {
    private final JdbcTemplate jdbc;
    public SecurityEventService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String record(Long userId, String conversationId, String requestId, PromptSafetyDecision decision, String source) {
        if (decision.riskLevel() == PromptRiskLevel.LOW) return null;
        String id = "sec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        try { jdbc.update("INSERT INTO ai_security_event(event_id,user_id,conversation_id,request_id,risk_level,action,source,reasons,content_hash) VALUES (?,?,?,?,?,?,?,?,?)", id, userId, conversationId, requestId, decision.riskLevel().name(), decision.blocked() ? "BLOCKED" : decision.sanitized() ? "SANITIZED" : "OBSERVED", source, String.join("|", decision.reasons()), sha256(String.join("|", decision.reasons()))); return id; }
        catch (Exception ignored) { return id; }
    }
    public List<Map<String,Object>> list(int limit) { return jdbc.query("SELECT event_id,user_id,conversation_id,request_id,risk_level,action,source,reasons,created_at FROM ai_security_event ORDER BY created_at DESC LIMIT ?", (rs,n)->Map.of("eventId",rs.getString(1),"userId",rs.getObject(2),"conversationId",rs.getString(3),"requestId",rs.getString(4),"riskLevel",rs.getString(5),"action",rs.getString(6),"source",rs.getString(7),"reasons",rs.getString(8),"createdAt",rs.getTimestamp(9).toInstant().toString()), Math.min(Math.max(limit,1),200)); }
    private static String sha256(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); return HexFormat.of().formatHex(bytes); } catch (Exception e) { return ""; } }
}
