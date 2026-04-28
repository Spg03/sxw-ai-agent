package com.sxw.sxwaiagent.infrastructure.skill;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 笔记技能（Markdown Notes Skill）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>所有读写被限制在 {@link NoteSkillProperties#getBaseDir()} 目录内，防止路径穿越。</li>
 *   <li>文件名仅允许安全字符，默认扩展名 .md。</li>
 *   <li>单文件大小上限可配置，避免恶意大文件。</li>
 *   <li>方法以 {@code @Tool} 注解，可同时被 Spring AI Agent 与 MCP 服务端暴露。</li>
 * </ul>
 */
public class NoteSkill {

    private static final java.util.regex.Pattern SAFE_NAME =
            java.util.regex.Pattern.compile("^[\\w\\-. \\u4e00-\\u9fa5]{1,64}$");

    private final NoteSkillProperties properties;
    private final Path baseDir;

    public NoteSkill(NoteSkillProperties properties) {
        this.properties = properties;
        this.baseDir = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("初始化笔记目录失败: " + baseDir, e);
        }
    }

    @Tool(description = "Create or overwrite a markdown note with the given title and content")
    public String createNote(
            @ToolParam(description = "Note title, also used as file name (without extension), allowed chars: letters/digits/中文/._- and space, length 1-64") String title,
            @ToolParam(description = "Markdown content to write") String content) {
        Path target = resolveNotePath(title);
        byte[] bytes = safeContent(content).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > properties.getMaxFileSizeBytes()) {
            return "failed: content exceeds max size " + properties.getMaxFileSizeBytes() + " bytes";
        }
        try {
            Files.write(target, bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return "ok: note saved at " + target;
        } catch (IOException e) {
            return "failed: " + e.getMessage();
        }
    }

    @Tool(description = "Append content to an existing markdown note; create it if missing")
    public String appendNote(
            @ToolParam(description = "Note title (file name without extension)") String title,
            @ToolParam(description = "Markdown content to append") String content) {
        Path target = resolveNotePath(title);
        try {
            long existing = Files.exists(target) ? Files.size(target) : 0L;
            byte[] bytes = ("\n" + safeContent(content)).getBytes(StandardCharsets.UTF_8);
            if (existing + bytes.length > properties.getMaxFileSizeBytes()) {
                return "failed: file would exceed max size " + properties.getMaxFileSizeBytes() + " bytes";
            }
            Files.write(target, bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "ok: appended to " + target;
        } catch (IOException e) {
            return "failed: " + e.getMessage();
        }
    }

    @Tool(description = "Read the full content of a markdown note by title")
    public String readNote(@ToolParam(description = "Note title (file name without extension)") String title) {
        Path target = resolveNotePath(title);
        if (!Files.exists(target)) {
            return "failed: note not found: " + title;
        }
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "failed: " + e.getMessage();
        }
    }

    @Tool(description = "List all markdown notes (titles only) stored by the agent")
    public String listNotes() {
        try (Stream<Path> stream = Files.list(baseDir)) {
            List<String> titles = stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".md"))
                    .map(n -> n.substring(0, n.length() - 3))
                    .sorted()
                    .collect(Collectors.toList());
            if (titles.isEmpty()) {
                return "ok: no notes";
            }
            return "ok:\n" + String.join("\n", titles);
        } catch (IOException e) {
            return "failed: " + e.getMessage();
        }
    }

    @Tool(description = "Search notes by a case-insensitive keyword in title or body; returns matched titles and snippets")
    public String searchNotes(
            @ToolParam(description = "Keyword to search, case-insensitive, length 1-64") String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.length() > 64) {
            return "failed: invalid keyword";
        }
        String kw = keyword.toLowerCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder();
        try (Stream<Path> stream = Files.list(baseDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        String title = name.substring(0, name.length() - 3);
                        try {
                            String body = Files.readString(p, StandardCharsets.UTF_8);
                            String bodyLc = body.toLowerCase(java.util.Locale.ROOT);
                            int idx = bodyLc.indexOf(kw);
                            boolean titleHit = title.toLowerCase(java.util.Locale.ROOT).contains(kw);
                            if (idx >= 0 || titleHit) {
                                out.append("- ").append(title);
                                if (idx >= 0) {
                                    int from = Math.max(0, idx - 20);
                                    int to = Math.min(body.length(), idx + kw.length() + 20);
                                    out.append("  ::  ")
                                            .append(body.substring(from, to).replaceAll("\\s+", " "));
                                }
                                out.append('\n');
                            }
                        } catch (IOException ignored) {
                            // 单文件读取失败不影响整体
                        }
                    });
        } catch (IOException e) {
            return "failed: " + e.getMessage();
        }
        return out.length() == 0 ? "ok: no matches" : "ok:\n" + out;
    }

    @Tool(description = "Delete a markdown note by title")
    public String deleteNote(@ToolParam(description = "Note title (file name without extension)") String title) {
        Path target = resolveNotePath(title);
        try {
            boolean removed = Files.deleteIfExists(target);
            return removed ? "ok: deleted " + title : "ok: note not found";
        } catch (IOException e) {
            return "failed: " + e.getMessage();
        }
    }

    // ---------- internals ----------

    private Path resolveNotePath(String title) {
        if (title == null || !SAFE_NAME.matcher(title).matches()) {
            throw new IllegalArgumentException("invalid note title: " + title);
        }
        Path target = baseDir.resolve(title + ".md").toAbsolutePath().normalize();
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("path traversal blocked: " + title);
        }
        return target;
    }

    private static String safeContent(String content) {
        return content == null ? "" : content;
    }
}
