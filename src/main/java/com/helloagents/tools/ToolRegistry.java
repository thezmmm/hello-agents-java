package com.helloagents.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registry that holds all available tools and dispatches execution by name.
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
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

    /** Returns a formatted list of tool names and descriptions for the system prompt. */
    public String describe() {
        return tools.values().stream()
                .map(t -> "- %s: %s".formatted(t.name(), t.description()))
                .collect(Collectors.joining("\n"));
    }
}
