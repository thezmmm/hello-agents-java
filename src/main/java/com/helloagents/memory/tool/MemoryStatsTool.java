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
        return "Return memory statistics: total count, per-type counts, average importance, "
                + "most-accessed entry. No input required.";
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