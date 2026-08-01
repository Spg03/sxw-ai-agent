package com.sxw.sxwaiagent.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 工具结果截断钩子
 * <p>
 * 防止超长工具结果撑爆 LLM 上下文窗口。
 * 超过阈值时截断并附加提示信息。
 */
@Component
@Order(1)
public class ResultTruncationHook implements ToolHook {

    private static final Logger log = LoggerFactory.getLogger(ResultTruncationHook.class);

    @Value("${sxw.tool.hook.max-result-chars:12000}")
    private int maxResultChars;

    @Override
    public String afterExecution(String toolName, String result, long latencyMs, boolean success, String requestId) {
        if (result == null || result.length() <= maxResultChars) {
            return null; // 不修改
        }

        log.info("[{}] Tool {} result truncated: {} -> {} chars", requestId, toolName, result.length(), maxResultChars);

        return result.substring(0, maxResultChars)
            + "\n\n... [结果已截断，原始长度 " + result.length() + " 字符，保留前 " + maxResultChars + " 字符]";
    }
}
