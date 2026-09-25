package com.sxw.sxwaiagent.note.dto;

import com.sxw.sxwaiagent.note.model.NoteEntry;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record NoteResponse(
        Long id,
        String title,
        String content,
        List<String> tags,
        boolean favorite,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static NoteResponse from(NoteEntry note) {
        List<String> parsedTags = note.getTags() == null || note.getTags().isBlank()
                ? List.of()
                : Arrays.stream(note.getTags().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        return new NoteResponse(note.getId(), note.getTitle(), note.getContent(), parsedTags,
                note.isFavorite(), note.getVersion(), note.getCreatedAt(), note.getUpdatedAt());
    }
}
