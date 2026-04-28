package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.memory.core.MemoryType;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

import java.util.List;
import java.util.Map;

/**
 * list_memories — show all saved memories grouped by type.
 *
 * <p>Call when you need a full overview of what has been remembered — for example,
 * at session start to reload context, or when the user asks what you remember about them.
 */
public class MemorySummaryTool implements Tool {

    private final MemoryService service;

    public MemorySummaryTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "list_memories"; }

    @Override
    public String description() {
        return """
                List all stored memories grouped by type (user / feedback / project / reference), \
                Call at session start to reload context, or when the user \
                asks what you remember.""";
    }

    @Override public ToolParameter parameters() { return ToolParameter.empty(); }

    @Override
    public String execute(java.util.Map<String, String> params) {
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