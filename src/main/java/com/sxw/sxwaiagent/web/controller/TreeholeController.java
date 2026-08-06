package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.treehole.TreeholeService;
import com.sxw.sxwaiagent.treehole.dto.CreateTreeholeRequest;
import com.sxw.sxwaiagent.treehole.dto.TreeholeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "树洞", description = "用户私密树洞记录的创建、查询与删除")
@RestController
@RequestMapping("/api/treeholes")
public class TreeholeController {

    // TODO(treehole-workspace): 增加情绪标签写入、语音转文字、AI 陪伴引导、
    // 心情趋势 / 日历统计、写作灵感、收藏与归档等接口；当前仅提供基础树洞 CRUD。

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
    public Result<List<TreeholeResponse>> list(Authentication authentication) {
        return Result.ok(treeholeService.list(currentUser(authentication).userId()));
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

    private static AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
