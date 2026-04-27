package com.helloagents.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Registry that holds all available tools and dispatches execution by name.
 */
public class ToolRegistry {

    /** Matches {@code [TOOL_CALL:name:{...}]} or {@code [TOOL_CALL:name:plain]} in LLM output. */
    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("\\[TOOL_CALL:([^:]+):(\\{[^}]*\\}|[^]]+)]");

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    /**
     * Registers a function as a tool without implementing the {@link Tool} interface.
     *
     * <pre>
     *   registry.register("greet", "Greets the user by name", name -> "Hello, " + name + "!");
     * </pre>
     *
     * @param name        unique tool name
     * @param description one-line description shown to the LLM
     * @param fn          function that receives the raw input string and returns the result
     */
    public ToolRegistry register(String name, String description, Function<Map<String, String>, String> fn) {
        return register(new Tool() {
            @Override public String name()        { return name; }
            @Override public String description() { return description; }
            @Override public String execute(Map<String, String> params) { return fn.apply(params); }
        });
    }

    /**
     * Registers a function as a tool with explicit parameter metadata.
     *
     * <pre>
     *   registry.register("greet", "Greets the user by name",
     *           ToolParameter.of(Param.required("name", "Name to greet", "string")),
     *           params -> "Hello, " + params.get("name") + "!");
     * </pre>
     *
     * @param name        unique tool name
     * @param description one-line description shown to the LLM
     * @param parameters  parameter schema for prompt injection
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

    /** Dispatches a parsed {@link ToolCall} to the matching tool. */
    public String execute(ToolCall call) {
        return execute(call.toolName(), call.parsedParams());
    }

    /**
     * Convenience overload for direct invocation (e.g. tests and demos).
     * {@code input} is parsed as a JSON object; pass {@code "{}"} or {@code ""} for no params.
     */
    public String execute(String toolName, String input) {
        return execute(toolName, ToolCall.parse(input));
    }

    private String execute(String toolName, Map<String, String> params) {
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
     * Finds all {@code [TOOL_CALL:name:{"param":"value"}]} markers in {@code text}
     * and returns them as an ordered list of {@link ToolCall}s.
     */
    public List<ToolCall> parseToolCalls(String text) {
        List<ToolCall> calls = new ArrayList<>();
        if (text == null || text.isBlank()) return calls;
        Matcher m = TOOL_CALL_PATTERN.matcher(text);
        while (m.find()) {
            calls.add(new ToolCall(m.group(1).strip(), m.group(2).strip(), m.group(0)));
        }
        return calls;
    }

    /** Returns a formatted list of tool names, descriptions, and parameters for the system prompt. */
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
}
