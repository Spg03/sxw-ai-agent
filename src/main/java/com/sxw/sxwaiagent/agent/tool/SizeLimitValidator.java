package com.sxw.sxwaiagent.agent.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 参数大小限制校验器
 * <p>
 * 防止超长参数导致 LLM 上下文溢出或内存压力。
 */
@Component
@Order(1)
public class SizeLimitValidator implements ToolValidator {

    @Value("${sxw.tool.validation.max-argument-chars:8192}")
    private int maxArgumentChars;

    @Value("${sxw.tool.validation.enabled:true}")
    private boolean validationEnabled;

    @Override
    public ValidationResult validate(String toolName, String arguments, ToolDefinition toolDef) {
        if (!validationEnabled) {
            return ValidationResult.ok();
        }

        if (arguments != null && arguments.length() > maxArgumentChars) {
            return ValidationResult.reject(
                String.format("参数长度 %d 超过上限 %d 字符，请精简输入", arguments.length(), maxArgumentChars)
            );
        }

        return ValidationResult.ok();
    }
}
