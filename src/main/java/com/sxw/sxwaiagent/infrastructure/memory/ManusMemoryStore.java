package com.sxw.sxwaiagent.infrastructure.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SxwManus 会话记忆存储适配器。
 * <p>
 * 已降级为 {@link JdbcChatMemoryRepository} 的适配层，底层委托 PostgreSQL。
 * 保留过滤逻辑（仅 UserMessage + 纯文本 AssistantMessage），旧链路兼容。
 *
 * @deprecated 新链路使用 {@link JdbcChatMemoryRepository} 直接操作 PG。
 *             旧 Runtime 迁移完成后删除此类。
 */
@Component
@Deprecated
public class ManusMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(ManusMemoryStore.class);

    private final JdbcChatMemoryRepository jdbcRepo;

    public ManusMemoryStore(JdbcChatMemoryRepository jdbcRepo) {
        this.jdbcRepo = jdbcRepo;
    }

    /**
     * 加载指定会话的历史消息（从 PostgreSQL）。
     * 过滤：仅保留 UserMessage + 纯文本 AssistantMessage。
     */
    public List<Message> load(String chatId) {
        if (chatId == null || chatId.isBlank()) return List.of();
        List<Message> all = jdbcRepo.findByConversationId(chatId);
        List<Message> filtered = new ArrayList<>(all.size());
        for (Message m : all) {
            if (m instanceof UserMessage) {
                filtered.add(m);
            } else if (m instanceof AssistantMessage am
                    && (am.getToolCalls() == null || am.getToolCalls().isEmpty())
                    && am.getText() != null && !am.getText().isBlank()) {
                filtered.add(am);
            }
        }
        log.debug("manus memory loaded: chatId={} total={} filtered={}", chatId, all.size(), filtered.size());
        return List.copyOf(filtered);
    }

    /**
     * 保存指定会话的消息列表到 PostgreSQL。
     * 过滤后写入，与 load 的过滤规则一致。
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
        }
        jdbcRepo.saveAll(chatId, filtered);
        log.info("manus memory saved: chatId={} kept={}/{} msgs", chatId, filtered.size(), messages.size());
    }

    /** 清空指定会话历史（PostgreSQL）。 */
    public void clear(String chatId) {
        if (chatId == null || chatId.isBlank()) return;
        jdbcRepo.deleteByConversationId(chatId);
    }

    /** 当前缓存的会话数。已降级到 PG，无法统计，返回 0。 */
    @Deprecated
    public int sessionCount() {
        return 0;
    }
}
