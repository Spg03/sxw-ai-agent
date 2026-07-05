package com.sxw.sxwaiagent.hermes;

import com.sxw.sxwaiagent.infrastructure.trace.AgentTraceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Hermes analyzer.
 * <p>
 * Asynchronously analyzes Agent run traces and generates improvement candidates.
 * This is the core component of the Hermes review system.
 * <p>
 * Analysis dimensions:
 * - Success patterns: which tool combinations work well
 * - Failure patterns: which tool calls failed
 * - User preferences: what response style users prefer
 * - Knowledge gaps: which retrievals did not hit
 * - Prompt effectiveness: which prompt versions perform better
 * - Efficiency issues: which call chains are too long
 */
@Component
public class HermesAnalyzer {
    
    private static final Logger log = LoggerFactory.getLogger(HermesAnalyzer.class);
    
    private final HermesCandidateRepository candidateRepository;
    private final AgentTraceStore agentTraceStore;
    
    public HermesAnalyzer(
        HermesCandidateRepository candidateRepository,
        AgentTraceStore agentTraceStore
    ) {
        this.candidateRepository = candidateRepository;
        this.agentTraceStore = agentTraceStore;
    }
    
    /**
     * 异步分析 Agent 运行完成事件
     */
    @Async
    @EventListener
    public void analyzeAgentRun(AgentRunCompletedEvent event) {
        log.info("Hermes analyzing agent run: requestId={}, traceId={}, success={}", 
            event.getRequestId(), event.getTraceId(), event.isSuccess());
        
        try {
            // 分析成功模式
            if (event.isSuccess() && event.getTurnCount() > 0) {
                analyzeSuccessPattern(event);
            }
            
            // 分析失败模式
            if (!event.isSuccess()) {
                analyzeFailurePattern(event);
            }
            
            // 分析效率问题
            if (event.getTurnCount() > 10) {
                analyzeEfficiencyIssue(event);
            }
            
            log.info("Hermes analysis completed for traceId: {}", event.getTraceId());
            
        } catch (Exception e) {
            log.error("Hermes analysis failed for traceId={}: {}", 
                event.getTraceId(), e.getMessage(), e);
        }
    }
    
    /**
     * 分析成功模式
     */
    private void analyzeSuccessPattern(AgentRunCompletedEvent event) {
        String candidateId = "hermes-" + UUID.randomUUID().toString().substring(0, 8);
        
        HermesCandidate candidate = new HermesCandidate(
            null,
            candidateId,
            event.getRequestId(),
            event.getTraceId(),
            HermesCandidateType.MEMORY,
            "成功模式：" + event.getProfileCode() + " 执行成功",
            String.format("Profile %s 在 %d 轮对话后成功完成任务。建议记录此成功模式供后续参考。",
                event.getProfileCode(), event.getTurnCount()),
            "memory",
            new BigDecimal("0.75"),
            HermesCandidateStatus.PENDING,
            LocalDateTime.now(),
            null,
            null,
            null,
            null
        );
        
        candidateRepository.save(candidate);
        log.debug("Generated success pattern candidate: {}", candidateId);
    }
    
    /**
     * 分析失败模式
     */
    private void analyzeFailurePattern(AgentRunCompletedEvent event) {
        String candidateId = "hermes-" + UUID.randomUUID().toString().substring(0, 8);
        
        HermesCandidate candidate = new HermesCandidate(
            null,
            candidateId,
            event.getRequestId(),
            event.getTraceId(),
            HermesCandidateType.TOOL_IMPROVEMENT,
            "失败模式：" + event.getProfileCode() + " 执行失败",
            String.format("Profile %s 在 %d 轮对话后失败。建议分析失败原因并改进工具或 Prompt。摘要：%s",
                event.getProfileCode(), event.getTurnCount(), 
                event.getSummary() != null ? event.getSummary() : "无"),
            "tool",
            new BigDecimal("0.85"),
            HermesCandidateStatus.PENDING,
            LocalDateTime.now(),
            null,
            null,
            null,
            null
        );
        
        candidateRepository.save(candidate);
        log.debug("Generated failure pattern candidate: {}", candidateId);
    }
    
    /**
     * 分析效率问题
     */
    private void analyzeEfficiencyIssue(AgentRunCompletedEvent event) {
        String candidateId = "hermes-" + UUID.randomUUID().toString().substring(0, 8);
        
        HermesCandidate candidate = new HermesCandidate(
            null,
            candidateId,
            event.getRequestId(),
            event.getTraceId(),
            HermesCandidateType.AGENT_RULE,
            "效率问题：对话轮次过多",
            String.format("Profile %s 使用了 %d 轮对话完成任务，可能存在效率问题。建议优化 Prompt 或工具调用策略。",
                event.getProfileCode(), event.getTurnCount()),
            "agent_rule",
            new BigDecimal("0.70"),
            HermesCandidateStatus.PENDING,
            LocalDateTime.now(),
            null,
            null,
            null,
            null
        );
        
        candidateRepository.save(candidate);
        log.debug("Generated efficiency issue candidate: {}", candidateId);
    }
}
