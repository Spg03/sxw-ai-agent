package com.sxw.sxwaiagent.infrastructure.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 终端操作工具（安全沙箱版本）。
 * <p>
 * 加固点：
 * <ul>
 *   <li>默认关闭，必须显式通过 {@code sxw.tool.terminal.enabled=true} 才能启用；</li>
 *   <li>命令首 token 必须在白名单内，禁止 shell 元字符（{@code & | ; > < ` $}）；</li>
 *   <li>执行超时后强制销毁进程，防止僵尸进程阻塞线程；</li>
 *   <li>输出字符数超过阈值会被截断，防止 OOM；</li>
 *   <li>stderr 合并到 stdout 避免阻塞，审计日志记录每次调用。</li>
 * </ul>
 */
public class TerminalOperationTool {

    private static final Logger log = LoggerFactory.getLogger(TerminalOperationTool.class);
    private static final String FORBIDDEN_CHARS = "&|;`$><\n\r";

    private final TerminalOperationProperties properties;

    public TerminalOperationTool(TerminalOperationProperties properties) {
        this.properties = properties;
    }

    @Tool(description = "Execute a whitelisted command in the terminal within a sandbox (timeout + output cap)")
    public String executeTerminalCommand(
            @ToolParam(description = "Command to execute; only commands starting with a whitelisted token are allowed") String command) {
        if (!properties.isEnabled()) {
            return "refused: terminal tool is disabled by configuration (sxw.tool.terminal.enabled=false)";
        }
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return "refused: empty command";
        }
        for (int i = 0; i < FORBIDDEN_CHARS.length(); i++) {
            if (trimmed.indexOf(FORBIDDEN_CHARS.charAt(i)) >= 0) {
                log.warn("[terminal] blocked forbidden char in command: {}", trimmed);
                return "refused: forbidden shell metacharacter detected";
            }
        }
        String head = trimmed.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        boolean allowed = properties.getAllowedCommands().stream()
                .anyMatch(c -> c.equalsIgnoreCase(head));
        if (!allowed) {
            log.warn("[terminal] blocked non-whitelisted command head: {}", head);
            return "refused: command '" + head + "' is not in allow-list " + properties.getAllowedCommands();
        }

        ProcessBuilder builder = isWindows()
                ? new ProcessBuilder("cmd.exe", "/c", trimmed)
                : new ProcessBuilder("sh", "-c", trimmed);
        builder.redirectErrorStream(true);

        Process process = null;
        try {
            log.info("[terminal] executing: {}", trimmed);
            process = builder.start();
            StringBuilder output = new StringBuilder();
            int cap = Math.max(1024, properties.getMaxOutputChars());
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() + line.length() + 1 > cap) {
                        output.append(line, 0, Math.max(0, cap - output.length()));
                        output.append("\n...[truncated, output exceeded ").append(cap).append(" chars]");
                        break;
                    }
                    output.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[terminal] timeout after {}s: {}", properties.getTimeoutSeconds(), trimmed);
                return "failed: command timed out after " + properties.getTimeoutSeconds() + "s";
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output.append("\nCommand execution failed with exit code: ").append(exitCode);
            }
            return output.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return "failed: interrupted - " + e.getMessage();
        } catch (IOException e) {
            return "failed: " + e.getMessage();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
