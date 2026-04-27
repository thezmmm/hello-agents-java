package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.ForgetStrategy;
import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import com.helloagents.tools.ToolParameter.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Evict memories by a forgetting strategy. Input: {@code strategy=lru|count=3} */
public class MemoryForgetTool implements Tool {

    private final MemoryService service;

    public MemoryForgetTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "memory_forget"; }

    @Override
    public String description() {
        return "Evict a number of memory entries using a chosen forgetting strategy.";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            Param.required("strategy", "Eviction strategy: lru (least recently used) | lfu (least frequently used) | oldest | lowest_importance", "string"),
            Param.optional("count",    "Number of entries to evict, default 1", "number")
        );
    }

    @Override
    public String execute(Map<String, String> p) {
        ForgetStrategy strategy;
        try {
            strategy = ForgetStrategy.fromString(p.getOrDefault("strategy", "lru"));
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        int count = (int) MemoryService.parseDouble(p.get("count"), 1);

        List<MemoryEntry> forgotten = service.forget(strategy, count);
        if (forgotten.isEmpty()) return "No memories to forget.";
        return "Forgot %d memory(s) [%s]:\n".formatted(forgotten.size(), strategy)
                + forgotten.stream().map(MemoryEntry::toString).collect(Collectors.joining("\n"));
    }
}