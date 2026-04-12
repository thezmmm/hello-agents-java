package com.helloagents.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Registry that holds all available tools and dispatches execution by name.
 */
public class ToolRegistry {

    /** Matches {@code [TOOL_CALL:tool_name:parameters]} embedded in LLM output. */
    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("\\[TOOL_CALL:([^:]+):([^]]+)]");

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
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

    public String execute(String toolName, String input) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return "Error: unknown tool '%s'. Available tools: %s"
                    .formatted(toolName, String.join(", ", tools.keySet()));
        }
        try {
            return tool.execute(input);
        } catch (Exception e) {
            return "Error executing tool '%s': %s".formatted(toolName, e.getMessage());
        }
    }

    /**
     * Parses all {@code [TOOL_CALL:name:params]} markers from the given LLM response text.
     *
     * @param text raw LLM response
     * @return ordered list of parsed tool calls; empty if none found
     */
    public List<ToolCall> parseToolCalls(String text) {
        Matcher m = TOOL_CALL_PATTERN.matcher(text);
        List<ToolCall> calls = new ArrayList<>();
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
