package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.infrastructure.skill.NoteSkill;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记技能 REST 端点：无需大模型即可冒烟验证 {@link NoteSkill}。
 */
@Tag(name = "笔记管理", description = "笔记的创建、读取、追加、搜索与删除")
@RestController
@RequestMapping("/api/notes")
@Validated
public class NoteController {

    private final NoteSkill noteSkill;

    public NoteController(NoteSkill noteSkill) {
        this.noteSkill = noteSkill;
    }

    public record NoteBody(@NotBlank @Size(max = 64) String title,
                           @NotBlank String content) {
    }

    @Operation(summary = "创建笔记")
    @PostMapping
    public Result<String> create(@RequestBody NoteBody body) {
        return Result.ok(noteSkill.createNote(body.title(), body.content()));
    }

    @Operation(summary = "追加笔记内容")
    @PostMapping("/append")
    public Result<String> append(@RequestBody NoteBody body) {
        return Result.ok(noteSkill.appendNote(body.title(), body.content()));
    }

    @Operation(summary = "读取笔记")
    @GetMapping
    public Result<String> read(@RequestParam @NotBlank @Size(max = 64) String title) {
        return Result.ok(noteSkill.readNote(title));
    }

    @Operation(summary = "列出所有笔记")
    @GetMapping("/list")
    public Result<String> list() {
        return Result.ok(noteSkill.listNotes());
    }

    @Operation(summary = "搜索笔记", description = "根据关键词搜索笔记内容")
    @GetMapping("/search")
    public Result<String> search(@RequestParam @NotBlank @Size(max = 64) String keyword) {
        return Result.ok(noteSkill.searchNotes(keyword));
    }

    @Operation(summary = "删除笔记")
    @DeleteMapping
    public Result<String> delete(@RequestParam @NotBlank @Size(max = 64) String title) {
        return Result.ok(noteSkill.deleteNote(title));
    }
}
