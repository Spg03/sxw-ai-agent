package com.sxw.sxwaiagent.infrastructure.tools;

import com.sxw.sxwaiagent.common.constant.FileConstant;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 工具沙箱公共能力：路径隔离、文件名校验、URL 校验、输出截断。
 */
final class ToolSandboxSupport {

    private static final Pattern SAFE_NAME =
            Pattern.compile("^[\\w\\-. \\u4e00-\\u9fa5]{1,128}$");

    private final Path baseDir;

    ToolSandboxSupport(String subDir) {
        this.baseDir = Path.of(FileConstant.FILE_SAVE_DIR, subDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to init sandbox directory: " + baseDir, e);
        }
    }

    Path resolveFile(String fileName) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("invalid file name: " + fileName);
        }
        Path target = baseDir.resolve(fileName).toAbsolutePath().normalize();
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("path traversal blocked: " + fileName);
        }
        return target;
    }

    Path baseDir() {
        return baseDir;
    }

    static java.net.URI validateHttpUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty url");
        }
        java.net.URI uri;
        try {
            uri = java.net.URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid url: " + rawUrl, e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("unsupported url scheme: " + scheme);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("url host is required");
        }
        return uri;
    }

    static String limitOutput(String output, int maxChars) {
        String safe = output == null ? "" : output;
        int cap = Math.max(256, maxChars);
        if (safe.length() <= cap) {
            return safe;
        }
        return safe.substring(0, cap) + "\n...[truncated, output exceeded " + cap + " chars]";
    }

    static long utf8Length(String text) {
        return (text == null ? "" : text).getBytes(StandardCharsets.UTF_8).length;
    }
}
