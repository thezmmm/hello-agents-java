package com.helloagents.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single tool-call returned by the LLM via native function calling.
 *
 * @param id        unique call ID assigned by the API (used to match tool results)
 * @param name      function name matching the tool registered in {@link com.helloagents.tools.ToolRegistry}
 * @param arguments raw JSON string of arguments, e.g. {@code {"expression":"2+2"}}
 */
public record FunctionCall(String id, String name, String arguments) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /**
     * Parses {@link #arguments} into a lowercase-keyed {@code Map<String, String>}.
     * All values are coerced to {@link String}. Returns an empty map if arguments
     * is blank or not a JSON object.
     */
    public Map<String, String> parseArguments() {
        Map<String, String> result = new LinkedHashMap<>();
        if (arguments == null || arguments.isBlank()) return result;
        String trimmed = arguments.strip();
        if (!trimmed.startsWith("{")) return result;
        try {
            Map<String, Object> raw = MAPPER.readValue(trimmed, MAP_TYPE);
            raw.forEach((k, v) -> result.put(k.toLowerCase(), v == null ? "" : v.toString()));
        } catch (Exception ignored) {}
        return result;
    }
}
