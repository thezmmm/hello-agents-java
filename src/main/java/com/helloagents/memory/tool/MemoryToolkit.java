package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryManager;
import com.helloagents.memory.MemoryService;
import com.helloagents.tools.Tool;
import com.helloagents.tools.Toolkit;

import java.util.List;

/**
 * Wires a {@link MemoryService} and exposes the five core memory tools.
 * All tools depend solely on {@link MemoryService}; {@link MemoryManager} is an
 * internal implementation detail not exposed to the tool layer.
 *
 * <p>Typical usage:
 * <pre>
 *   MemoryToolkit kit = new MemoryToolkit();
 *   kit.registerAll(toolRegistry);
 * </pre>
 */
public class MemoryToolkit implements Toolkit {

    private final MemoryService service;
    private final List<Tool>    tools;

    public MemoryToolkit() {
        this(new MemoryService(new MemoryManager()));
    }

    public MemoryToolkit(MemoryService service) {
        this.service = service;
        this.tools = List.of(
            new MemoryAddTool(service),
            new MemorySearchTool(service),
            new MemorySummaryTool(service),
            new MemoryUpdateTool(service),
            new MemoryRemoveTool(service)
        );
    }

    @Override public String name()        { return "memory"; }
    @Override public String description() { return "Five tools for saving, searching, listing, updating, and deleting agent memories"; }

    public MemoryService getService() { return service; }
    @Override public List<Tool> getTools() { return tools; }
}