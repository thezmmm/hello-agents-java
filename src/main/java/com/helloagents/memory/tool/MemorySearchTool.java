package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryEntry;
import com.helloagents.memory.MemoryService;
import com.helloagents.memory.MemoryType;
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
        return "Search memories by keyword. "
                + "Input: query=<keyword> or query=<keyword>|type=<type>. "
                + "Results are sorted by importance descending.";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            Param.required("query", "Case-insensitive keyword to search for", "string"),
            Param.optional("type",  "Restrict to one type: perceptual | working | episodic | semantic", "string")
        );
    }

    @Override
    public String execute(String input) {
        Map<String, String> p = MemoryService.parseParams(input);
        String query = p.get("query");
        if (query == null || query.isBlank()) return "Error: 'query' is required.";

        MemoryType[] types = MemoryService.parseTypes(p.get("type"));
        List<MemoryEntry> results = service.search(query, types);

        if (results.isEmpty()) return "No memories found matching: " + query;
        return "Found %d result(s):\n".formatted(results.size())
                + results.stream().map(MemoryEntry::toString).collect(Collectors.joining("\n"));
    }
}