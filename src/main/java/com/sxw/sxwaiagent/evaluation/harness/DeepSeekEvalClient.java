package com.sxw.sxwaiagent.evaluation.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class DeepSeekEvalClient {
    private static final int MAX_TOOL_TURNS = 8;
    private final EvalHarnessProperties properties;
    private final DeterministicEvalToolset toolset;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DeepSeekEvalClient(EvalHarnessProperties properties, DeterministicEvalToolset toolset,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.toolset = toolset;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(properties.deepseekBaseUrl()).build();
    }

    public HarnessEvalOutput execute(String systemPrompt, HarnessEvalInput input) {
        long started = System.currentTimeMillis();
        List<JsonNode> events = new ArrayList<>();
        if (properties.deepseekApiKey().isBlank()) {
            return HarnessEvalOutput.error("CONFIGURATION", "DEEPSEEK_API_KEY is not configured",
                System.currentTimeMillis() - started, events);
        }
        try {
            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(message("system", systemPrompt));
            messages.add(message("user", input.prompt()));
            int toolCalls = 0;
            int successfulTools = 0;
            int securityViolations = 0;
            int inputTokens = 0;
            int outputTokens = 0;
            String stopReason = null;

            for (int turn = 0; turn < MAX_TOOL_TURNS; turn++) {
                ObjectNode request = objectMapper.createObjectNode();
                request.put("model", properties.model());
                request.put("temperature", 0);
                request.put("max_tokens", properties.maxOutputTokens());
                request.set("messages", messages);
                ArrayNode schemas = toolset.schemas(input.enabledTools());
                if (!schemas.isEmpty()) request.set("tools", schemas);

                JsonNode response = restClient.post().uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.deepseekApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve().body(JsonNode.class);
                if (response == null || response.path("choices").isEmpty()) {
                    return HarnessEvalOutput.error("EMPTY_RESPONSE", "DeepSeek returned no choices",
                        System.currentTimeMillis() - started, events);
                }
                inputTokens += response.path("usage").path("prompt_tokens").asInt(0);
                outputTokens += response.path("usage").path("completion_tokens").asInt(0);
                JsonNode choice = response.path("choices").get(0);
                stopReason = choice.path("finish_reason").asText(null);
                JsonNode assistant = choice.path("message").deepCopy();
                events.add(event("assistant", assistant));
                messages.add(assistant);

                JsonNode calls = assistant.path("tool_calls");
                if (!calls.isArray() || calls.isEmpty()) {
                    return new HarnessEvalOutput(assistant.path("content").asText(""), stopReason,
                        inputTokens, outputTokens, System.currentTimeMillis() - started,
                        toolCalls, toolCalls == 0 ? 1.0 : (double) successfulTools / toolCalls,
                        securityViolations, events, null, null);
                }
                for (JsonNode call : calls) {
                    toolCalls++;
                    String name = call.path("function").path("name").asText();
                    String arguments = call.path("function").path("arguments").asText("{}");
                    if (!DeterministicEvalToolset.NAMES.contains(name)
                        || (input.enabledTools() != null && !input.enabledTools().isEmpty()
                            && !input.enabledTools().contains(name))) {
                        securityViolations++;
                    }
                    DeterministicEvalToolset.ToolOutcome outcome = toolset.execute(name, arguments);
                    if (outcome.success()) successfulTools++;
                    ObjectNode toolMessage = message("tool", outcome.content());
                    toolMessage.put("tool_call_id", call.path("id").asText());
                    toolMessage.put("name", name);
                    messages.add(toolMessage);
                    events.add(event("tool", toolMessage));
                }
            }
            return HarnessEvalOutput.error("MAX_TURNS", "Evaluation tool loop reached its turn limit",
                System.currentTimeMillis() - started, events);
        } catch (Exception e) {
            return HarnessEvalOutput.error("MODEL_ERROR", safeMessage(e),
                System.currentTimeMillis() - started, events);
        }
    }

    private ObjectNode message(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }

    private ObjectNode event(String type, JsonNode payload) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        node.set("payload", payload);
        return node;
    }

    private String safeMessage(Exception e) {
        String value = e.getMessage();
        return value == null ? e.getClass().getSimpleName() : value.replace(properties.deepseekApiKey(), "***");
    }
}
