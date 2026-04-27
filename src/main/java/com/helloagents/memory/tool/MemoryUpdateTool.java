package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryManager;
import com.helloagents.memory.MemoryService;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import com.helloagents.tools.ToolParameter.Param;

import java.util.Map;

/** Update an existing memory entry. Input: {@code id=<id>|content=<new text>|importance=<0.0-1.0>} */
public class MemoryUpdateTool implements Tool {

    private final MemoryManager manager;

    public MemoryUpdateTool(MemoryManager manager) {
        this.manager = manager;
    }

    @Override public String name() { return "memory_update"; }

    @Override
    public String description() {
        return "Update the content or importance of an existing memory entry by its ID.";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            Param.required("id",      "Entry ID returned by memory_add", "string"),
            Param.required("content", "Replacement content", "string"),
            Param.optional("importance", "New importance 0.0-1.0 (keeps current value if omitted)", "number")
        );
    }

    @Override
    public String execute(String input) {
        Map<String, String> p = MemoryService.parseParams(input);
        String id      = p.get("id");
        String content = p.get("content");
        if (id == null || id.isBlank())          return "Error: 'id' is required.";
        if (content == null || content.isBlank()) return "Error: 'content' is required.";

        double importance = MemoryService.parseDouble(p.get("importance"), Double.NaN);
        if (Double.isNaN(importance)) {
            importance = manager.get(id).map(e -> e.importance()).orElse(0.5);
        }

        return manager.update(id, content, importance)
                ? "Memory updated. id=%s importance=%.2f".formatted(id, importance)
                : "Error: no memory found with id=" + id;
    }
}