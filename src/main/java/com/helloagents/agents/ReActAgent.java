package com.helloagents.agents;

import com.helloagents.core.AbstractAgent;
import com.helloagents.llm.FunctionCall;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.LlmResponse;
import com.helloagents.llm.Message;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ReAct (Reasoning + Acting) agent using native function calling.
 *
 * <p>Each iteration the model may call any registered tool to gather information.
 * When it has a final answer it calls the built-in {@code finish} tool, ending the loop.
 * The model's reasoning appears in the {@code content} field; tool calls appear in the
 * structured {@code tool_calls} field — no text parsing required.
 *
 * <p>Construction:
 * <pre>
 *   new ReActAgent(llm)
 *   new ReActAgent("MyAgent", llm, systemPrompt, maxSteps)
 * </pre>
 */
public class ReActAgent extends AbstractAgent {

    private static final String DEFAULT_NAME      = "ReActAgent";
    private static final int    DEFAULT_MAX_STEPS = 10;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a problem-solving agent. Think step by step and use tools to gather information.
            When you have enough information to give a complete answer, call the `finish` tool
            with your final answer. Do not guess — only call `finish` when you are confident.
            """;

    /** Built-in finish tool — signals the end of the ReAct loop. */
    private static final Tool FINISH_TOOL = new Tool() {
        @Override public String name()        { return "finish"; }
        @Override public String description() { return "Call this when you have a complete final answer."; }
        @Override public ToolParameter parameters() {
            return ToolParameter.of(ToolParameter.Param.required("answer", "Your complete final answer", "string"));
        }
        @Override public String execute(Map<String, String> params) {
            return params.getOrDefault("answer", "");
        }
    };

    private final String    agentName;
    private final LlmClient llm;
    private final String    systemPrompt;
    private final int       maxSteps;

    // --- constructors --------------------------------------------------------

    public ReActAgent(LlmClient llm) {
        this(DEFAULT_NAME, llm, null, DEFAULT_MAX_STEPS);
    }

    public ReActAgent(LlmClient llm, int maxSteps) {
        this(DEFAULT_NAME, llm, null, maxSteps);
    }

    public ReActAgent(String name, LlmClient llm, String systemPrompt, int maxSteps) {
        this.agentName    = (name != null && !name.isBlank()) ? name : DEFAULT_NAME;
        this.llm          = llm;
        this.systemPrompt = (systemPrompt != null && !systemPrompt.isBlank()) ? systemPrompt : DEFAULT_SYSTEM_PROMPT;
        this.maxSteps     = maxSteps > 0 ? maxSteps : DEFAULT_MAX_STEPS;
    }

    @Override
    public String name() {
        return agentName;
    }

    // --- run / stream --------------------------------------------------------

    @Override
    public String run(String task) {
        List<Message> messages = buildMessages(task);
        List<Tool>    allTools = buildTools();

        for (int step = 0; step < maxSteps; step++) {
            LlmResponse resp = llm.chat(messages, allTools);

            if (!resp.hasToolCalls()) {
                String answer = resp.content() != null ? resp.content() : "";
                addMessage(Message.user(task));
                addMessage(Message.assistant(answer));
                return answer;
            }

            messages.add(Message.assistantWithToolCalls(resp.toolCalls()));

            String finalAnswer = null;
            for (FunctionCall call : resp.toolCalls()) {
                if ("finish".equals(call.name())) {
                    finalAnswer = call.parseArguments().getOrDefault("answer", "");
                    messages.add(Message.tool(call.id(), finalAnswer));
                } else {
                    String result = hasTools()
                            ? toolRegistry.execute(call.name(), call.parseArguments())
                            : "Error: no tools registered.";
                    messages.add(Message.tool(call.id(), result));
                }
            }

            if (finalAnswer != null) {
                addMessage(Message.user(task));
                addMessage(Message.assistant(finalAnswer));
                return finalAnswer;
            }
        }

        String fallback = "Max steps reached without a final answer.";
        addMessage(Message.user(task));
        addMessage(Message.assistant(fallback));
        return fallback;
    }

    @Override
    public void stream(String task, Consumer<String> onToken) {
        List<Message> messages = buildMessages(task);
        List<Tool>    allTools = buildTools();

        for (int step = 0; step < maxSteps; step++) {
            // intermediate reasoning text is streamed; tool-call rounds produce no content
            LlmResponse resp = llm.stream(messages, allTools, onToken);

            if (!resp.hasToolCalls()) {
                String answer = resp.content() != null ? resp.content() : "";
                addMessage(Message.user(task));
                addMessage(Message.assistant(answer));
                return;
            }

            messages.add(Message.assistantWithToolCalls(resp.toolCalls()));

            String finalAnswer = null;
            for (FunctionCall call : resp.toolCalls()) {
                if ("finish".equals(call.name())) {
                    finalAnswer = call.parseArguments().getOrDefault("answer", "");
                    messages.add(Message.tool(call.id(), finalAnswer));
                } else {
                    String result = hasTools()
                            ? toolRegistry.execute(call.name(), call.parseArguments())
                            : "Error: no tools registered.";
                    onToken.accept("\n[" + call.name() + "] → " + result + "\n");
                    messages.add(Message.tool(call.id(), result));
                }
            }

            if (finalAnswer != null) {
                onToken.accept("\nAnswer: " + finalAnswer);
                addMessage(Message.user(task));
                addMessage(Message.assistant(finalAnswer));
                return;
            }
        }

        String fallback = "\nMax steps reached without a final answer.";
        onToken.accept(fallback);
        addMessage(Message.user(task));
        addMessage(Message.assistant(fallback));
    }

    // --- helpers -------------------------------------------------------------

    private List<Message> buildMessages(String task) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(systemPrompt));
        messages.addAll(getHistory());
        messages.add(Message.user(task));
        return messages;
    }

    private List<Tool> buildTools() {
        List<Tool> tools = new ArrayList<>();
        if (hasTools()) tools.addAll(toolRegistry.getTools());
        tools.add(FINISH_TOOL);
        return tools;
    }
}
