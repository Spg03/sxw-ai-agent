package com.sxw.sxwaiagent.treehole.dto;

import com.sxw.sxwaiagent.treehole.model.TreeholeEntry;

import java.time.Instant;

public record TreeholeResponse(
        Long id,
        String title,
        String content,
        String emotionTag,
        String hermesSummary,
        String hermesReply,
        String mood,
        boolean favorite,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
    public static TreeholeResponse from(TreeholeEntry entry) {
        return new TreeholeResponse(
                entry.getId(),
                entry.getTitle(),
                entry.getContent(),
                entry.getEmotionTag(),
                entry.getHermesSummary(),
                entry.getHermesReply(),
                entry.getMood(),
                entry.isFavorite(),
                entry.isArchived(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
