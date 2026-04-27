package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryManager;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

/** Remove every entry from every memory type store. Input: (none) */
public class MemoryClearTool implements Tool {

    private final MemoryManager manager;

    public MemoryClearTool(MemoryManager manager) {
        this.manager = manager;
    }

    @Override public String name() { return "memory_clear"; }

    @Override
    public String description() {
        return "Erase every memory entry across all types. This action is irreversible — use with caution.";
    }

    @Override public ToolParameter parameters() { return ToolParameter.empty(); }

    @Override
    public String execute(java.util.Map<String, String> params) {
        manager.clearAll();
        return "All memories cleared.";
    }
}