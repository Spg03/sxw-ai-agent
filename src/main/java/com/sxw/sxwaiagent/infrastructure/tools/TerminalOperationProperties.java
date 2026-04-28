package com.sxw.sxwaiagent.infrastructure.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 终端工具安全沙箱配置。
 * 通过 {@code sxw.tool.terminal.*} 配置，支持环境变量覆盖。
 */
@ConfigurationProperties(prefix = "sxw.tool.terminal")
public class TerminalOperationProperties {

    /** 是否启用终端工具，默认关闭（生产默认拒绝执行任意命令）。*/
    private boolean enabled = false;

    /** 单条命令执行超时时间（秒）。*/
    private int timeoutSeconds = 10;

    /** 最大返回字符数，防止超大输出打爆内存。*/
    private int maxOutputChars = 8 * 1024;

    /** 允许执行的命令前缀白名单（大小写不敏感，按首 token 匹配）。*/
    private List<String> allowedCommands = new ArrayList<>(List.of(
            "echo", "dir", "ls", "pwd", "whoami", "hostname", "date", "cd"
    ));

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxOutputChars() { return maxOutputChars; }
    public void setMaxOutputChars(int maxOutputChars) { this.maxOutputChars = maxOutputChars; }
    public List<String> getAllowedCommands() { return allowedCommands; }
    public void setAllowedCommands(List<String> allowedCommands) { this.allowedCommands = allowedCommands; }
}
