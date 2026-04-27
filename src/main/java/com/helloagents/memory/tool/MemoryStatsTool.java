package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryService;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

import java.util.stream.Collectors;

/** Return aggregate statistics across all memory types. Input: (none) */
public class MemoryStatsTool implements Tool {

    private final MemoryService service;

    public MemoryStatsTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "memory_stats"; }

    @Override
    public String description() {
        return "Show memory statistics: total and per-type entry counts, average importance, and most-accessed entry.";
    }

    @Override public ToolParameter parameters() { return ToolParameter.empty(); }

    @Override
    public String execute(String input) {
        return "=== Memory Stats ===\n"
                + service.stats().entrySet().stream()
                        .map(e -> "  %s: %s".formatted(e.getKey(), e.getValue()))
                        .collect(Collectors.joining("\n"));
    }
}