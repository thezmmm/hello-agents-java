package com.helloagents.core;

import java.util.function.Consumer;

/**
 * Core abstraction for all agents.
 *
 * <p>Every agent accepts a natural-language task and returns a result string,
 * or streams output token-by-token via a {@link Consumer}.
 */
public interface BaseAgent {

    /**
     * Execute the given task and return the complete result (blocking).
     *
     * @param task natural-language task description
     * @return agent's final answer / output
     */
    String run(String task);

    /**
     * Execute the given task and deliver output token-by-token via {@code onToken}.
     * The default implementation calls {@link #run} and emits the full result as one token.
     * Agents that support true streaming should override this method.
     *
     * @param task    natural-language task description
     * @param onToken callback invoked for each text token as it arrives
     */
    default void stream(String task, Consumer<String> onToken) {
        onToken.accept(run(task));
    }

    /**
     * Human-readable name for this agent (used in logs and demos).
     */
    default String name() {
        return getClass().getSimpleName();
    }
}