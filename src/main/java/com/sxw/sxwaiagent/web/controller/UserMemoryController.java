package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.memory.UserMemoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memories")
public class UserMemoryController {
    private final UserMemoryService service;

    public UserMemoryController(UserMemoryService service) { this.service = service; }

    @GetMapping
    public Result<List<Map<String, Object>>> list(Authentication authentication,
                                                  @RequestParam(required = false) String status) {
        return Result.ok(service.list(user(authentication), status));
    }

    @PostMapping
    public Result<Map<String, Object>> explicit(Authentication authentication,
                                                @Valid @RequestBody CreateMemory body) {
        return Result.ok(service.explicit(user(authentication), body.conversationId(), body.content(),
                body.scopeType(), body.scopeId()));
    }

    @GetMapping("/candidates")
    public Result<List<Map<String, Object>>> candidates(Authentication authentication) {
        return Result.ok(service.candidates(user(authentication)));
    }

    @PatchMapping("/candidates/{candidateId}/decision")
    public Result<Void> candidateDecision(Authentication authentication, @PathVariable String candidateId,
                                          @RequestBody Map<String, Object> body) {
        service.decideCandidate(user(authentication), candidateId, Boolean.TRUE.equals(body.get("approve")));
        return Result.ok();
    }

    /** Compatibility endpoint retained for the existing chat panel. */
    @PatchMapping("/{memoryId}/decision")
    public Result<Void> decision(Authentication authentication, @PathVariable String memoryId,
                                 @RequestBody Map<String, Object> body) {
        service.decide(user(authentication), memoryId, Boolean.TRUE.equals(body.get("approve")),
                Boolean.TRUE.equals(body.get("alwaysOn")));
        return Result.ok();
    }

    @PatchMapping("/{memoryId}/archive")
    public Result<Void> archive(Authentication authentication, @PathVariable String memoryId) {
        service.archive(user(authentication), memoryId); return Result.ok();
    }

    @PatchMapping("/{memoryId}/restore")
    public Result<Void> restore(Authentication authentication, @PathVariable String memoryId) {
        service.restore(user(authentication), memoryId); return Result.ok();
    }

    @PatchMapping("/{memoryId}/pin")
    public Result<Void> pin(Authentication authentication, @PathVariable String memoryId,
                            @RequestBody Map<String, Object> body) {
        service.setAlwaysOn(user(authentication), memoryId, Boolean.TRUE.equals(body.get("alwaysOn")));
        return Result.ok();
    }

    @DeleteMapping("/{memoryId}")
    public Result<Void> delete(Authentication authentication, @PathVariable String memoryId,
                               @RequestParam(required = false) String reason) {
        service.delete(user(authentication), memoryId, reason); return Result.ok();
    }

    @PostMapping("/{memoryId}/purge")
    public Result<Void> purge(Authentication authentication, @PathVariable String memoryId,
                              @RequestBody(required = false) Map<String, String> body) {
        service.purge(user(authentication), memoryId, body == null ? null : body.get("reason"));
        return Result.ok();
    }

    @GetMapping("/forget-preview")
    public Result<List<Map<String, Object>>> forgetPreview(Authentication authentication,
                                                            @RequestParam @Size(max = 200) String query) {
        return Result.ok(service.forgetPreview(user(authentication), query));
    }

    private long user(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }

    public record CreateMemory(@NotBlank @Size(max = 500) String content,
                               @Size(max = 64) String conversationId,
                               @Size(max = 32) String scopeType,
                               @Size(max = 64) String scopeId) { }
}
