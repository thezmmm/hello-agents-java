package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryService;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import com.helloagents.tools.ToolParameter.Param;

import java.util.Map;

/**
 * update_memory — correct the content of an existing memory entry.
 *
 * <p>Call when a user clarifies or revises something you already remembered — prefer this
 * over delete + re-save. Use search_memory first to retrieve the entry ID.
 */
public class MemoryUpdateTool implements Tool {

    private final MemoryService service;

    public MemoryUpdateTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "update_memory"; }

    @Override
    public String description() {
        return """
                Update the content of an existing memory entry. Call when a user clarifies or \
                revises something you already remembered — prefer this over delete + re-save. \
                Use search_memory first to retrieve the entry ID.""";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            Param.required("id",      "Entry ID from save_memory or search_memory", "string"),
            Param.required("content", "Revised memory content", "string")
        );
    }

    @Override
    public String execute(Map<String, String> p) {
        String id      = p.get("id");
        String content = p.get("content");
        if (id == null || id.isBlank())          return "Error: 'id' is required.";
        if (content == null || content.isBlank()) return "Error: 'content' is required.";

        return service.update(id, content)
                ? "Memory updated. id=" + id
                : "Error: no memory found with id=" + id;
    }
}