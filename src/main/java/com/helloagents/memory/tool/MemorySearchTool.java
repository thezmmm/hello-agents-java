package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.memory.core.MemoryType;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import com.helloagents.tools.ToolParameter.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * search_memory — look up relevant memories before acting.
 *
 * <p>Call at the start of a new session, when the user references past preferences or decisions,
 * or before making a recommendation that prior context might affect.
 * Results provide directional guidance — always verify against current code state.
 */
public class MemorySearchTool implements Tool {

    private final MemoryService service;

    public MemorySearchTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "search_memory"; }

    @Override
    public String description() {
        return """
                Search stored memories by keyword. Call at the start of a new session or when \
                the user references past preferences, corrections, or decisions. \
                Memories are directional hints — verify against current code before acting on them.""";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            Param.required("query", "Case-insensitive keyword to search for", "string"),
            Param.optional("type",  "Restrict to one type: user | feedback | project | reference", "string")
        );
    }

    @Override
    public String execute(Map<String, String> p) {
        String query = p.get("query");
        if (query == null || query.isBlank()) return "Error: 'query' is required.";

        MemoryType[] types = MemoryService.parseTypes(p.get("type"));
        List<MemoryEntry> results = service.search(query, types);

        if (results.isEmpty()) return "No memories found matching: " + query;
        return "Found %d result(s):\n".formatted(results.size())
                + results.stream().map(MemoryEntry::toString).collect(Collectors.joining("\n"));
    }
}