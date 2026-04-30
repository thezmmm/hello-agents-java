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
 * <p>Both {@link #run} and {@link #stream} are overloaded to accept an optional
 * {@code maxToolIterations} argument. Tool calling is handled by private loop
 * methods, one per interaction mode.
 */
public class SimpleAgent extends AbstractAgent {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a helpful assistant. Answer the user's question concisely and accurately.";
    private static final int DEFAULT_MAX_TOOL_ITERATIONS = 3;

    private final String    agentName;
    private final LlmClient llm;
    private final String    systemPrompt;

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
    public String name() { return agentName; }

    // --- run -----------------------------------------------------------------

    @Override
    public String run(String task) {
        return run(task, DEFAULT_MAX_TOOL_ITERATIONS);
    }

    public String run(String task, int maxToolIterations) {
        List<Message> messages = buildMessages(task);
        List<Message> trace    = new ArrayList<>();
        trace.add(Message.user(task));

        String answer;
        if (hasTools()) {
            answer = runToolLoop(messages, task, maxToolIterations, trace);
        } else {
            answer = llm.chat(messages);
            trace.add(Message.assistant(answer));
            addMessage(Message.user(task));
            addMessage(Message.assistant(answer));
        }

        addExecutionTrace(trace);
        return answer;
    }

    // --- stream --------------------------------------------------------------

    @Override
    public void stream(String task, Consumer<String> onToken) {
        stream(task, DEFAULT_MAX_TOOL_ITERATIONS, onToken);
    }

    public void stream(String task, int maxToolIterations, Consumer<String> onToken) {
        List<Message> messages = buildMessages(task);
        List<Message> trace    = new ArrayList<>();
        trace.add(Message.user(task));

        if (hasTools()) {
            streamToolLoop(messages, task, maxToolIterations, trace, onToken);
        } else {
            StringBuilder buf = new StringBuilder();
            llm.stream(messages, token -> { buf.append(token); onToken.accept(token); });
            String answer = buf.toString();
            trace.add(Message.assistant(answer));
            addMessage(Message.user(task));
            addMessage(Message.assistant(answer));
        }

        addExecutionTrace(trace);
    }

    // --- tool loops ----------------------------------------------------------

    private String runToolLoop(List<Message> messages, String task,
                               int maxToolIterations, List<Message> trace) {
        for (int i = 0; i < maxToolIterations; i++) {
            LlmResponse resp = llm.chat(messages, toolRegistry.getTools());

            if (!resp.hasToolCalls()) {
                String answer = resp.content() != null ? resp.content() : "";
                trace.add(Message.assistant(answer));
                addMessage(Message.user(task));
                addMessage(Message.assistant(answer));
                return answer;
            }

            appendToolExchange(messages, trace, resp, null);
        }

        // iterations exhausted — final answer without tools
        String answer = llm.chat(messages);
        trace.add(Message.assistant(answer));
        addMessage(Message.user(task));
        addMessage(Message.assistant(answer));
        return answer;
    }

    private void streamToolLoop(List<Message> messages, String task,
                                int maxToolIterations, List<Message> trace,
                                Consumer<String> onToken) {
        for (int i = 0; i < maxToolIterations; i++) {
            LlmResponse resp = llm.stream(messages, toolRegistry.getTools(), onToken);

            if (!resp.hasToolCalls()) {
                String answer = resp.content() != null ? resp.content() : "";
                trace.add(Message.assistant(answer));
                addMessage(Message.user(task));
                addMessage(Message.assistant(answer));
                return;
            }

            appendToolExchange(messages, trace, resp, onToken);
        }

        // iterations exhausted — stream final answer without tools
        StringBuilder buf = new StringBuilder();
        llm.stream(messages, token -> { buf.append(token); onToken.accept(token); });
        String answer = buf.toString();
        trace.add(Message.assistant(answer));
        addMessage(Message.user(task));
        addMessage(Message.assistant(answer));
    }

    /**
     * Executes all tool calls in {@code resp}, appending the exchange to
     * {@code messages} and {@code trace}. Notifies {@code onToken} if non-null.
     */
    private void appendToolExchange(List<Message> messages, List<Message> trace,
                                    LlmResponse resp, Consumer<String> onToken) {
        Message toolCallMsg = Message.assistantWithToolCalls(resp.toolCalls());
        messages.add(toolCallMsg);
        trace.add(toolCallMsg);
        for (FunctionCall call : resp.toolCalls()) {
            String  result        = toolRegistry.execute(call.name(), call.parseArguments());
            Message toolResultMsg = Message.tool(call.id(), result);
            messages.add(toolResultMsg);
            trace.add(toolResultMsg);
            if (onToken != null) onToken.accept("\n[" + call.name() + "] → " + result + "\n");
        }
    }

    // --- message building ----------------------------------------------------

    private List<Message> buildMessages(String task) {
        List<Message> messages = new ArrayList<>();

        String baseSystem = systemPromptBuilder != null
                ? systemPromptBuilder.build(task, systemPrompt)
                : systemPrompt;

        if (compressedHistory != null) {
            compressedHistory.sync(getHistory());
            String summary = compressedHistory.getSummary();
            String system  = summary != null
                    ? baseSystem + "\n\nConversation summary:\n" + summary
                    : baseSystem;
            messages.add(Message.system(system));
            messages.addAll(compressedHistory.getRecentMessages());
        } else {
            messages.add(Message.system(baseSystem));
            messages.addAll(getHistory());
        }

        messages.add(Message.user(task));
        return messages;
    }
}