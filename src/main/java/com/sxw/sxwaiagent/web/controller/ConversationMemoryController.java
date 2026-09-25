package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.conversation.ConversationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/conversations/{conversationId}/memory")
public class ConversationMemoryController {
 private final ConversationService conversations; private final JdbcTemplate jdbc;
 public ConversationMemoryController(ConversationService conversations,JdbcTemplate jdbc){this.conversations=conversations;this.jdbc=jdbc;}
 @GetMapping("/status") public Result<Map<String,Object>> status(Authentication a,@PathVariable String conversationId){long user=user(a);conversations.get(user,conversationId);String summary=jdbc.query("SELECT content FROM ai_conversation_summary_version WHERE conversation_id=? AND status='SUCCESS' ORDER BY version_no DESC LIMIT 1",rs->rs.next()?rs.getString(1):null,conversationId);Integer version=jdbc.query("SELECT version_no FROM ai_working_memory_version WHERE conversation_id=? ORDER BY version_no DESC LIMIT 1",rs->rs.next()?rs.getInt(1):null,conversationId);Long count=jdbc.queryForObject("SELECT count(*) FROM ai_conversation_message WHERE conversation_id=?",Long.class,conversationId);return Result.ok(Map.of("messageCount",count,"hasSummary",summary!=null,"workingMemoryVersion",version==null?0:version));}
 private long user(Authentication a){return ((AuthenticatedUser)a.getPrincipal()).userId();}
}
