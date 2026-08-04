package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.infrastructure.skill.Skill;
import com.sxw.sxwaiagent.infrastructure.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SkillController Web 层契约测试。
 */
@ExtendWith(MockitoExtension.class)
class SkillControllerTest {

    @Mock
    private SkillRegistry skillRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SkillController controller = new SkillController();
        // SkillController 使用 @Resource 注入，通过反射设置
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "skillRegistry", skillRegistry);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/skills - 返回技能列表")
    void list_returnsAllSkills() throws Exception {
        when(skillRegistry.all()).thenReturn(List.of(
                new Skill("note", "笔记管理技能", "# Note Skill\n..."),
                new Skill("web-search", "网络搜索技能", "# Web Search\n...")
        ));

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("note"))
                .andExpect(jsonPath("$[0].description").value("笔记管理技能"))
                .andExpect(jsonPath("$[1].name").value("web-search"));
    }

    @Test
    @DisplayName("GET /api/skills - 空列表")
    void list_empty() throws Exception {
        when(skillRegistry.all()).thenReturn(List.of());

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/skills/{name} - 返回技能详情")
    void get_found() throws Exception {
        when(skillRegistry.findByName("note"))
                .thenReturn(Optional.of(new Skill("note", "笔记管理技能", "# Note Skill\n完整正文")));

        mockMvc.perform(get("/api/skills/note"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("note"))
                .andExpect(jsonPath("$.description").value("笔记管理技能"))
                .andExpect(jsonPath("$.body").value("# Note Skill\n完整正文"));
    }

    @Test
    @DisplayName("GET /api/skills/{name} - 技能不存在返回 404")
    void get_notFound() throws Exception {
        when(skillRegistry.findByName("nonexist")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/skills/nonexist"))
                .andExpect(status().isNotFound());
    }
}
