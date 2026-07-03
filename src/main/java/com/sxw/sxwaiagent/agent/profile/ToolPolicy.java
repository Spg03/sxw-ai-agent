package com.sxw.sxwaiagent.agent.profile;

/**
 * 工具策略
 *
 * @param maxRiskLevel    允许的最高风险等级
 * @param requiresApproval 是否需要审批
 * @param auditEnabled    是否启用审计日志
 */
public record ToolPolicy(
        ToolRiskLevel maxRiskLevel,
        boolean requiresApproval,
        boolean auditEnabled
) {
    
    /**
     * 只读策略（最高风险等级为 READ_ONLY）
     */
    public static ToolPolicy readOnly() {
        return new ToolPolicy(ToolRiskLevel.READ_ONLY, false, true);
    }
    
    /**
     * 本地写策略（最高风险等级为 LOCAL_WRITE）
     */
    public static ToolPolicy localWrite() {
        return new ToolPolicy(ToolRiskLevel.LOCAL_WRITE, false, true);
    }
    
    /**
     * 严格策略（需要审批）
     */
    public static ToolPolicy strict() {
        return new ToolPolicy(ToolRiskLevel.LOCAL_WRITE, true, true);
    }
}
