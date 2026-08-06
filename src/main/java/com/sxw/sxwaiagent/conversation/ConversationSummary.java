package com.sxw.sxwaiagent.conversation;

import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import java.time.Instant;

public record ConversationSummary(String conversationId, String title, AgentProfileCode profile,
                                  boolean pinned, String rollingSummary, Instant createdAt, Instant updatedAt) {}
