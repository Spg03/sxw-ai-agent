package com.sxw.sxwaiagent.agent.dto;

/**
 * Agent 执行错误分类
 * <p>
 * 用于区分不同类型的执行失败，便于前端差异化展示和运维分级告警。
 */
public enum AgentErrorKind {

    /** LLM 调用超时 */
    TIMEOUT("服务响应超时，请稍后重试。"),

    /** 触发限流 */
    RATE_LIMIT("当前请求过于频繁，请稍等片刻再试。"),

    /** 熔断器开启，下游不可用 */
    CIRCUIT_OPEN("AI 服务暂时不可用，系统正在恢复中，请稍后重试。"),

    /** 模型返回错误（解析失败、token 超限等） */
    MODEL_ERROR("AI 模型处理异常，请尝试简化您的问题后重试。"),

    /** 未分类错误 */
    UNKNOWN("抱歉，处理您的请求时遇到了问题，请稍后重试。");

    private final String userMessage;

    AgentErrorKind(String userMessage) {
        this.userMessage = userMessage;
    }

    /**
     * 面向用户的友好话术
     */
    public String userMessage() {
        return userMessage;
    }

    /**
     * 根据异常类型推断错误分类
     */
    public static AgentErrorKind fromException(Exception e) {
        if (e == null) {
            return UNKNOWN;
        }

        String className = e.getClass().getName();
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        // 超时类
        if (e instanceof java.util.concurrent.TimeoutException
                || e instanceof java.net.SocketTimeoutException
                || message.contains("timeout")
                || message.contains("timed out")) {
            return TIMEOUT;
        }

        // 限流类
        if (className.contains("RateLimit")
                || className.contains("RequestNotPermitted")
                || message.contains("rate limit")
                || message.contains("too many requests")
                || message.contains("429")) {
            return RATE_LIMIT;
        }

        // 熔断类
        if (className.contains("CallNotPermitted")
                || className.contains("CircuitBreaker")
                || message.contains("circuit breaker")
                || message.contains("circuitbreaker")) {
            return CIRCUIT_OPEN;
        }

        // 模型错误（Spring AI 相关异常）
        if (className.contains("springframework.ai")
                || message.contains("token")
                || message.contains("model")
                || message.contains("dashscope")) {
            return MODEL_ERROR;
        }

        return UNKNOWN;
    }
}
