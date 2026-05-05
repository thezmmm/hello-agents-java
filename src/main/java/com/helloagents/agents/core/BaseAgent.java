package com.helloagents.core;

import com.helloagents.llm.Message;

import java.util.List;
import java.util.function.Consumer;

/**
 * Core abstraction for all agents.
 *
 * <p>Every agent accepts a natural-language task and returns a result string,
 * or streams output token-by-token via a {@link Consumer}.
 * Agents maintain a conversation history across multiple {@link #run} calls.
 */
public interface BaseAgent {

    /**
     * Execute the given task and return the complete result (blocking).
     * Each call appends a user + assistant turn to the conversation history.
     *
     * @param task natural-language task description
     * @return agent's final answer / output
     */
    String run(String task);

    /**
     * Execute the given task and deliver output token-by-token via {@code onToken}.
     * Each call appends a user + assistant turn to the conversation history.
     * The default implementation calls {@link #run} and emits the full result as one token.
     *
     * @param task    natural-language task description
     * @param onToken callback invoked for each text token as it arrives
     */
    default void stream(String task, Consumer<String> onToken) {
        onToken.accept(run(task));
    }

    /**
     * Returns an unmodifiable view of the conversation history.
     * History contains interleaved user and assistant messages from all previous runs.
     */
    List<Message> getHistory();

    /** Appends a message to the conversation history. */
    void addMessage(Message message);

    /** Clears the conversation history. */
    void clearHistory();

    /**
     * Returns all execution traces, one per {@link #run} call.
     * Each trace is the full message sequence for that run: user input,
     * any tool call exchanges, and the final assistant answer.
     */
    List<List<Message>> getExecutionTrace();

    /**
     * Returns the message sequence from the most recent {@link #run},
     * or an empty list if no runs have been made yet.
     */
    default List<Message> getLastExecution() {
        List<List<Message>> trace = getExecutionTrace();
        return trace.isEmpty() ? List.of() : trace.get(trace.size() - 1);
    }

    /** Clears all stored execution traces. */
    void clearExecutionTrace();

    /** Human-readable name for this agent (used in logs and demos). */
    default String name() {
        return getClass().getSimpleName();
    }
}
