package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.auth.AuthenticatedUser;
import com.sxw.sxwaiagent.common.api.GlobalExceptionHandler;
import com.sxw.sxwaiagent.note.NoteWorkspaceService;
import com.sxw.sxwaiagent.note.dto.NoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NoteControllerTest {

    private MockMvc mockMvc;
    private NoteWorkspaceService service;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        service = mock(NoteWorkspaceService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NoteController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        AuthenticatedUser user = new AuthenticatedUser(42L, "tester", "Tester", "USER");
        authentication = new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities());
    }

    @Test
    void saveListFavoriteAndDeleteAreScopedToAuthenticatedUser() throws Exception {
        NoteResponse note = note(7L, "hello", false);
        when(service.save(eq(42L), any())).thenReturn(note);
        when(service.list(42L, null, null)).thenReturn(List.of(note));
        when(service.favorite(42L, 7L, true)).thenReturn(note(7L, "hello", true));

        mockMvc.perform(post("/api/notes").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hello\",\"content\":\"# hi\",\"tags\":[\"work\"],\"favorite\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.title").value("hello"));

        mockMvc.perform(get("/api/notes/list").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("hello"));

        mockMvc.perform(patch("/api/notes/7/favorite").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorite").value(true));

        mockMvc.perform(delete("/api/notes/7").principal(authentication))
                .andExpect(status().isOk());
        verify(service).delete(42L, 7L);
    }

    @Test
    void validatesOversizedTitle() throws Exception {
        String title = "x".repeat(121);
        mockMvc.perform(post("/api/notes").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"x\",\"tags\":[]}"))
                .andExpect(status().isBadRequest());
    }

    private static NoteResponse note(Long id, String title, boolean favorite) {
        Instant now = Instant.parse("2026-08-28T00:00:00Z");
        return new NoteResponse(id, title, "# hi", List.of("work"), favorite, 0L, now, now);
    }
}
