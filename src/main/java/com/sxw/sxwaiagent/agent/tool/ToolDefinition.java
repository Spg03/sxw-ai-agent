package com.sxw.sxwaiagent.agent.tool;

import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.profile.ToolRiskLevel;

import java.util.List;

/**
 * 工具定义
 * <p>
 * 每个工具必须定义 ToolDefinition，包含名称、描述、风险等级等信息。
 * 用于 ToolRegistry 注册和 ToolRiskEvaluator 风险评估。
 *
 * @param name              工具名称（唯一标识，与 Spring AI ToolCallback 名称一致）
 * @param description       工具描述
 * @param riskLevel         风险等级
 * @param readOnly          是否只读
 * @param destructive       是否有破坏性
 * @param concurrencySafe   是否并发安全
 * @param requiresApproval  是否需要审批
 * @param enabledProfiles   启用的 Profile 列表（空表示所有 Profile 可用）
 */
public record ToolDefinition(
        String name,
        String description,
        ToolRiskLevel riskLevel,
        boolean readOnly,
        boolean destructive,
        boolean concurrencySafe,
        boolean requiresApproval,
        List<AgentProfileCode> enabledProfiles
) {

    /**
     * 快速创建只读工具定义
     */
    public static ToolDefinition readOnly(String name, String description) {
        return new ToolDefinition(
                name, description,
                ToolRiskLevel.READ_ONLY,
                true, false, true, false,
                List.of()
        );
    }

    /**
     * 快速创建本地写工具定义
     */
    public static ToolDefinition localWrite(String name, String description) {
        return new ToolDefinition(
                name, description,
                ToolRiskLevel.LOCAL_WRITE,
                false, false, true, false,
                List.of()
        );
    }

    /**
     * 快速创建外部写工具定义
     */
    public static ToolDefinition externalWrite(String name, String description) {
        return new ToolDefinition(
                name, description,
                ToolRiskLevel.EXTERNAL_WRITE,
                false, false, false, true,
                List.of()
        );
    }

    /**
     * 快速创建破坏性工具定义（默认关闭）
     */
    public static ToolDefinition destructive(String name, String description) {
        return new ToolDefinition(
                name, description,
                ToolRiskLevel.DESTRUCTIVE,
                false, true, false, true,
                List.of()
        );
    }

    /**
     * 快速创建 Shell 工具定义（默认关闭）
     */
    public static ToolDefinition shell(String name, String description) {
        return new ToolDefinition(
                name, description,
                ToolRiskLevel.SHELL,
                false, true, false, true,
                List.of()
        );
    }

    /**
     * 检查工具是否对指定 Profile 启用
     */
    public boolean isEnabledFor(AgentProfileCode profileCode) {
        if (enabledProfiles == null || enabledProfiles.isEmpty()) {
            return true;
        }
        return enabledProfiles.contains(profileCode);
    }

    /**
     * 检查风险等级是否在允许范围内
     */
    public boolean isWithinRiskLimit(ToolRiskLevel maxAllowed) {
        return this.riskLevel.ordinal() <= maxAllowed.ordinal();
    }
}
