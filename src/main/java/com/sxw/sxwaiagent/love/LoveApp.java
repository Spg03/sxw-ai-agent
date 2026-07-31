package com.sxw.sxwaiagent.love;

import com.sxw.sxwaiagent.infrastructure.advisor.MyLoggerAdvisor;
import com.sxw.sxwaiagent.infrastructure.cache.LlmAnswerCacheAdvisor;
import com.sxw.sxwaiagent.infrastructure.memory.JdbcChatMemoryRepository;
import com.sxw.sxwaiagent.infrastructure.resilience.DashScopeResilienceAdvisor;
import com.sxw.sxwaiagent.infrastructure.rag.QueryRewriter;
import com.sxw.sxwaiagent.infrastructure.rag.RagFlowKnowledgeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;
    private final VectorStore loveAppVectorStore;
    private final QueryRewriter queryRewriter;
    private final RagFlowKnowledgeService ragFlowKnowledgeService;
    private final ToolCallback[] allTools;
    private final ToolCallbackProvider toolCallbackProvider;

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    private static final String CONVERSATION_ID_PREFIX = "love:";

    /**
     * 初始化 ChatClient，所有依赖统一通过构造函数注入。
     * ChatMemory 使用共享的 JdbcChatMemoryRepository 实现持久化，
     * 并在 conversationId 上拼接 "love:" 前缀避免与 AgentOrchestrator 冲突。
     */
    public LoveApp(ChatModel dashscopeChatModel,
                   ObjectProvider<LlmAnswerCacheAdvisor> llmAnswerCacheAdvisorProvider,
                   ObjectProvider<DashScopeResilienceAdvisor> resilienceAdvisorProvider,
                   @Qualifier("loveAppVectorStore") VectorStore loveAppVectorStore,
                   QueryRewriter queryRewriter,
                   RagFlowKnowledgeService ragFlowKnowledgeService,
                   ToolCallback[] allTools,
                   @Qualifier("mcpToolCallbacks") ToolCallbackProvider toolCallbackProvider,
                   JdbcChatMemoryRepository jdbcChatMemoryRepository) {
        this.loveAppVectorStore = loveAppVectorStore;
        this.queryRewriter = queryRewriter;
        this.ragFlowKnowledgeService = ragFlowKnowledgeService;
        this.allTools = allTools;
        this.toolCallbackProvider = toolCallbackProvider;

        // 基于 JDBC 持久化的对话记忆，与 AgentOrchestrator 共享同一 Repository Bean
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(20)
                .build();
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());
        advisors.add(new MyLoggerAdvisor());
        // LLM 应答缓存（次内层）：相同提问秒级返回 + 降本
        LlmAnswerCacheAdvisor cacheAdvisor = llmAnswerCacheAdvisorProvider.getIfAvailable();
        if (cacheAdvisor != null) {
            advisors.add(cacheAdvisor);
        }
        // Resilience4j（最内层，紧贴 LLM）：重试 + 限流 + 熔断；缓存命中不消耗其配额
        DashScopeResilienceAdvisor resilienceAdvisor = resilienceAdvisorProvider.getIfAvailable();
        if (resilienceAdvisor != null) {
            advisors.add(resilienceAdvisor);
        }
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(advisors)
                .build();
    }

    /**
     * 为 conversationId 添加 "love:" 前缀，避免与 AgentOrchestrator 的 conversationId 冲突。
     */
    private String loveConversationId(String chatId) {
        return CONVERSATION_ID_PREFIX + chatId;
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, loveConversationId(chatId)))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, loveConversationId(chatId)))
                .stream()
                .content();
    }

    record LoveReport(String title, List<String> suggestions) {
    }

    /**
     * AI 恋爱报告功能（结构化输出）
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, loveConversationId(chatId)))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    /**
     * 和 RAG 知识库进行对话
     */
    public String doChatWithRag(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, loveConversationId(chatId)))
                .advisors(new MyLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 和 RAGFlow 知识库进行对话：RAGFlow 负责检索，本项目继续负责生成。
     */
    public String doChatWithRagFlow(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        String ragFlowContext = ragFlowKnowledgeService.retrieveContext(rewrittenMessage);
        String userPrompt = ragFlowContext == null || ragFlowContext.isBlank()
                ? rewrittenMessage
                : ragFlowContext + "\n\nUser question:\n" + rewrittenMessage;
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(userPrompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, loveConversationId(chatId)))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("ragflow content: {}", content);
        return content;
    }

    /**
     * AI 对话（支持调用工具）
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, loveConversationId(chatId)))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 对话（调用 MCP 服务）
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, loveConversationId(chatId)))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
