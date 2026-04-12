package com.helloagents.agents;

import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Executes a plan one step at a time.
 *
 * <p>Each step gets one LLM call with the task, the current step description, and all previous
 * step results as context. Every step is handled identically — the last step's result is
 * returned directly as the final answer.
 */
public class Solver {

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

    /**
     * Executes each step individually and returns the last step's output as the final answer (blocking).
     *
     * @param task  natural-language task description
     * @param steps ordered list of sub-steps produced by {@link Planner}
     * @return last step's output, used directly as the final answer
     */
    public String solve(String task, List<String> steps) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            results.add(llm.chat(stepMessages(task, steps.get(i), i + 1, results)));
        }
        return results.get(results.size() - 1);
    }

    /**
     * Executes each step individually, streaming output as it is produced.
     * Step headers are injected as synthetic tokens. The last step streams directly as the final answer.
     *
     * @param task    natural-language task description
     * @param steps   ordered list of sub-steps produced by {@link Planner}
     * @param onToken callback invoked for each text token as it arrives
     */
    public void stream(String task, List<String> steps, Consumer<String> onToken) {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            onToken.accept("Step " + (i + 1) + ": " + steps.get(i) + "\n");
            StringBuilder buf = new StringBuilder();
            llm.stream(stepMessages(task, steps.get(i), i + 1, results), token -> {
                buf.append(token);
                onToken.accept(token);
            });
            results.add(buf.toString());
            onToken.accept("\n");
        }
    }

    private List<Message> stepMessages(String task, String step, int n, List<String> prevResults) {
        return List.of(Message.system(STEP_SYSTEM), Message.user(buildContext(task, step, n, prevResults)));
    }

    private String buildContext(String task, String step, int n, List<String> prevResults) {
        StringBuilder user = new StringBuilder();
        user.append("Task: ").append(task).append("\n");
        if (!prevResults.isEmpty()) {
            user.append("\nPrevious step results:\n");
            for (int i = 0; i < prevResults.size(); i++) {
                user.append("Step ").append(i + 1).append(": ").append(prevResults.get(i)).append("\n");
            }
        }
        user.append("\nCurrent step (").append(n).append("): ").append(step);
        return user.toString();
    }
}
