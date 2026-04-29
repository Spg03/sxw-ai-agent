package com.sxw.sxwaiagent.infrastructure.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SxwManus 会话记忆存储（基于内存）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>按 {@code chatId} 隔离：不同会话之间互不影响；</li>
 *   <li>有界 LRU：限制最大会话数，防止长跑造成 OOM；</li>
 *   <li>读写均加锁，保证并发安全（Manus 实例本身非线程安全，但同一 chatId 也可能并发触发）；</li>
 *   <li>仅保存 {@link Message} 对象引用，不做序列化。</li>
 * </ul>
 * 如需持久化，可换成 {@code FileBasedChatMemory} / Redis / DB 实现。
 */
@Component
public class ManusMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(ManusMemoryStore.class);

    /** 最多缓存的会话数（超出后按 LRU 淘汰最久未访问的）。 */
    private static final int MAX_SESSIONS = 256;

    /** 单个会话最多保留的消息条数（超出后裁剪最早的，保护 token / 内存）。 */
    private static final int MAX_MESSAGES_PER_SESSION = 60;

    private final Map<String, List<Message>> sessions = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
                    return size() > MAX_SESSIONS;
                }
            }
    );

    /**
     * 加载指定会话的历史消息（返回拷贝，调用方可自由追加而不影响 store）。
     * 当 chatId 不存在时返回空 List。
     */
    public List<Message> load(String chatId) {
        if (chatId == null || chatId.isBlank()) return List.of();
        synchronized (sessions) {
            List<Message> stored = sessions.get(chatId);
            if (stored == null || stored.isEmpty()) return List.of();
            return List.copyOf(stored);
        }
    }

    /**
     * 保存指定会话的最新消息列表（覆盖式）。
     * <p>过滤规则：仅保留 {@link UserMessage} 与“纯文本”的 {@link AssistantMessage}。
     * 丢弃 {@code AssistantMessage(toolCalls=...)} 和 {@code ToolResponseMessage}，避免下一轮请求
     * 重放时 {@code tool_call_id} 无法配对造成模型报错/倒贴。会按 {@link #MAX_MESSAGES_PER_SESSION} 裁剪最早消息。</p>
     */
    public void save(String chatId, List<Message> messages) {
        if (chatId == null || chatId.isBlank() || messages == null) return;
        List<Message> filtered = new ArrayList<>(messages.size());
        for (Message m : messages) {
            if (m instanceof UserMessage) {
                filtered.add(m);
            } else if (m instanceof AssistantMessage am
                    && (am.getToolCalls() == null || am.getToolCalls().isEmpty())
                    && am.getText() != null && !am.getText().isBlank()) {
                filtered.add(am);
            }
            // 其他类型（SystemMessage / ToolResponseMessage / 带 toolCalls 的 AssistantMessage）丢弃
        }
        if (filtered.size() > MAX_MESSAGES_PER_SESSION) {
            filtered = new ArrayList<>(filtered.subList(filtered.size() - MAX_MESSAGES_PER_SESSION, filtered.size()));
        }
        List<Message> snapshot = List.copyOf(filtered);
        synchronized (sessions) {
            sessions.put(chatId, snapshot);
        }
        log.info("manus memory saved: chatId={} kept={}/{} msgs", chatId, snapshot.size(), messages.size());
    }

    /** 清空指定会话历史。 */
    public void clear(String chatId) {
        if (chatId == null || chatId.isBlank()) return;
        synchronized (sessions) {
            sessions.remove(chatId);
        }
    }

    /** 当前缓存的会话数（监控用）。 */
    public int sessionCount() {
        synchronized (sessions) {
            return sessions.size();
        }
    }
}
