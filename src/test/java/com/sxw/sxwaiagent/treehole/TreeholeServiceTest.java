package com.sxw.sxwaiagent.treehole;

import com.sxw.sxwaiagent.agent.hermes.HermesAgent;
import com.sxw.sxwaiagent.agent.hermes.HermesReply;
import com.sxw.sxwaiagent.treehole.dto.CreateTreeholeRequest;
import com.sxw.sxwaiagent.treehole.model.TreeholeEntry;
import com.sxw.sxwaiagent.treehole.repository.TreeholeEntryRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TreeholeServiceTest {

    @Test
    void createStoresHermesSummaryForCurrentUser() {
        TreeholeEntryRepository repository = mock(TreeholeEntryRepository.class);
        HermesAgent hermesAgent = mock(HermesAgent.class);
        TreeholeService service = new TreeholeService(repository, hermesAgent);
        HermesReply reply = new HermesReply("我在听。", "anxious", "你很在意这件事。", "先写下最担心的一点。");

        when(hermesAgent.comfort("今天很累", "treehole-42")).thenReturn(reply);
        when(repository.save(any(TreeholeEntry.class))).thenAnswer(invocation -> {
            TreeholeEntry entry = invocation.getArgument(0);
            entry.setId(100L);
            return entry;
        });

        var response = service.create(42L, new CreateTreeholeRequest("加班后的晚上", "今天很累"));

        assertEquals(100L, response.id());
        assertEquals("加班后的晚上", response.title());
        assertEquals("anxious", response.emotionTag());
        assertEquals("你很在意这件事。", response.hermesSummary());
    }

    @Test
    void getRejectsEntriesOwnedByOtherUsers() {
        TreeholeEntryRepository repository = mock(TreeholeEntryRepository.class);
        TreeholeService service = new TreeholeService(repository, mock(HermesAgent.class));
        TreeholeEntry entry = TreeholeEntry.create(7L, "title", "content", "calm", "summary", "reply");
        entry.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entry));

        assertThrows(IllegalArgumentException.class, () -> service.get(42L, 1L));
    }
}
