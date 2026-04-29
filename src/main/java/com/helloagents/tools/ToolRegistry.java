package com.helloagents.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry that holds all available tools and dispatches execution by name.
 */
public class ToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    /**
     * Registers a lambda as a tool without implementing the {@link Tool} interface.
     *
     * @param name        unique tool name
     * @param description one-line description shown to the LLM
     * @param fn          function that receives parsed params and returns the result
     */
    public ToolRegistry register(String name, String description, Function<Map<String, String>, String> fn) {
        return register(new Tool() {
            @Override public String name()        { return name; }
            @Override public String description() { return description; }
            @Override public String execute(Map<String, String> params) { return fn.apply(params); }
        });
    }

    /**
     * Registers a lambda as a tool with explicit parameter metadata.
     *
     * @param name        unique tool name
     * @param description one-line description shown to the LLM
     * @param parameters  parameter schema
     * @param fn          function that receives parsed params and returns the result
     */
    public ToolRegistry register(String name, String description,
                                 ToolParameter parameters, Function<Map<String, String>, String> fn) {
        return register(new Tool() {
            @Override public String name()              { return name; }
            @Override public String description()       { return description; }
            @Override public ToolParameter parameters() { return parameters; }
            @Override public String execute(Map<String, String> params) { return fn.apply(params); }
        });
    }

    /** Removes the tool with the given name. Returns {@code true} if it was present. */
    public boolean unregister(String toolName) {
        return tools.remove(toolName) != null;
    }

    /** Returns the names of all registered tools in registration order. */
    public List<String> list() {
        return List.copyOf(tools.keySet());
    }

    /** Returns {@code true} if at least one tool is registered. */
    public boolean hasTools() {
        return !tools.isEmpty();
    }

    /** Returns all registered tools in registration order. */
    public List<Tool> getTools() {
        return List.copyOf(tools.values());
    }

    /** Executes a tool by name with pre-parsed parameters. */
    public String execute(String toolName, Map<String, String> params) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return "Error: unknown tool '%s'. Available tools: %s"
                    .formatted(toolName, String.join(", ", tools.keySet()));
        }
        try {
            return tool.execute(params);
        } catch (Exception e) {
            return "Error executing tool '%s': %s".formatted(toolName, e.getMessage());
        }
    }

    /**
     * Convenience overload: parses a JSON string then executes.
     * Useful for tests and demos — pass {@code "{}"} or {@code ""} for no params.
     */
    public String execute(String toolName, String jsonInput) {
        return execute(toolName, parseJson(jsonInput));
    }

    /** Returns a formatted description of all tools for display purposes. */
    public String describe() {
        return tools.values().stream()
                .map(t -> {
                    String header = "- %s: %s".formatted(t.name(), t.description());
                    ToolParameter params = t.parameters();
                    if (params.isEmpty()) return header;
                    return header + "\n" + params.describe();
                })
                .collect(Collectors.joining("\n"));
    }

    // -------------------------------------------------------------------------

    private static Map<String, String> parseJson(String input) {
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
}
