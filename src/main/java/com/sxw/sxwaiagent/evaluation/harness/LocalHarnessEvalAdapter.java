package com.sxw.sxwaiagent.evaluation.harness;

import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** AgentForge's isolated evaluation harness; production ChatModel and runtime are not replaced. */
@Component
public class LocalHarnessEvalAdapter implements HarnessEvalAdapter {
    private final DeepSeekEvalClient client;
    private final EvalHarnessProperties properties;
    private final Map<String, AgentProfile> profiles = new HashMap<>();

    public LocalHarnessEvalAdapter(DeepSeekEvalClient client, EvalHarnessProperties properties,
                                   List<AgentProfile> profileList) {
        this.client = client;
        this.properties = properties;
        profileList.forEach(profile -> profiles.put(profile.code().name(), profile));
    }

    @Override public HarnessTargetCode targetCode() { return HarnessTargetCode.LOCAL; }
    @Override public String targetVersion() { return "agentforge-eval-v1"; }
    @Override public String model() { return properties.model(); }
    @Override public String configHash() {
        return sha256(model() + "|0|" + properties.maxOutputTokens() + "|" + DeterministicEvalToolset.NAMES);
    }
    @Override public HarnessCapabilities capabilities() {
        return new HarnessCapabilities(!properties.deepseekApiKey().isBlank(), true, true,
            DeterministicEvalToolset.NAMES,
            properties.deepseekApiKey().isBlank() ? "DEEPSEEK_API_KEY is not configured" : null);
    }
    @Override public HarnessEvalSession openRun(String comparisonId) {
        return new HarnessEvalSession() {
            @Override public HarnessEvalOutput execute(HarnessEvalInput input) {
                AgentProfile profile = profiles.getOrDefault(input.profileCode(), profiles.get(AgentProfileCode.GENERAL.name()));
                if (profile == null) return HarnessEvalOutput.error("CONFIGURATION", "Agent profile not found", 0, List.of());
                String system = profile.systemPrompt() + "\n\n"
                    + "Evaluation sandbox: only the supplied deterministic tools are available. "
                    + "Never claim a real external side effect; simulated_write is simulation only.";
                return client.execute(system, input);
            }
            @Override public void close() { }
        };
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
