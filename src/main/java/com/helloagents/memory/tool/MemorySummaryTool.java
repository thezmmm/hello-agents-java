package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryEntry;
import com.helloagents.memory.MemoryService;
import com.helloagents.memory.MemoryType;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

import java.util.List;
import java.util.Map;

/** List all memories grouped by type. Input: (none) */
public class MemorySummaryTool implements Tool {

    private final MemoryService service;

    public MemorySummaryTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "memory_summary"; }

    @Override
    public String description() {
        return "List all memories grouped by type (perceptual, working, episodic, semantic), "
                + "sorted by importance within each group. No input required.";
    }

    @Override public ToolParameter parameters() { return ToolParameter.empty(); }

    @Override
    public String execute(String input) {
        Map<MemoryType, List<MemoryEntry>> byType = service.summary();
        StringBuilder sb = new StringBuilder("=== Memory Summary ===");
        for (MemoryType t : MemoryType.values()) {
            List<MemoryEntry> entries = byType.get(t);
            sb.append("\n\n[%s - %s] %d entries".formatted(t.displayName, t.description, entries.size()));
            entries.forEach(e -> sb.append("\n  ").append(e));
        }
        return sb.toString();
    }
}