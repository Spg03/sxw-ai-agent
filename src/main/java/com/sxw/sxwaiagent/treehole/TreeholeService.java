package com.sxw.sxwaiagent.treehole;

import com.sxw.sxwaiagent.agent.hermes.HermesAgent;
import com.sxw.sxwaiagent.agent.hermes.HermesReply;
import com.sxw.sxwaiagent.treehole.dto.CreateTreeholeRequest;
import com.sxw.sxwaiagent.treehole.dto.TreeholeResponse;
import com.sxw.sxwaiagent.treehole.dto.TreeholeInsightsResponse;
import com.sxw.sxwaiagent.treehole.model.TreeholeEntry;
import com.sxw.sxwaiagent.treehole.repository.TreeholeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TreeholeService {

    private final TreeholeEntryRepository treeholeEntryRepository;
    private final HermesAgent hermesAgent;

    public TreeholeService(TreeholeEntryRepository treeholeEntryRepository, HermesAgent hermesAgent) {
        this.treeholeEntryRepository = treeholeEntryRepository;
        this.hermesAgent = hermesAgent;
    }

    @Transactional
    public TreeholeResponse create(Long userId, CreateTreeholeRequest request) {
        HermesReply reply = hermesAgent.comfort(request.content(), "treehole-" + userId);
        TreeholeEntry entry = TreeholeEntry.create(
                userId,
                request.title().trim(),
                request.content().trim(),
                emptyToDefault(reply.emotionTag(), "unknown"),
                emptyToDefault(reply.summary(), request.content().trim()),
                emptyToDefault(reply.reply(), ""),
                request.mood()
        );
        return TreeholeResponse.from(treeholeEntryRepository.save(entry));
    }

    @Transactional(readOnly = true)
    public List<TreeholeResponse> list(Long userId, boolean archived) {
        return treeholeEntryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(entry -> entry.isArchived() == archived)
                .map(TreeholeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TreeholeResponse> list(Long userId) {
        return list(userId, false);
    }

    @Transactional(readOnly = true)
    public TreeholeResponse get(Long userId, Long entryId) {
        TreeholeEntry entry = treeholeEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("treehole not found"));
        if (!entry.getUserId().equals(userId)) {
            throw new IllegalArgumentException("treehole not found");
        }
        return TreeholeResponse.from(entry);
    }

    @Transactional
    public void delete(Long userId, Long entryId) {
        TreeholeEntry entry = owned(userId, entryId);
        treeholeEntryRepository.delete(entry);
    }

    @Transactional
    public TreeholeResponse archive(Long userId, Long entryId, boolean archived) {
        TreeholeEntry entry = owned(userId, entryId);
        entry.setArchived(archived);
        return TreeholeResponse.from(treeholeEntryRepository.save(entry));
    }

    @Transactional
    public TreeholeResponse favorite(Long userId, Long entryId, boolean favorite) {
        TreeholeEntry entry = owned(userId, entryId);
        entry.setFavorite(favorite);
        return TreeholeResponse.from(treeholeEntryRepository.save(entry));
    }

    @Transactional(readOnly = true)
    public TreeholeInsightsResponse insights(Long userId, int days) {
        int safeDays = Math.max(7, Math.min(days, 90));
        ZoneId zone = ZoneId.systemDefault();
        LocalDate start = LocalDate.now(zone).minusDays(safeDays - 1L);
        List<TreeholeEntry> all = treeholeEntryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<TreeholeEntry> active = all.stream().filter(entry -> !entry.isArchived()).toList();
        Map<String, Long> moodCounts = active.stream().collect(Collectors.groupingBy(
                TreeholeEntry::getMood, LinkedHashMap::new, Collectors.counting()));
        Map<LocalDate, List<TreeholeEntry>> byDay = active.stream()
                .filter(entry -> !entry.getCreatedAt().atZone(zone).toLocalDate().isBefore(start))
                .collect(Collectors.groupingBy(entry -> entry.getCreatedAt().atZone(zone).toLocalDate()));
        List<TreeholeInsightsResponse.MoodDay> trend = start.datesUntil(LocalDate.now(zone).plusDays(1))
                .map(date -> {
                    List<TreeholeEntry> dayEntries = byDay.getOrDefault(date, List.of());
                    String mood = dayEntries.stream().max(Comparator.comparing(TreeholeEntry::getCreatedAt))
                            .map(TreeholeEntry::getMood).orElse("");
                    int score = dayEntries.isEmpty() ? 0 : (int) Math.round(dayEntries.stream()
                            .mapToInt(entry -> moodScore(entry.getMood())).average().orElse(0));
                    return new TreeholeInsightsResponse.MoodDay(date, mood, score, dayEntries.size());
                }).toList();
        Map<LocalDate, String> calendar = new LinkedHashMap<>();
        byDay.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> calendar.put(
                entry.getKey(),
                entry.getValue().stream().max(Comparator.comparing(TreeholeEntry::getCreatedAt))
                        .map(TreeholeEntry::getMood).orElse("")
        ));
        return new TreeholeInsightsResponse(all.size(), active.size(), moodCounts, trend, calendar);
    }

    public List<String> prompts(LocalDate date) {
        List<String> prompts = List.of(
                "今天，我想记录一件小事……",
                "最近让我感到温暖的是……",
                "如果可以对未来的自己说一句话……",
                "此刻我最想放下的一件事是……",
                "今天值得感谢的三个瞬间是……",
                "我正在慢慢学会……"
        );
        int offset = Math.floorMod(date.getDayOfYear(), prompts.size());
        return List.of(prompts.get(offset), prompts.get((offset + 2) % prompts.size()), prompts.get((offset + 4) % prompts.size()));
    }

    private TreeholeEntry owned(Long userId, Long entryId) {
        TreeholeEntry entry = treeholeEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("treehole not found"));
        if (!entry.getUserId().equals(userId)) throw new IllegalArgumentException("treehole not found");
        return entry;
    }

    private static int moodScore(String mood) {
        return switch (mood) {
            case "开心" -> 5;
            case "平静" -> 4;
            case "疲惫" -> 3;
            case "焦虑" -> 2;
            case "难过" -> 1;
            default -> 3;
        };
    }

    private static String emptyToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
