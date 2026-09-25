package com.sxw.sxwaiagent.treehole.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record TreeholeInsightsResponse(
        int total,
        int active,
        Map<String, Long> moodCounts,
        List<MoodDay> trend,
        Map<LocalDate, String> calendar
) {
    public record MoodDay(LocalDate date, String mood, int score, long count) {}
}
