package com.sxw.sxwaiagent.agent.tool;

/**
 * 工具参数校验器
 * <p>
 * 在工具执行前对参数进行校验，校验失败则拒绝执行并记录审计。
 * 多个 Validator 按 Spring 注入顺序依次执行，任一失败即中止。
 */
public interface ToolValidator {

    /**
     * 校验工具调用参数
     *
     * @param toolName  工具名称
     * @param arguments 工具参数（JSON 字符串）
     * @param toolDef   工具定义（可能为 null，表示未注册工具）
     * @return 校验结果
     */
    ValidationResult validate(String toolName, String arguments, ToolDefinition toolDef);

    /**
     * 校验结果
     */
    record ValidationResult(boolean valid, String rejectReason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
