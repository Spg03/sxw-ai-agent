package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.treehole.TreeholeService;
import com.sxw.sxwaiagent.treehole.dto.CreateTreeholeRequest;
import com.sxw.sxwaiagent.treehole.dto.TreeholeResponse;
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

@RestController
@RequestMapping("/treeholes")
public class TreeholeController {

    private final TreeholeService treeholeService;

    public TreeholeController(TreeholeService treeholeService) {
        this.treeholeService = treeholeService;
    }

    @PostMapping
    public Result<TreeholeResponse> create(Authentication authentication,
                                           @Valid @RequestBody CreateTreeholeRequest request) {
        return Result.ok(treeholeService.create(currentUser(authentication).userId(), request));
    }

    @GetMapping
    public Result<List<TreeholeResponse>> list(Authentication authentication) {
        return Result.ok(treeholeService.list(currentUser(authentication).userId()));
    }

    @GetMapping("/{id}")
    public Result<TreeholeResponse> get(Authentication authentication, @PathVariable Long id) {
        return Result.ok(treeholeService.get(currentUser(authentication).userId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(Authentication authentication, @PathVariable Long id) {
        treeholeService.delete(currentUser(authentication).userId(), id);
        return Result.ok();
    }

    private static AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
