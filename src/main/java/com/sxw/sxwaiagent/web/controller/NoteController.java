package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.Result;
import com.sxw.sxwaiagent.infrastructure.skill.NoteSkill;
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
@RestController
@RequestMapping("/notes")
@Validated
public class NoteController {

    private final NoteSkill noteSkill;

    public NoteController(NoteSkill noteSkill) {
        this.noteSkill = noteSkill;
    }

    public record NoteBody(@NotBlank @Size(max = 64) String title,
                           @NotBlank String content) {
    }

    @PostMapping
    public Result<String> create(@RequestBody NoteBody body) {
        return Result.ok(noteSkill.createNote(body.title(), body.content()));
    }

    @PostMapping("/append")
    public Result<String> append(@RequestBody NoteBody body) {
        return Result.ok(noteSkill.appendNote(body.title(), body.content()));
    }

    @GetMapping
    public Result<String> read(@RequestParam @NotBlank @Size(max = 64) String title) {
        return Result.ok(noteSkill.readNote(title));
    }

    @GetMapping("/list")
    public Result<String> list() {
        return Result.ok(noteSkill.listNotes());
    }

    @GetMapping("/search")
    public Result<String> search(@RequestParam @NotBlank @Size(max = 64) String keyword) {
        return Result.ok(noteSkill.searchNotes(keyword));
    }

    @DeleteMapping
    public Result<String> delete(@RequestParam @NotBlank @Size(max = 64) String title) {
        return Result.ok(noteSkill.deleteNote(title));
    }
}
