package com.helloagents.core;

import com.helloagents.llm.Message;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base implementation of {@link BaseAgent} that provides conversation history management
 * and tool support via {@link ToolSupport}.
 *
 * <p>The {@link ToolRegistry} is initialised lazily on the first {@link #addTool} call.
 * Subclasses access it through the {@code protected} field {@code toolRegistry}.
 */
public abstract class AbstractAgent implements BaseAgent, ToolSupport {

    private final List<Message> history = new ArrayList<>();

    /** Lazily initialised when the first tool is registered. */
    protected ToolRegistry toolRegistry;

    @Override
    public void addMessage(Message message) {
        history.add(message);
    }

    @Override
    public List<Message> getHistory() {
        return Collections.unmodifiableList(history);
    }

    @Override
    public void clearHistory() {
        history.clear();
    }

    // --- ToolSupport ---------------------------------------------------------

    @Override
    public void addTool(Tool tool) {
        if (toolRegistry == null) toolRegistry = new ToolRegistry();
        toolRegistry.register(tool);
    }

    @Override
    public boolean hasTools() {
        return toolRegistry != null && toolRegistry.hasTools();
    }

    @Override
    public boolean removeTool(String toolName) {
        return toolRegistry != null && toolRegistry.unregister(toolName);
    }

    @Override
    public List<String> listTools() {
        return toolRegistry == null ? List.of() : toolRegistry.list();
    }
}
