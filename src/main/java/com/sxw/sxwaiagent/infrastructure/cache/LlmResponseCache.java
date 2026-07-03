package com.sxw.sxwaiagent.infrastructure.cache;

import org.springframework.ai.chat.client.ChatClientResponse;

public interface LlmResponseCache {

    ChatClientResponse get(String key);

    void put(String key, ChatClientResponse response);
}
