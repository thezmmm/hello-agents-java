package com.helloagents.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single tool-call parsed from an LLM response.
 *
 * <p>Wire format in LLM output: {@code [TOOL_CALL:tool_name:{"param":"value"}]}
 *
 * @param toolName   name of the tool to invoke
 * @param parameters raw JSON parameter string extracted from the response
 * @param original   the full matched text (used for string replacement in SimpleAgent)
 */
public record ToolCall(String toolName, String parameters, String original) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /**
     * Parses a raw JSON parameter string into a lowercase-keyed {@code Map<String, String>}.
     * All values are coerced to {@link String}. Returns an empty map if {@code input} is
     * blank or not a JSON object.
     */
    public static Map<String, String> parse(String input) {
        Map<String, String> result = new LinkedHashMap<>();
        if (input == null || input.isBlank()) return result;
        String trimmed = input.strip();
        if (!trimmed.startsWith("{")) return result;
        try {
            Map<String, Object> raw = MAPPER.readValue(trimmed, MAP_TYPE);
            raw.forEach((k, v) -> result.put(k.toLowerCase(), v == null ? "" : v.toString()));
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Returns {@link #parameters} parsed into a key→value map.
     * Equivalent to {@code ToolCall.parse(this.parameters())}.
     */
    public Map<String, String> parsedParams() {
        return parse(parameters);
    }
}
