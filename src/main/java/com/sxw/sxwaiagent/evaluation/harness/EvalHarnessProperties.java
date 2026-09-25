package com.sxw.sxwaiagent.evaluation.harness;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sxw.eval")
public record EvalHarnessProperties(
    String model,
    Integer maxOutputTokens,
    Integer repeats,
    String deepseekBaseUrl,
    String deepseekApiKey,
    Dsh dsh
) {
    public EvalHarnessProperties {
        model = defaultString(model, "deepseek-chat");
        maxOutputTokens = maxOutputTokens == null ? 4096 : maxOutputTokens;
        repeats = repeats == null ? 3 : repeats;
        deepseekBaseUrl = defaultString(deepseekBaseUrl, "https://api.deepseek.com");
        deepseekApiKey = deepseekApiKey == null ? "" : deepseekApiKey;
        dsh = dsh == null ? new Dsh(null, null, null, null, null, null, null, null) : dsh;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Dsh(
        Boolean enabled,
        String image,
        String commit,
        Integer caseTimeoutSeconds,
        Integer runTimeoutMinutes,
        String dockerCommand,
        String provider,
        String eventLogBucket
    ) {
        public Dsh {
            enabled = enabled != null && enabled;
            image = defaultString(image, "agentforge/dsh-eval:99f6f02");
            commit = defaultString(commit, "99f6f02");
            caseTimeoutSeconds = caseTimeoutSeconds == null ? 180 : caseTimeoutSeconds;
            runTimeoutMinutes = runTimeoutMinutes == null ? 30 : runTimeoutMinutes;
            dockerCommand = defaultString(dockerCommand, "docker");
            provider = defaultString(provider, "deepseek-official");
            eventLogBucket = defaultString(eventLogBucket, "sxw-eval-events");
        }
    }
}
