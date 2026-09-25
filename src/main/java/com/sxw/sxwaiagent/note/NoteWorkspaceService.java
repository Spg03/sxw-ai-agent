package com.sxw.sxwaiagent.note;

import com.sxw.sxwaiagent.note.dto.NoteResponse;
import com.sxw.sxwaiagent.note.dto.SaveNoteRequest;
import com.sxw.sxwaiagent.note.model.NoteEntry;
import com.sxw.sxwaiagent.note.repository.NoteEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class NoteWorkspaceService {

    private final NoteEntryRepository repository;

    public NoteWorkspaceService(NoteEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NoteResponse save(Long userId, SaveNoteRequest request) {
        String title = request.title().trim();
        String content = request.content().trim();
        String tags = normalizeTags(request.tags());
        NoteEntry note;
        if (request.id() != null) {
            note = owned(userId, request.id());
            repository.findByUserIdAndTitle(userId, title)
                    .filter(other -> !other.getId().equals(note.getId()))
                    .ifPresent(other -> { throw new IllegalArgumentException("已存在同名笔记"); });
        } else {
            note = repository.findByUserIdAndTitle(userId, title)
                    .orElseGet(() -> NoteEntry.create(userId, title, content, tags));
        }
        note.update(title, content, tags, request.favorite());
        return NoteResponse.from(repository.save(note));
    }

    @Transactional
    public NoteResponse append(Long userId, String title, String content) {
        NoteEntry note = repository.findByUserIdAndTitle(userId, title.trim())
                .orElseThrow(() -> new IllegalArgumentException("笔记不存在"));
        note.append(content.trim());
        return NoteResponse.from(repository.save(note));
    }

    @Transactional(readOnly = true)
    public NoteResponse get(Long userId, Long id) {
        return NoteResponse.from(owned(userId, id));
    }

    @Transactional(readOnly = true)
    public NoteResponse getByTitle(Long userId, String title) {
        return repository.findByUserIdAndTitle(userId, title.trim())
                .map(NoteResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("笔记不存在"));
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(Long userId, String query, Boolean favorite) {
        List<NoteEntry> notes;
        if (query != null && !query.isBlank()) {
            notes = repository.findByUserIdAndTitleContainingIgnoreCaseOrUserIdAndContentContainingIgnoreCaseOrderByUpdatedAtDesc(
                    userId, query.trim(), userId, query.trim());
        } else if (Boolean.TRUE.equals(favorite)) {
            notes = repository.findByUserIdAndFavoriteTrueOrderByUpdatedAtDesc(userId);
        } else {
            notes = repository.findByUserIdOrderByUpdatedAtDesc(userId);
        }
        return notes.stream().map(NoteResponse::from).toList();
    }

    @Transactional
    public NoteResponse favorite(Long userId, Long id, boolean favorite) {
        NoteEntry note = owned(userId, id);
        note.setFavorite(favorite);
        return NoteResponse.from(repository.save(note));
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> related(Long userId, Long id, int limit) {
        NoteResponse selected = get(userId, id);
        Set<String> selectedTags = selected.tags().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        List<NoteResponse> candidates = new ArrayList<>(list(userId, null, false));
        candidates.removeIf(note -> note.id().equals(id));
        candidates.sort(Comparator
                .comparingInt((NoteResponse note) -> overlap(note.tags(), selectedTags)).reversed()
                .thenComparing(NoteResponse::updatedAt, Comparator.reverseOrder()));
        return candidates.stream().limit(Math.max(1, Math.min(limit, 10))).toList();
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(owned(userId, id));
    }

    private NoteEntry owned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("笔记不存在"));
    }

    private static String normalizeTags(List<String> input) {
        if (input == null || input.isEmpty()) return "";
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        input.stream().filter(java.util.Objects::nonNull).map(String::trim).filter(value -> !value.isBlank())
                .limit(12).forEach(tags::add);
        return String.join(",", tags);
    }

    private static int overlap(List<String> tags, Set<String> selected) {
        return (int) tags.stream().map(value -> value.toLowerCase(Locale.ROOT)).filter(selected::contains).count();
    }
}
