package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.conversation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService service;
    public ConversationController(ConversationService service) { this.service=service; }
    @PostMapping public Result<ConversationSummary> create(Authentication a,@Valid @RequestBody Create body) { return Result.ok(service.create(user(a),body.profile()==null?AgentProfileCode.GENERAL:body.profile(),body.title())); }
    @GetMapping public Result<List<ConversationSummary>> list(Authentication a,@RequestParam(defaultValue="50") int limit) { return Result.ok(service.list(user(a),limit)); }
    @GetMapping("/{id}") public Result<ConversationSummary> get(Authentication a,@PathVariable String id) { return Result.ok(service.get(user(a),id)); }
    @GetMapping("/{id}/messages") public Result<List<ConversationMessage>> messages(Authentication a,@PathVariable String id,@RequestParam(defaultValue="100") int limit) { return Result.ok(service.messages(user(a),id,limit)); }
    @PatchMapping("/{id}") public Result<ConversationSummary> update(Authentication a,@PathVariable String id,@RequestBody Update body) { return Result.ok(service.update(user(a),id,body.title(),body.profile(),body.pinned())); }
    @DeleteMapping("/{id}") public Result<Void> delete(Authentication a,@PathVariable String id) { service.delete(user(a),id); return Result.ok(); }
    private long user(Authentication a) { return ((AuthenticatedUser)a.getPrincipal()).userId(); }
    public record Create(@Size(max=160) String title, AgentProfileCode profile) {}
    public record Update(@Size(max=160) String title, AgentProfileCode profile, Boolean pinned) {}
}
