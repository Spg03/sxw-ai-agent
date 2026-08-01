package com.sxw.sxwaiagent.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 基本参数格式校验器
 * <p>
 * 校验参数是否为合法 JSON（非空、非 null 字面量）。
 * 作为最基础的守门校验，排在第一位。
 */
@Component
@Order(0)
public class SchemaValidator implements ToolValidator {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class);

    @Value("${sxw.tool.validation.enabled:true}")
    private boolean validationEnabled;

    @Override
    public ValidationResult validate(String toolName, String arguments, ToolDefinition toolDef) {
        if (!validationEnabled) {
            return ValidationResult.ok();
        }

        // 允许空参数（部分工具无需参数）
        if (arguments == null || arguments.isBlank()) {
            return ValidationResult.ok();
        }

        String trimmed = arguments.trim();

        // 基本 JSON 格式检查：必须以 { 或 [ 开头
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            log.warn("Schema validation failed for tool '{}': arguments is not valid JSON", toolName);
            return ValidationResult.reject("参数格式错误：期望 JSON 格式，实际收到非结构化文本");
        }

        // 简单括号匹配检查
        if (trimmed.startsWith("{") && !trimmed.endsWith("}")) {
            return ValidationResult.reject("参数格式错误：JSON 对象未正确闭合");
        }
        if (trimmed.startsWith("[") && !trimmed.endsWith("]")) {
            return ValidationResult.reject("参数格式错误：JSON 数组未正确闭合");
        }

        return ValidationResult.ok();
    }
}
