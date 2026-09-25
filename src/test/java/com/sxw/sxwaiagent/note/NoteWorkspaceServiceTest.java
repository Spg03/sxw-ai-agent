package com.sxw.sxwaiagent.note;

import com.sxw.sxwaiagent.note.dto.SaveNoteRequest;
import com.sxw.sxwaiagent.note.model.NoteEntry;
import com.sxw.sxwaiagent.note.repository.NoteEntryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NoteWorkspaceServiceTest {

    @Test
    void createsUserScopedStructuredNote() {
        NoteEntryRepository repository = mock(NoteEntryRepository.class);
        NoteWorkspaceService service = new NoteWorkspaceService(repository);
        when(repository.findByUserIdAndTitle(42L, "Architecture")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            NoteEntry note = invocation.getArgument(0);
            note.setId(9L);
            return note;
        });

        var response = service.save(42L, new SaveNoteRequest(null, " Architecture ", " content ", List.of("work", " work "), true));

        assertEquals(9L, response.id());
        assertEquals("Architecture", response.title());
        assertEquals(List.of("work"), response.tags());
    }

    @Test
    void rejectsNoteOwnedByAnotherUser() {
        NoteEntryRepository repository = mock(NoteEntryRepository.class);
        NoteWorkspaceService service = new NoteWorkspaceService(repository);
        when(repository.findByIdAndUserId(7L, 42L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.get(42L, 7L));
    }
}
