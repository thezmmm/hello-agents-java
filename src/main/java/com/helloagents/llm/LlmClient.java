package com.helloagents.llm;

import com.helloagents.tools.Tool;

import java.util.List;
import java.util.function.Consumer;

/**
 * Unified interface for LLM API calls.
 * All agent implementations must use this interface — no direct HTTP calls in business code.
 *
 * <p>Quick construction:
 * <pre>
 *   // from explicit parameters
 *   LlmClient client = LlmClient.of(apiKey, baseUrl, model);
 *
 *   // from environment variables (LLM_API_KEY, LLM_BASE_URL, LLM_MODEL)
 *   LlmClient client = LlmClient.fromEnv();
 * </pre>
 */
public interface LlmClient {

    /**
     * Send a list of messages and return the complete assistant reply (blocking).
     *
     * @param messages conversation history
     * @return assistant reply text
     */
    String chat(List<Message> messages);

    /** Convenience overload for a single user message. */
    default String chat(String userMessage) {
        return chat(List.of(Message.user(userMessage)));
    }

    /**
     * Send a list of messages and deliver the reply token-by-token via {@code onToken}.
     * The default implementation calls {@link #chat} and emits the full reply as one token.
     *
     * @param messages conversation history
     * @param onToken  callback invoked for each text token as it arrives
     */
    default void stream(List<Message> messages, Consumer<String> onToken) {
        onToken.accept(chat(messages));
    }

    /** Convenience overload for a single user message. */
    default void stream(String userMessage, Consumer<String> onToken) {
        stream(List.of(Message.user(userMessage)), onToken);
    }

    /**
     * Send messages with tool definitions and return a structured response (blocking).
     * The response contains either a text reply or a list of tool calls to execute.
     *
     * @param messages conversation history
     * @param tools    tools available to the model
     * @return {@link LlmResponse} with {@code content} or {@code toolCalls}
     */
    default LlmResponse chat(List<Message> messages, List<Tool> tools) {
        throw new UnsupportedOperationException(
                "Native function calling not supported by this LlmClient implementation");
    }

    /**
     * Send messages with tool definitions and stream content tokens as they arrive.
     *
     * <p>Behaviour by finish reason:
     * <ul>
     *   <li>{@code stop} — content tokens are emitted to {@code onToken} in real time.</li>
     *   <li>{@code tool_calls} — no content tokens; {@code onToken} is not called.</li>
     * </ul>
     *
     * <p>The default implementation falls back to the blocking {@link #chat(List, List)} and
     * emits the content (if any) as a single token.
     *
     * @param messages conversation history
     * @param tools    tools available to the model
     * @param onToken  callback invoked for each content token as it arrives
     * @return structured response describing the final state
     */
    default LlmResponse stream(List<Message> messages, List<Tool> tools, Consumer<String> onToken) {
        LlmResponse resp = chat(messages, tools);
        if (!resp.hasToolCalls() && resp.content() != null) {
            onToken.accept(resp.content());
        }
        return resp;
    }

    /**
     * Create a client with explicit parameters.
     *
     * @param apiKey  API key (required)
     * @param baseUrl API base URL, e.g. {@code https://api.openai.com/v1} (null = default)
     * @param model   model name, e.g. {@code gpt-4o} (null = default)
     */
    static LlmClient of(String apiKey, String baseUrl, String model) {
        return new OpenAiClient(apiKey, baseUrl, model);
    }

    /**
     * Create a client from environment variables:
     * {@code LLM_API_KEY}, {@code LLM_BASE_URL}, {@code LLM_MODEL}.
     */
    static LlmClient fromEnv() {
        return OpenAiClient.fromEnv();
    }
}