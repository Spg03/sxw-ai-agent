package com.sxw.sxwaiagent.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.ai.chat.client.ChatClientResponse;

public class CaffeineLlmResponseCache implements LlmResponseCache {

    private final Cache<String, ChatClientResponse> cache;

    public CaffeineLlmResponseCache(Cache<String, ChatClientResponse> cache) {
        this.cache = cache;
    }

    @Override
    public ChatClientResponse get(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void put(String key, ChatClientResponse response) {
        cache.put(key, response);
    }
}
