package com.helloagents.agents;

import com.helloagents.core.AbstractAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;

import java.util.List;
import java.util.function.Consumer;

/**
 * The simplest possible agent: a single LLM call with an optional system prompt.
 *
 * <p>Chapter 1 — Hello Agent: demonstrates the most basic agent pattern where
 * the user task is sent directly to the LLM and the reply is returned as-is.
 */
public class SimpleAgent extends AbstractAgent {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful assistant. Answer the user's question concisely and accurately.
            """;

    private final LlmClient llm;
    private final String systemPrompt;

    public SimpleAgent(LlmClient llm) {
        this(llm, DEFAULT_SYSTEM_PROMPT);
    }

    public SimpleAgent(LlmClient llm, String systemPrompt) {
        this.llm = llm;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String run(String task) {
        String response = llm.chat(messages(task));
        addMessage(Message.user(task));
        addMessage(Message.assistant(response));
        return response;
    }

    @Override
    public void stream(String task, Consumer<String> onToken) {
        StringBuilder buf = new StringBuilder();
        llm.stream(messages(task), token -> {
            buf.append(token);
            onToken.accept(token);
        });
        recordTurn(task, buf.toString());
    }

    private List<Message> messages(String task) {
        return List.of(Message.system(systemPrompt), Message.user(task));
    }
}