package com.sxw.sxwaiagent.infrastructure.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteSkillTest {

    private NoteSkill skill;
    private NoteSkillProperties properties;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        properties = new NoteSkillProperties();
        properties.setBaseDir(tempDir.toString());
        properties.setMaxFileSizeBytes(1024);
        skill = new NoteSkill(properties);
    }

    @Test
    void createAndReadNote() {
        String create = skill.createNote("hello", "# Hi\nworld");
        assertTrue(create.startsWith("ok"));
        String read = skill.readNote("hello");
        assertEquals("# Hi\nworld", read);
    }

    @Test
    void appendNote() {
        skill.createNote("diary", "day1");
        String append = skill.appendNote("diary", "day2");
        assertTrue(append.startsWith("ok"));
        String content = skill.readNote("diary");
        assertTrue(content.contains("day1"));
        assertTrue(content.contains("day2"));
    }

    @Test
    void listAndDelete() {
        skill.createNote("a", "x");
        skill.createNote("b", "y");
        String list = skill.listNotes();
        assertTrue(list.contains("a"));
        assertTrue(list.contains("b"));

        String del = skill.deleteNote("a");
        assertTrue(del.contains("deleted"));
        assertFalse(skill.listNotes().contains("\na\n"));
    }

    @Test
    void rejectPathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> skill.createNote("../evil", "x"));
        assertThrows(IllegalArgumentException.class, () -> skill.createNote("/etc/passwd", "x"));
        assertThrows(IllegalArgumentException.class, () -> skill.readNote(""));
    }

    @Test
    void rejectOversizedContent() {
        String big = "x".repeat(2048);
        String result = skill.createNote("big", big);
        assertTrue(result.startsWith("failed"));
    }

    @Test
    void searchByTitleAndBody() {
        skill.createNote("springAi", "MCP and tool calling rocks");
        skill.createNote("misc", "an unrelated note about gardening");
        skill.createNote("rag", "vector store with PgVector");

        String byBody = skill.searchNotes("mcp");
        assertTrue(byBody.contains("springAi"), byBody);
        assertFalse(byBody.contains("misc"), byBody);

        String byTitle = skill.searchNotes("RAG");
        assertTrue(byTitle.contains("rag"), byTitle);

        assertTrue(skill.searchNotes("nonexistent-keyword").contains("no matches"));
        assertTrue(skill.searchNotes("").startsWith("failed"));
    }
}
