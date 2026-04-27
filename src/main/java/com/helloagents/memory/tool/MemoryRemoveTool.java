package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryManager;
import com.helloagents.memory.MemoryService;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import com.helloagents.tools.ToolParameter.Param;
import java.util.Map;

/** Delete a specific memory entry. Input: the entry ID. */
public class MemoryRemoveTool implements Tool {

    private final MemoryManager manager;

    public MemoryRemoveTool(MemoryManager manager) {
        this.manager = manager;
    }

    @Override public String name() { return "memory_remove"; }

    @Override
    public String description() {
        return "Delete a specific memory entry by its ID (returned by memory_add).";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(Param.required("id", "Entry ID to delete", "string"));
    }

    @Override
    public String execute(Map<String, String> params) {
        String id = params.getOrDefault("id", "").strip();
        if (id.isBlank()) return "Error: 'id' is required.";
        return manager.remove(id)
                ? "Memory removed. id=" + id
                : "Error: no memory found with id=" + id;
    }
}