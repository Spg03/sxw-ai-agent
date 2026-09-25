package com.sxw.sxwaiagent.evaluation.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/** Newline-delimited JSON-RPC client for @deepseek-ai/dsh-sdk-jsonrpc-server. */
public final class DshJsonRpcClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DshJsonRpcClient.class);
    private final ObjectMapper objectMapper;
    private final Process process;
    private final BufferedWriter writer;
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    private DshJsonRpcClient(Process process, ObjectMapper objectMapper) {
        this.process = process;
        this.objectMapper = objectMapper;
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        Thread.ofVirtual().name("dsh-jsonrpc-stdout").start(this::readStdout);
        Thread.ofVirtual().name("dsh-jsonrpc-stderr").start(this::readStderr);
    }

    public static DshJsonRpcClient start(EvalHarnessProperties properties, ObjectMapper objectMapper) {
        EvalHarnessProperties.Dsh dsh = properties.dsh();
        List<String> command = new ArrayList<>(List.of(
            dsh.dockerCommand(), "run", "--rm", "-i",
            "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=128m",
            "--cpus", "1", "--memory", "768m", "--pids-limit", "128",
            "--network", "bridge", "--env", "DEEPSEEK_API_KEY", "--env", "DEEPSEEK_BASE_URL",
            "--label", "agentforge.eval.target=dsh", dsh.image()));
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            String apiKey = properties.deepseekApiKey();
            if (!apiKey.isBlank()) builder.environment().put("DEEPSEEK_API_KEY", apiKey);
            builder.environment().put("DEEPSEEK_BASE_URL", properties.deepseekBaseUrl());
            Process process = builder.start();
            return new DshJsonRpcClient(process, objectMapper);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start DSH sidecar", e);
        }
    }

    public JsonNode initialize(String provider, String model, int maxTokens, Duration timeout) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("cwd", "/tmp/dsh-eval");
        params.put("provider", provider);
        params.put("model", model);
        params.put("maxTokens", maxTokens);
        return request("initialize", params, timeout);
    }

    public DshSessionResult prompt(String sessionId, String prompt, Duration timeout) throws TimeoutException {
        SessionState state = new SessionState();
        sessions.put(sessionId, state);
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionId", sessionId);
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "text");
        block.put("text", prompt);
        params.putArray("contentBlocks").add(block);
        request("session/prompt", params, timeout);
        try {
            state.idle.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return parseEvents(state.events);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException("DSH session did not become idle within " + timeout.toSeconds() + " seconds");
        } catch (Exception e) {
            throw new IllegalStateException("DSH session failed", e);
        } finally {
            sessions.remove(sessionId);
        }
    }

    private JsonNode request(String method, JsonNode params, Duration timeout) {
        if (closed || !process.isAlive()) throw new IllegalStateException("DSH process is not running");
        long id = ids.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) request.set("params", params);
        try {
            synchronized (writer) {
                writer.write(objectMapper.writeValueAsString(request));
                writer.newLine();
                writer.flush();
            }
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            throw new IllegalStateException("DSH JSON-RPC " + method + " failed", e);
        } finally {
            pending.remove(id);
        }
    }

    private void readStdout() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) dispatch(objectMapper.readTree(line));
            failPending(new IllegalStateException("DSH stdout closed"));
        } catch (Exception e) {
            if (!closed) failPending(e);
        }
    }

    private void dispatch(JsonNode message) {
        if (message.has("id")) {
            long id = message.path("id").asLong();
            CompletableFuture<JsonNode> future = pending.get(id);
            if (future == null) return;
            if (message.has("error")) future.completeExceptionally(
                new IllegalStateException("DSH protocol error: " + message.path("error").path("message").asText()));
            else future.complete(message.path("result"));
            return;
        }
        String method = message.path("method").asText();
        JsonNode params = message.path("params");
        if ("session.event".equals(method)) {
            SessionState state = sessions.get(params.path("sessionId").asText());
            if (state != null) state.events.add(params.path("event").deepCopy());
        } else if ("session.status".equals(method)) {
            SessionState state = sessions.get(params.path("sessionId").asText());
            if (state != null && "idle".equals(params.path("status").asText())) state.idle.complete(null);
        }
    }

    static DshSessionResult parseEvents(List<JsonNode> events) {
        String answer = "";
        String stopReason = null;
        int inputTokens = 0;
        int outputTokens = 0;
        int toolCalls = 0;
        int toolResults = 0;
        int toolSuccesses = 0;
        int violations = 0;
        for (JsonNode event : events) {
            String type = event.path("type").asText();
            JsonNode data = event.path("data");
            if ("assistant/message".equals(type)) {
                StringBuilder text = new StringBuilder();
                for (JsonNode content : data.path("message").path("content")) {
                    if ("text".equals(content.path("type").asText())) text.append(content.path("text").asText());
                }
                if (!text.isEmpty()) answer = text.toString();
                inputTokens += data.path("usage").path("inputTokens").asInt(0);
                outputTokens += data.path("usage").path("outputTokens").asInt(0);
            } else if ("tool/call".equals(type)) {
                toolCalls++;
                if (!DeterministicEvalToolset.NAMES.contains(data.path("name").asText())) violations++;
            } else if ("tool/result".equals(type)) {
                toolResults++;
                if (!data.has("error")) toolSuccesses++;
            } else if ("turn/end".equals(type)) {
                stopReason = data.path("reason").path("kind").asText(stopReason);
            }
        }
        double successRate = toolResults == 0 ? (toolCalls == 0 ? 1.0 : 0.0)
            : (double) toolSuccesses / toolResults;
        return new DshSessionResult(answer, stopReason, inputTokens, outputTokens,
            toolCalls, successRate, violations, List.copyOf(events));
    }

    private void readStderr() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("DSH sidecar: {}", line.replaceAll("sk-[A-Za-z0-9_-]{8,}", "sk-***"));
            }
        } catch (Exception ignored) { }
    }

    private void failPending(Throwable error) {
        pending.values().forEach(future -> future.completeExceptionally(error));
    }

    @Override
    public void close() {
        if (closed) return;
        try { request("shutdown", null, Duration.ofSeconds(3)); } catch (Exception ignored) { }
        closed = true;
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static final class SessionState {
        private final List<JsonNode> events = java.util.Collections.synchronizedList(new ArrayList<>());
        private final CompletableFuture<Void> idle = new CompletableFuture<>();
    }

    public record DshSessionResult(String answer, String stopReason, int inputTokens, int outputTokens,
                                   int toolCallCount, double toolSuccessRate, int securityViolationCount,
                                   List<JsonNode> events) { }
}
