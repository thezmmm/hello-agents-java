package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryService;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import com.helloagents.tools.ToolParameter.Param;

import java.util.Map;

/**
 * delete_memory — remove an outdated or revoked memory entry.
 *
 * <p>Call when the user explicitly revokes a previously stated preference or correction,
 * or when you confirm that a remembered fact is no longer accurate.
 * Do NOT call speculatively — only delete when the memory is clearly invalid.
 */
public class MemoryRemoveTool implements Tool {

    private final MemoryService service;

    public MemoryRemoveTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "delete_memory"; }

    @Override
    public String description() {
        return """
                Delete a memory entry that is outdated or explicitly revoked by the user. \
                Call only when you have confirmed the memory is no longer valid. \
                Use search_memory first to find the entry ID.""";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(Param.required("id", "Entry ID to delete (from save_memory or search_memory)", "string"));
    }

    @Override
    public String execute(Map<String, String> params) {
        String id = params.getOrDefault("id", "").strip();
        if (id.isBlank()) return "Error: 'id' is required.";
        return service.delete(id)
                ? "Memory deleted. id=" + id
                : "Error: no memory found with id=" + id;
    }
}