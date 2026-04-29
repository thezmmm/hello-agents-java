package com.helloagents.llm;

import java.util.List;

/**
 * Response from {@link LlmClient#chat(java.util.List, java.util.List)}.
 *
 * <p>When the model decides to call tools, {@link #content} is {@code null} and
 * {@link #toolCalls} is non-empty. When the model produces a final text answer,
 * {@link #toolCalls} is empty and {@link #content} holds the reply.
 *
 * @param content   assistant's text reply; {@code null} when tool calls are present
 * @param toolCalls structured tool calls requested by the model; empty when none
 */
public record LlmResponse(String content, List<FunctionCall> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static LlmResponse ofContent(String content) {
        return new LlmResponse(content, List.of());
    }

    public static LlmResponse ofToolCalls(List<FunctionCall> calls) {
        return new LlmResponse(null, List.copyOf(calls));
    }
}
