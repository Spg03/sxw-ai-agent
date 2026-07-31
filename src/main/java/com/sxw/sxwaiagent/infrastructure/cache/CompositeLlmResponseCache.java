package com.sxw.sxwaiagent.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * L1(Caffeine) + L2(Redis) 双层缓存实现，含穿透防护和雪崩防护。
 * <p>
 * 查找顺序：L1 → L2 → miss（由调用方调 LLM 后 put 回填两层）。
 * <ul>
 *   <li>L1 hit → 直接返回（最快路径）</li>
 *   <li>L1 miss / L2 hit → 回填 L1 并返回</li>
 *   <li>L1 miss / L2 miss → 返回 null，由调用方调 LLM 后 put 同时写两层</li>
 * </ul>
 * <p>
 * <b>穿透防护（Penetration Guard）</b>：对无有效响应的查询，缓存一个短 TTL 的空标记，
 * 防止同一恶意/无效查询反复击穿到 LLM。
 * <p>
 * <b>雪崩防护（Avalanche Guard）</b>：对同一 key 的并发 miss，使用 singleflight 模式
 * （inflight map + CompletableFuture），确保同一时刻只有一个线程调用 LLM，
 * 其余线程等待结果共享。
 * <p>
 * Redis 不可用时 graceful degradation：所有 Redis 异常被 RedisLlmResponseCache 内部 catch，
 * 此处无需额外处理；若 redisCache 为 null 则直接退化为纯 Caffeine L1。
 */
public class CompositeLlmResponseCache implements LlmResponseCache {

    private static final Logger log = LoggerFactory.getLogger(CompositeLlmResponseCache.class);

    /** 空标记哨兵：表示"该 key 已被查询过但无有效 LLM 响应" */
    private static final ChatClientResponse NULL_SENTINEL = ChatClientResponse.builder().build();

    private final Cache<String, ChatClientResponse> l1Cache;

    /** 穿透防护：短 TTL 的空标记缓存，防止同一无效查询反复穿透到 LLM */
    private final Cache<String, Boolean> nullCache;

    /** 雪崩防护：inflight map，同一 key 的并发 miss 只调一次 LLM */
    private final ConcurrentHashMap<String, CompletableFuture<ChatClientResponse>> inflight = new ConcurrentHashMap<>();

    @Nullable
    private final RedisLlmResponseCache redisCache;

    public CompositeLlmResponseCache(Cache<String, ChatClientResponse> l1Cache,
                                     @Nullable RedisLlmResponseCache redisCache) {
        this(l1Cache, redisCache, Duration.ofSeconds(30));
    }

    public CompositeLlmResponseCache(Cache<String, ChatClientResponse> l1Cache,
                                     @Nullable RedisLlmResponseCache redisCache,
                                     Duration nullTtl) {
        this.l1Cache = l1Cache;
        this.redisCache = redisCache;
        this.nullCache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(nullTtl)
                .build();
        if (redisCache != null) {
            log.info("CompositeLlmResponseCache initialized with L1(Caffeine) + L2(Redis), nullTtl={}s",
                    nullTtl.toSeconds());
        } else {
            log.info("CompositeLlmResponseCache initialized with L1(Caffeine) only — Redis unavailable");
        }
    }

    @Override
    public ChatClientResponse get(String key) {
        // 0. 穿透检查：该 key 最近被查过且无有效响应，直接返回 null（不调 LLM）
        if (nullCache.getIfPresent(key) != null) {
            log.debug("Null-cache hit (penetration guard) key={}", key);
            return null;
        }

        // 1. L1 (Caffeine) — fastest path
        ChatClientResponse value = l1Cache.getIfPresent(key);
        if (value != null) {
            log.debug("L1 cache hit key={}", key);
            return value;
        }

        // 2. L2 (Redis) — only if available
        if (redisCache != null) {
            value = redisCache.get(key);
            if (value != null) {
                log.info("L2 cache hit key={}, backfilling L1", key);
                l1Cache.put(key, value);
                return value;
            }
        }

        log.debug("L1+L2 cache miss key={}", key);
        return null;
    }

    @Override
    public void put(String key, ChatClientResponse response) {
        // 有效响应 → 清除空标记，写入两层缓存
        nullCache.invalidate(key);
        l1Cache.put(key, response);
        if (redisCache != null) {
            redisCache.put(key, response);
        }
    }

    /**
     * 穿透防护：标记该 key 为"无有效响应"，短 TTL 内不再穿透到 LLM。
     * 用于 LLM 返回空/错误响应的场景。
     */
    public void putNull(String key) {
        nullCache.put(key, Boolean.TRUE);
        log.debug("Null-cache stored key={} (penetration guard)", key);
    }

    /**
     * 雪崩防护：singleflight 模式。对同一 key 的并发 miss，只允许一个线程
     * 执行 {@code loader}（通常是 LLM 调用），其余线程等待并共享结果。
     *
     * @param key    缓存 key
     * @param loader LLM 调用（仅在 cache miss 时执行）
     * @return 缓存或新加载的响应
     */
    public ChatClientResponse computeIfAbsent(String key, Supplier<ChatClientResponse> loader) {
        // 先检查缓存（快速路径）
        ChatClientResponse cached = get(key);
        if (cached != null) {
            return cached;
        }

        // 创建或复用 inflight future
        CompletableFuture<ChatClientResponse> future = new CompletableFuture<>();
        CompletableFuture<ChatClientResponse> existing = inflight.putIfAbsent(key, future);

        if (existing != null) {
            // 已有其他线程在加载同一 key，等待其结果
            log.debug("Singleflight: waiting for inflight key={}", key);
            try {
                return existing.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for cache load", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Cache load failed", e.getCause());
            }
        }

        // 本线程负责加载
        try {
            ChatClientResponse response = loader.get();
            if (response != null) {
                put(key, response);
            } else {
                putNull(key);
            }
            future.complete(response);
            return response;
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
            throw e;
        } finally {
            inflight.remove(key);
        }
    }

    /**
     * 同时清除 L1 和 L2 中指定 key 的缓存条目（含空标记）。
     */
    public void evict(String key) {
        l1Cache.invalidate(key);
        nullCache.invalidate(key);
        if (redisCache != null) {
            redisCache.evict(key);
        }
    }
}
