package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.common.api.GlobalExceptionHandler;
import com.sxw.sxwaiagent.infrastructure.skill.NoteSkill;
import com.sxw.sxwaiagent.infrastructure.skill.NoteSkillProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NoteController Web 层契约测试（不启动完整 Spring 上下文）。
 */
class NoteControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        NoteSkillProperties props = new NoteSkillProperties();
        props.setBaseDir(tempDir.toString());
        props.setMaxFileSizeBytes(4096);
        NoteSkill skill = new NoteSkill(props);
        NoteController controller = new NoteController(skill);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReadListAndDelete() throws Exception {
        // create
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hello\",\"content\":\"# hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.startsWith("ok:")));

        // read
        mockMvc.perform(get("/notes").param("title", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("# hi"));

        // list
        mockMvc.perform(get("/notes/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("hello")));

        // delete
        mockMvc.perform(delete("/notes").param("title", "hello"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsPathTraversalWith400() throws Exception {
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"../evil\",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
