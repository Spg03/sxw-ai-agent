package com.sxw.sxwaiagent.note.repository;

import com.sxw.sxwaiagent.note.model.NoteEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteEntryRepository extends JpaRepository<NoteEntry, Long> {

    long countByUserId(Long userId);

    Optional<NoteEntry> findByIdAndUserId(Long id, Long userId);

    Optional<NoteEntry> findByUserIdAndTitle(Long userId, String title);

    List<NoteEntry> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<NoteEntry> findByUserIdAndFavoriteTrueOrderByUpdatedAtDesc(Long userId);

    List<NoteEntry> findByUserIdAndTitleContainingIgnoreCaseOrUserIdAndContentContainingIgnoreCaseOrderByUpdatedAtDesc(
            Long titleUserId, String title, Long contentUserId, String content);
}
