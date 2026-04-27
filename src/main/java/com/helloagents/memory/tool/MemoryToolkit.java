package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryManager;
import com.helloagents.memory.MemoryService;
import com.helloagents.tools.Tool;
import com.helloagents.tools.Toolkit;
import com.helloagents.tools.ToolRegistry;

import java.util.List;

/**
 * Factory that wires {@link MemoryManager} and {@link MemoryService} together
 * and exposes all nine memory tools sharing the same underlying stores.
 *
 * <p>Typical usage:
 * <pre>
 *   MemoryToolkit kit = new MemoryToolkit();
 *   kit.registerAll(toolRegistry);
 * </pre>
 */
public class MemoryToolkit implements Toolkit {

    private final MemoryManager manager;
    private final MemoryService  service;
    private final List<Tool>     tools;

    public MemoryToolkit() {
        this(new MemoryManager());
    }

    public MemoryToolkit(MemoryManager manager) {
        this.manager = manager;
        this.service = new MemoryService(manager);
        this.tools = List.of(
            new MemoryAddTool(service),
            new MemoryUpdateTool(manager),
            new MemoryRemoveTool(manager),
            new MemoryClearTool(manager),
            new MemorySearchTool(service),
            new MemorySummaryTool(service),
            new MemoryStatsTool(service),
            new MemoryForgetTool(service),
            new MemoryConsolidateTool(service)
        );
    }

    @Override public String name()        { return "memory"; }
    @Override public String description() { return "Tools for storing, searching, updating, and managing agent memory"; }

    public MemoryManager getManager() { return manager; }
    public MemoryService  getService() { return service; }
    @Override public List<Tool> getTools() { return tools; }
}