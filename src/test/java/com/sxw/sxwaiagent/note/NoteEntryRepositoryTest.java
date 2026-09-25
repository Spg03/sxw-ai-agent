package com.sxw.sxwaiagent.note;

import com.sxw.sxwaiagent.note.model.NoteEntry;
import com.sxw.sxwaiagent.note.repository.NoteEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.flyway.enabled=false")
class NoteEntryRepositoryTest {

    @Autowired
    private NoteEntryRepository repository;

    @Test
    void allowsSameTitleForDifferentUsersWithoutCrossUserVisibility() {
        repository.saveAndFlush(NoteEntry.create(11L, "weekly", "user 11", "work"));
        repository.saveAndFlush(NoteEntry.create(22L, "weekly", "user 22", "private"));

        assertThat(repository.findByUserIdOrderByUpdatedAtDesc(11L))
                .extracting(NoteEntry::getContent)
                .containsExactly("user 11");
        assertThat(repository.findByUserIdOrderByUpdatedAtDesc(22L))
                .extracting(NoteEntry::getContent)
                .containsExactly("user 22");
        assertThat(repository.countByUserId(11L)).isEqualTo(1);
        assertThat(repository.findByIdAndUserId(
                repository.findByUserIdAndTitle(11L, "weekly").orElseThrow().getId(), 22L)).isEmpty();
    }
}
