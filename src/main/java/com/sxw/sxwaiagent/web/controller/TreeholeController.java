package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.treehole.TreeholeService;
import com.sxw.sxwaiagent.treehole.dto.CreateTreeholeRequest;
import com.sxw.sxwaiagent.treehole.dto.TreeholeResponse;
import com.sxw.sxwaiagent.treehole.dto.TreeholeInsightsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "树洞", description = "用户私密树洞记录的创建、查询与删除")
@RestController
@RequestMapping("/api/treeholes")
public class TreeholeController {

    // TODO(treehole-voice): 语音转文字需在选定 ASR 供应商后接入，当前明确返回不可用。

    private final TreeholeService treeholeService;

    public TreeholeController(TreeholeService treeholeService) {
        this.treeholeService = treeholeService;
    }

    @Operation(summary = "创建树洞记录")
    @PostMapping
    public Result<TreeholeResponse> create(Authentication authentication,
                                           @Valid @RequestBody CreateTreeholeRequest request) {
        return Result.ok(treeholeService.create(currentUser(authentication).userId(), request));
    }

    @Operation(summary = "列出我的树洞记录")
    @GetMapping
    public Result<List<TreeholeResponse>> list(Authentication authentication,
                                               @RequestParam(defaultValue = "false") boolean archived) {
        return Result.ok(treeholeService.list(currentUser(authentication).userId(), archived));
    }

    @Operation(summary = "获取树洞记录详情")
    @GetMapping("/{id}")
    public Result<TreeholeResponse> get(Authentication authentication, @PathVariable Long id) {
        return Result.ok(treeholeService.get(currentUser(authentication).userId(), id));
    }

    @Operation(summary = "删除树洞记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(Authentication authentication, @PathVariable Long id) {
        treeholeService.delete(currentUser(authentication).userId(), id);
        return Result.ok();
    }

    public record StateBody(boolean value) {}

    @Operation(summary = "归档或恢复树洞记录")
    @PatchMapping("/{id}/archive")
    public Result<TreeholeResponse> archive(Authentication authentication,
                                            @PathVariable Long id,
                                            @RequestBody StateBody body) {
        return Result.ok(treeholeService.archive(currentUser(authentication).userId(), id, body.value()));
    }

    @Operation(summary = "收藏或取消收藏树洞记录")
    @PatchMapping("/{id}/favorite")
    public Result<TreeholeResponse> favorite(Authentication authentication,
                                             @PathVariable Long id,
                                             @RequestBody StateBody body) {
        return Result.ok(treeholeService.favorite(currentUser(authentication).userId(), id, body.value()));
    }

    @Operation(summary = "获取我的心情趋势和日历统计")
    @GetMapping("/insights")
    public Result<TreeholeInsightsResponse> insights(Authentication authentication,
                                                      @RequestParam(defaultValue = "30") int days) {
        return Result.ok(treeholeService.insights(currentUser(authentication).userId(), days));
    }

    @Operation(summary = "获取写作灵感")
    @GetMapping("/prompts")
    public Result<List<String>> prompts() {
        return Result.ok(treeholeService.prompts(LocalDate.now()));
    }

    private static AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
