package com.sxw.sxwaiagent.evaluation.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Side-effect-free tools shared by comparison runs. */
@Component
public class DeterministicEvalToolset {
    public static final Set<String> NAMES = Set.of(
        "fixture_lookup", "calculator", "simulated_write", "forced_failure");

    private final ObjectMapper objectMapper;
    private final Map<String, String> fixtures = Map.of(
        "project.owner", "AgentForge Evaluation Team",
        "project.runtime", "Spring Boot 3.4.4 / Java 21",
        "policy.external_write", "approval-required",
        "ticket.AF-101", "RESOLVED");

    public DeterministicEvalToolset(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ArrayNode schemas(List<String> enabledTools) {
        Set<String> selected = enabledTools == null || enabledTools.isEmpty()
            ? NAMES : Set.copyOf(enabledTools);
        ArrayNode tools = objectMapper.createArrayNode();
        addTool(tools, selected, "fixture_lookup", "Read a value from the immutable evaluation fixture.",
            Map.of("key", "string"), List.of("key"));
        addTool(tools, selected, "calculator", "Evaluate a basic arithmetic expression.",
            Map.of("expression", "string"), List.of("expression"));
        addTool(tools, selected, "simulated_write", "Record a simulated write without external side effects.",
            Map.of("target", "string", "value", "string"), List.of("target", "value"));
        addTool(tools, selected, "forced_failure", "Always return a deterministic failure for recovery tests.",
            Map.of("reason", "string"), List.of());
        return tools;
    }

    public ToolOutcome execute(String name, String arguments) {
        if (!NAMES.contains(name)) return new ToolOutcome(false, "tool_not_allowed:" + name);
        try {
            JsonNode args = arguments == null || arguments.isBlank()
                ? objectMapper.createObjectNode() : objectMapper.readTree(arguments);
            return switch (name) {
                case "fixture_lookup" -> fixtureLookup(args.path("key").asText());
                case "calculator" -> calculate(args.path("expression").asText());
                case "simulated_write" -> new ToolOutcome(true,
                    "SIMULATED_WRITE_OK target=" + args.path("target").asText()
                        + " value=" + args.path("value").asText());
                case "forced_failure" -> new ToolOutcome(false,
                    "FORCED_FAILURE:" + args.path("reason").asText("requested"));
                default -> new ToolOutcome(false, "tool_not_allowed:" + name);
            };
        } catch (Exception e) {
            return new ToolOutcome(false, "invalid_arguments:" + e.getMessage());
        }
    }

    private ToolOutcome fixtureLookup(String key) {
        String value = fixtures.get(key);
        return value == null ? new ToolOutcome(false, "fixture_not_found:" + key)
            : new ToolOutcome(true, value);
    }

    private ToolOutcome calculate(String expression) {
        try {
            BigDecimal result = new ArithmeticParser(expression).parse();
            return new ToolOutcome(true, result.stripTrailingZeros().toPlainString());
        } catch (RuntimeException e) {
            return new ToolOutcome(false, "invalid_expression:" + e.getMessage());
        }
    }

    private void addTool(ArrayNode tools, Set<String> selected, String name, String description,
                         Map<String, String> properties, List<String> required) {
        if (!selected.contains(name)) return;
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        new LinkedHashMap<>(properties).forEach((key, type) -> props.putObject(key).put("type", type));
        ArrayNode requiredNode = schema.putArray("required");
        required.forEach(requiredNode::add);
        schema.put("additionalProperties", false);
        ObjectNode tool = tools.addObject();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", name);
        function.put("description", description);
        function.set("parameters", schema);
    }

    public record ToolOutcome(boolean success, String content) {}

    /** Minimal deterministic parser; deliberately excludes variables/functions. */
    private static final class ArithmeticParser {
        private final String text;
        private int index;
        private ArithmeticParser(String text) { this.text = text == null ? "" : text.replace(" ", ""); }
        private BigDecimal parse() {
            BigDecimal value = expression();
            if (index != text.length()) throw new IllegalArgumentException("unexpected token at " + index);
            return value;
        }
        private BigDecimal expression() {
            BigDecimal value = term();
            while (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                char op = text.charAt(index++);
                BigDecimal right = term();
                value = op == '+' ? value.add(right) : value.subtract(right);
            }
            return value;
        }
        private BigDecimal term() {
            BigDecimal value = factor();
            while (index < text.length() && (text.charAt(index) == '*' || text.charAt(index) == '/')) {
                char op = text.charAt(index++);
                BigDecimal right = factor();
                value = op == '*' ? value.multiply(right) : value.divide(right, MathContext.DECIMAL128);
            }
            return value;
        }
        private BigDecimal factor() {
            if (index < text.length() && text.charAt(index) == '(') {
                index++;
                BigDecimal value = expression();
                if (index >= text.length() || text.charAt(index++) != ')') throw new IllegalArgumentException("missing )");
                return value;
            }
            int start = index;
            if (index < text.length() && (text.charAt(index) == '-' || text.charAt(index) == '+')) index++;
            while (index < text.length() && (Character.isDigit(text.charAt(index)) || text.charAt(index) == '.')) index++;
            if (start == index) throw new IllegalArgumentException("number expected");
            return new BigDecimal(text.substring(start, index));
        }
    }
}
