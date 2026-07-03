package com.sxw.sxwaiagent.agent.tool;

/**
 * 工具执行结果
 *
 * @param content 结果内容
 * @param success 是否成功
 */
public record ToolResult(
        String content,
        boolean success
) {
    
    /**
     * 创建成功结果
     */
    public static ToolResult success(String content) {
        return new ToolResult(content, true);
    }
    
    /**
     * 创建失败结果
     */
    public static ToolResult failure(String content) {
        return new ToolResult(content, false);
    }
}
