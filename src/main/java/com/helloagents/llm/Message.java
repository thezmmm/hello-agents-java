package com.helloagents.llm;

/**
 * Represents a single chat message.
 *
 * <p>Supported roles: {@code system}, {@code user}, {@code assistant}, {@code tool}.
 * The {@code toolCallId} field is only meaningful for {@code tool} role messages.
 */
public record Message(String role, String content, String toolCallId) {

    /** Convenience constructor for non-tool messages (toolCallId is null). */
    public Message(String role, String content) {
        this(role, content, null);
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
     * Creates a tool-result message.
     *
     * @param toolCallId the ID of the tool call this result belongs to
     * @param content    the tool's output
     */
    public static Message tool(String toolCallId, String content) {
        return new Message("tool", content, toolCallId);
    }
}
