package com.sxw.sxwaiagent.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveNoteRequest(
        Long id,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 100_000) String content,
        @Size(max = 12) List<@Size(max = 32) String> tags,
        boolean favorite
) {
}
