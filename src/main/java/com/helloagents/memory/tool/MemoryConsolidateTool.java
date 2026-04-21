package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryEntry;
import com.helloagents.memory.MemoryService;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

import java.util.List;
import java.util.stream.Collectors;

/** Promote short-lived memories toward longer-term storage. Input: (none) */
public class MemoryConsolidateTool implements Tool {

    private final MemoryService service;

    public MemoryConsolidateTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "memory_consolidate"; }

    @Override
    public String description() {
        return "Promote short-lived memories to longer-term storage: "
                + "perceptual(importance>=0.5)->working, working(importance>=0.7)->episodic/semantic. "
                + "No input required.";
    }

    @Override public ToolParameter parameters() { return ToolParameter.empty(); }

    @Override
    public String execute(String input) {
        List<MemoryEntry> promoted = service.consolidate();
        if (promoted.isEmpty()) return "No memories eligible for consolidation.";
        return "Consolidated %d memory(s):\n".formatted(promoted.size())
                + promoted.stream().map(MemoryEntry::toString).collect(Collectors.joining("\n"));
    }
}