package com.helloagents.llm;

import java.util.List;

/**
 * Represents a single chat message.
 *
 * <p>Supported roles: {@code system}, {@code user}, {@code assistant}, {@code tool}.
 *
 * <ul>
 *   <li>{@code toolCallId} — only meaningful for {@code tool} role messages (tool result).</li>
 *   <li>{@code toolCalls}  — only meaningful for {@code assistant} messages that request tool calls
 *       (native function calling). Must be sent back to the API when building conversation history.</li>
 * </ul>
 */
public record Message(String role, String content, String toolCallId, List<FunctionCall> toolCalls) {

    /** Convenience constructor — no tool call id, no tool calls. */
    public Message(String role, String content) {
        this(role, content, null, List.of());
    }

    /** Convenience constructor — with tool call id (for tool-result messages), no outgoing tool calls. */
    public Message(String role, String content, String toolCallId) {
        this(role, content, toolCallId, List.of());
    }

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    /**
     * Creates a tool-result message (role = "tool").
     *
     * @param toolCallId the ID of the tool call this result belongs to
     * @param content    the tool's output
     */
    public static Message tool(String toolCallId, String content) {
        return new Message("tool", content, toolCallId);
    }

    /**
     * Creates an assistant message carrying native function-call requests.
     * Used when replaying conversation history that included tool calls.
     *
     * @param toolCalls the function calls the model requested
     */
    public static Message assistantWithToolCalls(List<FunctionCall> toolCalls) {
        return new Message("assistant", null, null, List.copyOf(toolCalls));
    }
}
