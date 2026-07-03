package com.sxw.sxwaiagent.agent.hermes;

public record HermesReply(
        String reply,
        String emotionTag,
        String summary,
        String suggestion
) {
}
