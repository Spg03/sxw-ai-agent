package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.note.NoteWorkspaceService;
import com.sxw.sxwaiagent.note.dto.NoteResponse;
import com.sxw.sxwaiagent.note.dto.SaveNoteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "笔记管理", description = "当前用户的笔记、标签、收藏、关联与导出")
@RestController
@RequestMapping("/api/notes")
@Validated
public class NoteController {

    private final NoteWorkspaceService noteService;

    public NoteController(NoteWorkspaceService noteService) {
        this.noteService = noteService;
    }

    public record AppendBody(@NotBlank @Size(max = 120) String title,
                             @NotBlank @Size(max = 100_000) String content) {}

    public record FavoriteBody(boolean favorite) {}

    public record NoteExport(String filename, String mediaType, String content) {}

    @Operation(summary = "创建或更新我的笔记")
    @PostMapping
    public Result<NoteResponse> save(Authentication authentication,
                                     @Valid @RequestBody SaveNoteRequest body) {
        return Result.ok(noteService.save(userId(authentication), body));
    }

    @Operation(summary = "追加笔记内容")
    @PostMapping("/append")
    public Result<NoteResponse> append(Authentication authentication,
                                       @Valid @RequestBody AppendBody body) {
        return Result.ok(noteService.append(userId(authentication), body.title(), body.content()));
    }

    @Operation(summary = "按标题读取我的笔记", description = "兼容旧版前端")
    @GetMapping
    public Result<NoteResponse> read(Authentication authentication,
                                     @RequestParam @NotBlank @Size(max = 120) String title) {
        return Result.ok(noteService.getByTitle(userId(authentication), title));
    }

    @Operation(summary = "读取我的笔记")
    @GetMapping("/{id}")
    public Result<NoteResponse> readById(Authentication authentication, @PathVariable Long id) {
        return Result.ok(noteService.get(userId(authentication), id));
    }

    @Operation(summary = "列出我的笔记")
    @GetMapping("/list")
    public Result<List<NoteResponse>> list(Authentication authentication,
                                           @RequestParam(required = false) String query,
                                           @RequestParam(required = false) Boolean favorite) {
        return Result.ok(noteService.list(userId(authentication), query, favorite));
    }

    @Operation(summary = "搜索我的笔记", description = "兼容旧版搜索路径")
    @GetMapping("/search")
    public Result<List<NoteResponse>> search(Authentication authentication,
                                             @RequestParam @NotBlank @Size(max = 120) String keyword) {
        return Result.ok(noteService.list(userId(authentication), keyword, false));
    }

    @Operation(summary = "设置收藏状态")
    @PatchMapping("/{id}/favorite")
    public Result<NoteResponse> favorite(Authentication authentication,
                                         @PathVariable Long id,
                                         @RequestBody FavoriteBody body) {
        return Result.ok(noteService.favorite(userId(authentication), id, body.favorite()));
    }

    @Operation(summary = "获取相关笔记")
    @GetMapping("/{id}/related")
    public Result<List<NoteResponse>> related(Authentication authentication,
                                              @PathVariable Long id,
                                              @RequestParam(defaultValue = "4") int limit) {
        return Result.ok(noteService.related(userId(authentication), id, limit));
    }

    @Operation(summary = "导出 Markdown")
    @GetMapping("/{id}/export")
    public Result<NoteExport> export(Authentication authentication, @PathVariable Long id) {
        NoteResponse note = noteService.get(userId(authentication), id);
        return Result.ok(new NoteExport(safeFilename(note.title()) + ".md", "text/markdown;charset=UTF-8", note.content()));
    }

    @Operation(summary = "删除我的笔记")
    @DeleteMapping("/{id}")
    public Result<Void> delete(Authentication authentication, @PathVariable Long id) {
        noteService.delete(userId(authentication), id);
        return Result.ok();
    }

    private static Long userId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }

    private static String safeFilename(String title) {
        String sanitized = title.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        return sanitized.isBlank() ? "note" : sanitized;
    }
}
