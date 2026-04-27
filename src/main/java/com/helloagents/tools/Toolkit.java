package com.helloagents.tools;

import java.util.List;

/**
 * A named, self-describing collection of related {@link Tool}s.
 *
 * <p>Implement this interface to group tools that share common infrastructure
 * (e.g. the same backing store or service). The default {@link #registerAll}
 * implementation iterates {@link #getTools()} and registers each tool,
 * so subclasses only need to provide the list.
 */
public interface Toolkit {

    /** Short identifier for this toolkit, e.g. {@code "memory"} or {@code "rag"}. */
    String name();

    /** One-line description of what this toolkit provides. */
    String description();

    /** All tools belonging to this toolkit. */
    List<Tool> getTools();

    /** Registers every tool in this toolkit into the given registry. */
    default void registerAll(ToolRegistry registry) {
        getTools().forEach(registry::register);
    }
}