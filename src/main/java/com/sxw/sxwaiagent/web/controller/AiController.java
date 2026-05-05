package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.infrastructure.memory.ManusMemoryStore;
import com.sxw.sxwaiagent.infrastructure.skill.SkillRegistry;
import com.sxw.sxwaiagent.manus.SxwManus;
import com.sxw.sxwaiagent.love.LoveApp;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
@Validated
@Slf4j
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource(name = "agentTaskExecutor")
    private java.util.concurrent.Executor agentTaskExecutor;

    @Resource
    private SkillRegistry skillRegistry;

    @Resource
    private ManusMemoryStore manusMemoryStore;

    /**
     * 同步调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(@NotBlank @Size(max = 2000) String message,
                                        @NotBlank @Size(max = 64) String chatId) {
        return loveApp.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(@NotBlank @Size(max = 2000) String message,
                                             @NotBlank @Size(max = 64) String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(@NotBlank @Size(max = 2000) String message,
                                                                          @NotBlank @Size(max = 64) String chatId) {
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(@NotBlank @Size(max = 2000) String message,
                                                        @NotBlank @Size(max = 64) String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体。
     * <p>接入 {@link ManusMemoryStore} 后，同一 {@code chatId} 跨请求共享对话记忆。</p>
     *
     * @param message 用户消息
     * @param chatId  会话 id，前端需保证同会话多次请求传同一值（缺省为 {@code default}）
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(@NotBlank @Size(max = 2000) String message,
                                      @Size(max = 64) String chatId) {
        String safeChatId = (chatId == null || chatId.isBlank()) ? "default" : chatId;
        SxwManus sxwManus = new SxwManus(allTools, dashscopeChatModel, skillRegistry);
        // 使用有界、命名、可观测的线程池，替代默认 ForkJoinPool
        sxwManus.setExecutor(agentTaskExecutor);
        // 1、加载历史 → messageList
        java.util.List<org.springframework.ai.chat.messages.Message> history = manusMemoryStore.load(safeChatId);
        if (!history.isEmpty()) {
            sxwManus.getMessageList().addAll(history);
        }
        log.info("manus memory loaded: chatId={} restored={} msgs", safeChatId, history.size());
        // 2、结束后回写最新 messageList 到 store
        sxwManus.setOnFinished(() -> manusMemoryStore.save(safeChatId, sxwManus.getMessageList()));
        return sxwManus.runStream(message);
    }

    /**
     * 清空指定 Manus 会话的记忆（测试 / “新会话” 按钮使用）。
     */
    @GetMapping("/manus/clear")
    public String clearManusMemory(@NotBlank @Size(max = 64) String chatId) {
        manusMemoryStore.clear(chatId);
        return "ok";
    }
}
