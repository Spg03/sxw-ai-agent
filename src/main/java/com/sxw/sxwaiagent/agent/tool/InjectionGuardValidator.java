package com.sxw.sxwaiagent.agent.tool;

import com.sxw.sxwaiagent.agent.profile.ToolRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 注入攻击防护校验器
 * <p>
 * 检测参数中的命令注入、路径穿越、内网探测等恶意模式。
 * 仅对高风险工具（DESTRUCTIVE / SHELL / EXTERNAL_WRITE）生效，避免误伤普通读写。
 */
@Component
@Order(2)
public class InjectionGuardValidator implements ToolValidator {

    private static final Logger log = LoggerFactory.getLogger(InjectionGuardValidator.class);

    /** 命令注入模式：$(...)、`...`、; rm、&& 等 */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("\\$\\("),           // $(command)
        Pattern.compile("`[^`]+`"),          // `command`
        Pattern.compile(";\\s*(rm|del|format|shutdown|reboot)"),  // ; rm / del
        Pattern.compile("&&\\s*(rm|del|curl|wget)"),              // && dangerous
        Pattern.compile("\\|\\s*(bash|sh|cmd|powershell)"),       // | bash
        Pattern.compile("\\.\\./\\.\\./"),   // ../../ 路径穿越
        Pattern.compile("(127\\.0\\.0\\.1|10\\.\\d+\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+|172\\.(1[6-9]|2\\d|3[01])\\.\\d+\\.\\d+)")  // 内网 IP
    );

    @Value("${sxw.tool.validation.enabled:true}")
    private boolean validationEnabled;

    @Override
    public ValidationResult validate(String toolName, String arguments, ToolDefinition toolDef) {
        if (!validationEnabled) {
            return ValidationResult.ok();
        }

        // 仅对高风险工具生效
        if (toolDef == null || toolDef.riskLevel().ordinal() < ToolRiskLevel.EXTERNAL_WRITE.ordinal()) {
            return ValidationResult.ok();
        }

        if (arguments == null || arguments.isEmpty()) {
            return ValidationResult.ok();
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(arguments).find()) {
                log.warn("Injection guard rejected tool '{}': pattern '{}' matched in arguments",
                    toolName, pattern.pattern());
                return ValidationResult.reject(
                    "参数包含潜在注入攻击模式，已被安全策略拦截（工具: " + toolName + "）"
                );
            }
        }

        return ValidationResult.ok();
    }
}
