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
 * <p>Each iteration the model may call any registered tool. When it has a final
 * answer it calls the built-in {@code finish} tool, ending the loop.
 * The model's reasoning appears in the {@code content} field; tool calls appear
 * in the structured {@code tool_calls} field — no text parsing required.
 */
public class ReActAgent extends AbstractAgent {

    private static final String DEFAULT_NAME      = "ReActAgent";
    private static final int    DEFAULT_MAX_STEPS = 10;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a problem-solving agent. Think step by step and use tools to gather information.
            When you have enough information to give a complete answer, call the `finish` tool
            with your final answer. Do not guess — only call `finish` when you are confident.
            """;

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
    public String name() { return agentName; }

    // --- run -----------------------------------------------------------------

    @Override
    public String run(String task) {
        List<Message> messages = buildMessages(task);
        List<Tool>    allTools = buildTools();
        List<Message> trace    = new ArrayList<>();
        trace.add(Message.user(task));

        for (int step = 0; step < maxSteps; step++) {
            LlmResponse resp = llm.chat(messages, allTools);

            if (!resp.hasToolCalls()) {
                String answer = resp.content() != null ? resp.content() : "";
                trace.add(Message.assistant(answer));
                addMessage(Message.user(task));
                addMessage(Message.assistant(answer));
                addExecutionTrace(trace);
                return answer;
            }

            String finalAnswer = appendToolExchange(messages, trace, resp, null);
            if (finalAnswer != null) {
                trace.add(Message.assistant(finalAnswer));
                addMessage(Message.user(task));
                addMessage(Message.assistant(finalAnswer));
                addExecutionTrace(trace);
                return finalAnswer;
            }
        }

        String fallback = "Max steps reached without a final answer.";
        trace.add(Message.assistant(fallback));
        addMessage(Message.user(task));
        addMessage(Message.assistant(fallback));
        addExecutionTrace(trace);
        return fallback;
    }

    // --- stream --------------------------------------------------------------

    @Override
    public void stream(String task, Consumer<String> onToken) {
        List<Message> messages = buildMessages(task);
        List<Tool>    allTools = buildTools();
        List<Message> trace    = new ArrayList<>();
        trace.add(Message.user(task));

        for (int step = 0; step < maxSteps; step++) {
            LlmResponse resp = llm.stream(messages, allTools, onToken);

            if (!resp.hasToolCalls()) {
                String answer = resp.content() != null ? resp.content() : "";
                trace.add(Message.assistant(answer));
                addMessage(Message.user(task));
                addMessage(Message.assistant(answer));
                addExecutionTrace(trace);
                return;
            }

            String finalAnswer = appendToolExchange(messages, trace, resp, onToken);
            if (finalAnswer != null) {
                onToken.accept("\nAnswer: " + finalAnswer);
                trace.add(Message.assistant(finalAnswer));
                addMessage(Message.user(task));
                addMessage(Message.assistant(finalAnswer));
                addExecutionTrace(trace);
                return;
            }
        }

        String fallback = "Max steps reached without a final answer.";
        onToken.accept("\n" + fallback);
        trace.add(Message.assistant(fallback));
        addMessage(Message.user(task));
        addMessage(Message.assistant(fallback));
        addExecutionTrace(trace);
    }

    // --- helpers -------------------------------------------------------------

    /**
     * Processes all tool calls in {@code resp}, appending the exchange to
     * {@code messages} and {@code trace}. Returns the final answer if the
     * {@code finish} tool was called, or {@code null} to continue the loop.
     */
    private String appendToolExchange(List<Message> messages, List<Message> trace,
                                      LlmResponse resp, Consumer<String> onToken) {
        Message toolCallMsg = Message.assistantWithToolCalls(resp.toolCalls());
        messages.add(toolCallMsg);
        trace.add(toolCallMsg);

        String finalAnswer = null;
        for (FunctionCall call : resp.toolCalls()) {
            String result;
            if ("finish".equals(call.name())) {
                finalAnswer = call.parseArguments().getOrDefault("answer", "");
                result = finalAnswer;
            } else {
                result = hasTools()
                        ? toolRegistry.execute(call.name(), call.parseArguments())
                        : "Error: no tools registered.";
                if (onToken != null) onToken.accept("\n[" + call.name() + "] → " + result + "\n");
            }
            Message toolResultMsg = Message.tool(call.id(), result);
            messages.add(toolResultMsg);
            trace.add(toolResultMsg);
        }
        return finalAnswer;
    }

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

    private List<Tool> buildTools() {
        List<Tool> tools = new ArrayList<>();
        if (hasTools()) tools.addAll(toolRegistry.getTools());
        tools.add(FINISH_TOOL);
        return tools;
    }
}