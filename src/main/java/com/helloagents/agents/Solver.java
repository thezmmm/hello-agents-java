package com.helloagents.agents;

import com.helloagents.llm.FunctionCall;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.LlmResponse;
import com.helloagents.llm.Message;
import com.helloagents.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Executes a plan one step at a time using native function calling for tool invocation.
 *
 * <p>Each step gets one LLM call with the task, the current step description, and all previous
 * step results as context. When a {@link ToolRegistry} is supplied, each step may call tools
 * via the native function calling protocol before producing its result.
 */
public class Solver {

    private static final int DEFAULT_MAX_TOOL_ITERATIONS = 3;

    private static final String STEP_SYSTEM = """
            You are executing one step of a multi-step plan.
            Your only job is to complete the CURRENT STEP — do not attempt other steps.

            Use the previous step results solely as reference context.
            Do not repeat, summarize, or comment on them.

            Output exactly one line:
            Result: <Answer for this step>

            Rules:
            - Output ONLY the Result line, nothing else.
            - Be concise and factual.
            - If the current step involves a calculation, show only the final number.
            """;

    private final LlmClient llm;

    public Solver(LlmClient llm) {
        this.llm = llm;
    }

    // --- public API ----------------------------------------------------------

    public String solve(String task, List<String> steps) {
        return solve(task, steps, null);
    }

    public void stream(String task, List<String> steps, Consumer<String> onToken) {
        stream(task, steps, null, onToken);
    }

    public String solve(String task, List<String> steps, ToolRegistry toolRegistry) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            List<Message> messages = buildMessages(task, steps, i, results);
            results.add(runStep(messages, toolRegistry));
        }
        return results.get(results.size() - 1);
    }

    public void stream(String task, List<String> steps, ToolRegistry toolRegistry, Consumer<String> onToken) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            onToken.accept("Step " + (i + 1) + ": " + steps.get(i) + "\n");
            List<Message> messages = buildMessages(task, steps, i, results);
            results.add(streamStep(messages, toolRegistry, onToken));
            onToken.accept("\n");
        }
    }

    // --- step execution ------------------------------------------------------

    private String runStep(List<Message> messages, ToolRegistry toolRegistry) {
        if (!hasTools(toolRegistry)) return llm.chat(messages);

        List<Message> history = new ArrayList<>(messages);
        for (int i = 0; i < DEFAULT_MAX_TOOL_ITERATIONS; i++) {
            LlmResponse resp = llm.chat(history, toolRegistry.getTools());
            if (!resp.hasToolCalls()) {
                return resp.content() != null ? resp.content() : "";
            }
            history.add(Message.assistantWithToolCalls(resp.toolCalls()));
            for (FunctionCall call : resp.toolCalls()) {
                history.add(Message.tool(call.id(), toolRegistry.execute(call.name(), call.parseArguments())));
            }
        }
        return llm.chat(history);
    }

    private String streamStep(List<Message> messages, ToolRegistry toolRegistry, Consumer<String> onToken) {
        if (!hasTools(toolRegistry)) {
            StringBuilder buf = new StringBuilder();
            llm.stream(messages, token -> { buf.append(token); onToken.accept(token); });
            return buf.toString();
        }

        List<Message> history = new ArrayList<>(messages);
        for (int i = 0; i < DEFAULT_MAX_TOOL_ITERATIONS; i++) {
            LlmResponse resp = llm.stream(history, toolRegistry.getTools(), onToken);
            if (!resp.hasToolCalls()) {
                return resp.content() != null ? resp.content() : "";
            }
            history.add(Message.assistantWithToolCalls(resp.toolCalls()));
            for (FunctionCall call : resp.toolCalls()) {
                String result = toolRegistry.execute(call.name(), call.parseArguments());
                onToken.accept("\n[" + call.name() + "] → " + result + "\n");
                history.add(Message.tool(call.id(), result));
            }
        }
        LlmResponse resp = llm.stream(history, toolRegistry.getTools(), onToken);
        return resp.content() != null ? resp.content() : "";
    }

    // --- message building ----------------------------------------------------

    private List<Message> buildMessages(String task, List<String> steps, int currentIndex,
                                        List<String> prevResults) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(STEP_SYSTEM));
        messages.add(Message.user("Task: " + task));

        for (int i = 0; i < prevResults.size(); i++) {
            messages.add(Message.user("Step " + (i + 1) + ": " + steps.get(i)));
            messages.add(Message.assistant(prevResults.get(i)));
        }

        messages.add(Message.user("Step " + (currentIndex + 1) + ": " + steps.get(currentIndex)));
        return messages;
    }

    // --- helpers -------------------------------------------------------------

    private boolean hasTools(ToolRegistry toolRegistry) {
        return toolRegistry != null && toolRegistry.hasTools();
    }
}
