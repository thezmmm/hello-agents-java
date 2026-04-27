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

/** Search memories by keyword. Input: {@code query=Java} or {@code query=Java|type=semantic} */
public class MemorySearchTool implements Tool {

    private final MemoryService service;

    public MemorySearchTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "memory_search"; }

    @Override
    public String description() {
        return "Search stored memories by keyword, optionally filtered by type. Results are sorted by importance.";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            Param.required("query", "Case-insensitive keyword to search for", "string"),
            Param.optional("type",  "Restrict to one type: perceptual | working | episodic | semantic", "string")
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