package com.sxw.sxwaiagent.agent.tool;

/**
 * 工具执行钩子
 * <p>
 * 在工具执行前后插入横切逻辑（指标采集、结果截断等）。
 * 多个 Hook 按 Spring 注入顺序执行。
 */
public interface ToolHook {

    /**
     * 工具执行前回调
     *
     * @param toolName  工具名称
     * @param arguments 工具参数
     * @param requestId 请求 ID
     */
    default void beforeExecution(String toolName, String arguments, String requestId) {
        // 默认空实现
    }

    /**
     * 工具执行后回调
     *
     * @param toolName  工具名称
     * @param result    执行结果（可能被修改/截断）
     * @param latencyMs 执行耗时
     * @param success   是否成功
     * @param requestId 请求 ID
     * @return 处理后的结果内容（可截断/修改），返回 null 表示不修改
     */
    default String afterExecution(String toolName, String result, long latencyMs, boolean success, String requestId) {
        return null;
    }
}
