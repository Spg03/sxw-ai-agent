package com.sxw.sxwaiagent.web.controller;
import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.memory.UserMemoryService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/memories") public class UserMemoryController { private final UserMemoryService s; public UserMemoryController(UserMemoryService s){this.s=s;} private long u(Authentication a){return ((AuthenticatedUser)a.getPrincipal()).userId();} @GetMapping public Result<List<Map<String,Object>>> list(Authentication a,@RequestParam(required=false) String status){return Result.ok(s.list(u(a),status));} @PostMapping public Result<Map<String,Object>> explicit(Authentication a,@RequestBody Map<String,String> body){return Result.ok(s.explicit(u(a),body.get("content")));} @PatchMapping("/{memoryId}/decision") public Result<Void> decide(Authentication a,@PathVariable String memoryId,@RequestBody Map<String,Object> body){s.decide(u(a),memoryId,Boolean.TRUE.equals(body.get("approve")),Boolean.TRUE.equals(body.get("alwaysOn")));return Result.ok(null);} }
