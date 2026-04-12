package com.helloagents.agents;

import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;
import com.helloagents.tools.ToolCall;
import com.helloagents.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Executes a plan one step at a time.
 *
 * <p>Each step gets one LLM call with the task, the current step description, and all previous
 * step results as context. Every step is handled identically — the last step's result is
 * returned directly as the final answer.
 *
 * <p>When a {@link ToolRegistry} is supplied, each step runs a tool-call loop (same
 * {@code [TOOL_CALL:name:params]} format as {@code SimpleAgent}) before moving to the next step.
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
            List<Message> messages = buildMessages(task, steps, i, results, toolRegistry);
            results.add(runStep(messages, toolRegistry));
        }
        return results.get(results.size() - 1);
    }

    public void stream(String task, List<String> steps, ToolRegistry toolRegistry, Consumer<String> onToken) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            onToken.accept("Step " + (i + 1) + ": " + steps.get(i) + "\n");
            List<Message> messages = buildMessages(task, steps, i, results, toolRegistry);
            results.add(streamStep(messages, toolRegistry, onToken));
            onToken.accept("\n");
        }
    }

    // --- step execution ------------------------------------------------------

    private String runStep(List<Message> messages, ToolRegistry toolRegistry) {
        if (!hasTools(toolRegistry)) return llm.chat(messages);

        List<Message> history = new ArrayList<>(messages);
        for (int i = 0; i < DEFAULT_MAX_TOOL_ITERATIONS; i++) {
            String response = llm.chat(history);
            List<ToolCall> toolCalls = toolRegistry.parseToolCalls(response);
            if (toolCalls.isEmpty()) return response;

            history.add(Message.assistant(stripToolCalls(response, toolCalls)));
            for (ToolCall call : toolCalls) {
                history.add(Message.user("Observation: " + toolRegistry.execute(call.toolName(), call.parameters())));
            }
        }
        return llm.chat(history);
    }

    private String streamStep(List<Message> messages, ToolRegistry toolRegistry, Consumer<String> onToken) {
        List<Message> history = new ArrayList<>(messages);
        for (int i = 0; i < DEFAULT_MAX_TOOL_ITERATIONS; i++) {
            StringBuilder buf = new StringBuilder();
            llm.stream(history, token -> { buf.append(token); onToken.accept(token); });
            String response = buf.toString();

            if (!hasTools(toolRegistry)) return response;

            List<ToolCall> toolCalls = toolRegistry.parseToolCalls(response);
            if (toolCalls.isEmpty()) return response;

            history.add(Message.assistant(stripToolCalls(response, toolCalls)));
            for (ToolCall call : toolCalls) {
                String observation = "Observation: " + toolRegistry.execute(call.toolName(), call.parameters());
                onToken.accept("\n" + observation + "\n");
                history.add(Message.user(observation));
            }
        }
        StringBuilder buf = new StringBuilder();
        llm.stream(history, token -> { buf.append(token); onToken.accept(token); });
        return buf.toString();
    }

    // --- message building ----------------------------------------------------

    private List<Message> buildMessages(String task, List<String> steps, int currentIndex,
                                        List<String> prevResults, ToolRegistry toolRegistry) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(buildSystemPrompt(toolRegistry)));

        // first user turn: the overall task
        messages.add(Message.user("Task: " + task));

        // interleaved turns for each completed step
        for (int i = 0; i < prevResults.size(); i++) {
            messages.add(Message.user("Step " + (i + 1) + ": " + steps.get(i)));
            messages.add(Message.assistant(prevResults.get(i)));
        }

        // current step as the latest user turn
        messages.add(Message.user("Step " + (currentIndex + 1) + ": " + steps.get(currentIndex)));
        return messages;
    }

    private String buildSystemPrompt(ToolRegistry toolRegistry) {
        if (!hasTools(toolRegistry)) return STEP_SYSTEM;
        return STEP_SYSTEM + """


                ## Available Tools
                """ + toolRegistry.describe() + """


                ## Tool Call Format
                `[TOOL_CALL:tool_name:parameters]`
                """;
    }

    // --- helpers -------------------------------------------------------------

    private boolean hasTools(ToolRegistry toolRegistry) {
        return toolRegistry != null && toolRegistry.hasTools();
    }

    private String stripToolCalls(String response, List<ToolCall> toolCalls) {
        String clean = response;
        for (ToolCall call : toolCalls) clean = clean.replace(call.original(), "");
        return clean.strip();
    }
}
