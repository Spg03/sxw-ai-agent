package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.security.SecurityEventService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/admin/security-events")
public class SecurityAuditController {
 private final SecurityEventService events; public SecurityAuditController(SecurityEventService events){this.events=events;}
 @GetMapping public Result<List<Map<String,Object>>> list(@RequestParam(defaultValue="50") int limit){return Result.ok(events.list(limit));}
}
