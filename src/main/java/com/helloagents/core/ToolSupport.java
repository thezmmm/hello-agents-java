package com.helloagents.core;

import com.helloagents.tools.Tool;

import java.util.List;

/**
 * Mixin interface for agents that support dynamic tool management.
 *
 * <p>Agents implementing this interface expose a consistent API for registering,
 * querying, and removing tools at runtime. The underlying {@code ToolRegistry}
 * is typically initialised lazily on the first {@link #addTool} call.
 */
public interface ToolSupport {

    /** Registers a tool with this agent. */
    void addTool(Tool tool);

    /** Returns {@code true} if at least one tool is registered. */
    boolean hasTools();

    /**
     * Removes the tool with the given name.
     *
     * @return {@code true} if the tool was present and removed
     */
    boolean removeTool(String toolName);

    /** Returns the names of all registered tools in registration order. */
    List<String> listTools();
}
