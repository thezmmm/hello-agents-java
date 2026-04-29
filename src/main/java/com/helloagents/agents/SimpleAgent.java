package com.helloagents.agents;

import com.helloagents.core.AbstractAgent;
import com.helloagents.llm.FunctionCall;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.LlmResponse;
import com.helloagents.llm.Message;
import com.helloagents.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A general-purpose conversational agent with optional tool-calling support.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Conversation history is automatically prepended to every call.</li>
 *   <li>When tools are registered, uses native function calling via
 *       {@link LlmClient#chat(java.util.List, java.util.List)} — no text-parsing of tool markers.</li>
 *   <li>Tool results are returned as {@code tool} role messages with matching
 *       {@code tool_call_id}, as required by the OpenAI protocol.</li>
 * </ul>
 */
public class SimpleAgent extends AbstractAgent {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a helpful assistant. Answer the user's question concisely and accurately.";

    private static final int DEFAULT_MAX_TOOL_ITERATIONS = 3;

    private final String agentName;
    private final LlmClient llm;
    private final String systemPrompt;

    // --- constructors --------------------------------------------------------

    public SimpleAgent(LlmClient llm) {
        this("SimpleAgent", llm, DEFAULT_SYSTEM_PROMPT, null);
    }

    public SimpleAgent(LlmClient llm, String systemPrompt) {
        this("SimpleAgent", llm, systemPrompt, null);
    }

    public SimpleAgent(String name, LlmClient llm, String systemPrompt, ToolRegistry toolRegistry) {
        this.agentName    = name;
        this.llm          = llm;
        this.systemPrompt = systemPrompt != null ? systemPrompt : DEFAULT_SYSTEM_PROMPT;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public String name() {
        return agentName;
    }

    // --- BaseAgent -----------------------------------------------------------

    @Override
    public String run(String task) {
        return run(task, DEFAULT_MAX_TOOL_ITERATIONS);
    }

    public String run(String task, int maxToolIterations) {
        List<Message> messages = buildMessages(task);

        String response;
        if (hasTools()) {
            response = runWithTools(messages, task, maxToolIterations);
        } else {
            response = llm.chat(messages);
            addMessage(Message.user(task));
            addMessage(Message.assistant(response));
        }
        return response;
    }

    @Override
    public void stream(String task, Consumer<String> onToken) {
        stream(task, DEFAULT_MAX_TOOL_ITERATIONS, onToken);
    }

    public void stream(String task, int maxToolIterations, Consumer<String> onToken) {
        List<Message> messages = buildMessages(task);

        if (!hasTools()) {
            StringBuilder buf = new StringBuilder();
            llm.stream(messages, token -> {
                buf.append(token);
                onToken.accept(token);
            });
            addMessage(Message.user(task));
            addMessage(Message.assistant(buf.toString()));
            return;
        }

        doStream(messages, task, maxToolIterations, onToken);
    }

    // --- tool-calling loop (native function calling) -------------------------

    private String runWithTools(List<Message> messages, String task, int maxToolIterations) {
        for (int i = 0; i < maxToolIterations; i++) {
            LlmResponse resp = llm.chat(messages, toolRegistry.getTools());

            if (!resp.hasToolCalls()) {
                String answer = resp.content() != null ? resp.content() : "";
                addMessage(Message.user(task));
                addMessage(Message.assistant(answer));
                return answer;
            }

            messages.add(Message.assistantWithToolCalls(resp.toolCalls()));
            for (FunctionCall call : resp.toolCalls()) {
                String result = toolRegistry.execute(call.name(), call.parseArguments());
                messages.add(Message.tool(call.id(), result));
            }
        }

        // iterations exhausted — ask for a final answer without tools
        String answer = llm.chat(messages);
        addMessage(Message.user(task));
        addMessage(Message.assistant(answer));
        return answer;
    }

    private void doStream(List<Message> messages, String task, int maxToolIterations,
                                 Consumer<String> onToken) {
        for (int i = 0; i < maxToolIterations; i++) {
            // stream(tools) emits content tokens for final answers, silent for tool-call rounds
            LlmResponse resp = llm.stream(messages, toolRegistry.getTools(), onToken);

            if (!resp.hasToolCalls()) {
                String answer = resp.content() != null ? resp.content() : "";
                addMessage(Message.user(task));
                addMessage(Message.assistant(answer));
                return;
            }

            messages.add(Message.assistantWithToolCalls(resp.toolCalls()));
            for (FunctionCall call : resp.toolCalls()) {
                String result = toolRegistry.execute(call.name(), call.parseArguments());
                onToken.accept("\n[" + call.name() + "] → " + result + "\n");
                messages.add(Message.tool(call.id(), result));
            }
        }

        // iterations exhausted — stream one final answer
        LlmResponse resp = llm.stream(messages, toolRegistry.getTools(), onToken);
        String answer = resp.content() != null ? resp.content() : "";
        addMessage(Message.user(task));
        addMessage(Message.assistant(answer));
    }

    // --- message building ----------------------------------------------------

    private List<Message> buildMessages(String task) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(systemPrompt));
        messages.addAll(getHistory());
        messages.add(Message.user(task));
        return messages;
    }
}
