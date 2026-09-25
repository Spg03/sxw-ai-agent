package com.sxw.sxwaiagent.evaluation.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class DshHarnessEvalAdapter implements HarnessEvalAdapter {
    private final EvalHarnessProperties properties;
    private final ObjectMapper objectMapper;

    public DshHarnessEvalAdapter(EvalHarnessProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override public HarnessTargetCode targetCode() { return HarnessTargetCode.DSH; }
    @Override public String targetVersion() { return properties.dsh().commit(); }
    @Override public String model() { return properties.model(); }
    @Override public String configHash() {
        return sha256(targetVersion() + "|" + model() + "|0|" + properties.maxOutputTokens());
    }
    @Override public HarnessCapabilities capabilities() {
        if (!properties.dsh().enabled()) return HarnessCapabilities.unavailable("sxw.eval.dsh.enabled=false");
        if (properties.deepseekApiKey().isBlank()) return HarnessCapabilities.unavailable("DEEPSEEK_API_KEY is not configured");
        return new HarnessCapabilities(true, true, true, DeterministicEvalToolset.NAMES, null);
    }

    @Override
    public HarnessEvalSession openRun(String comparisonId) {
        return new Session();
    }

    private final class Session implements HarnessEvalSession {
        private DshJsonRpcClient client;
        private Session() { startClient(); }

        @Override
        public HarnessEvalOutput execute(HarnessEvalInput input) {
            long started = System.currentTimeMillis();
            try {
                DshJsonRpcClient.DshSessionResult result = client.prompt(
                    "eval-" + input.caseId() + "-" + input.repeatIndex() + "-" + System.nanoTime(),
                    input.prompt(), input.timeout());
                return new HarnessEvalOutput(result.answer(), result.stopReason(), result.inputTokens(),
                    result.outputTokens(), System.currentTimeMillis() - started, result.toolCallCount(),
                    result.toolSuccessRate(), result.securityViolationCount(), result.events(), null, null);
            } catch (TimeoutException e) {
                restartClient();
                return HarnessEvalOutput.error("TIMEOUT", e.getMessage(),
                    System.currentTimeMillis() - started, List.of());
            } catch (Exception e) {
                restartClient();
                return HarnessEvalOutput.error("DSH_PROTOCOL", safeMessage(e),
                    System.currentTimeMillis() - started, List.of());
            }
        }

        private void startClient() {
            client = DshJsonRpcClient.start(properties, objectMapper);
            client.initialize(properties.dsh().provider(), properties.model(), properties.maxOutputTokens(),
                Duration.ofSeconds(Math.min(60, properties.dsh().caseTimeoutSeconds())));
        }

        private void restartClient() {
            try { if (client != null) client.close(); } catch (Exception ignored) { }
            startClient();
        }

        @Override public void close() { if (client != null) client.close(); }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName()
            : message.replace(properties.deepseekApiKey(), "***");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
