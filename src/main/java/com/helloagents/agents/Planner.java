package com.helloagents.agents;

import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decomposes a task into a numbered plan.
 *
 * <p>Model output format:
 * <pre>
 * 1. First sub-step
 * 2. Second sub-step
 * </pre>
 *
 * <p>{@link #plan(String)} parses that output and returns each step as an element of a
 * {@code List<String>}, with the leading number stripped:
 * <pre>
 * ["First sub-step", "Second sub-step"]
 * </pre>
 */
public class Planner {

    private static final String SYSTEM = """
            You are a planning assistant. Break the user's task into a numbered list of sub-steps.

            Each step must satisfy ALL of the following constraints:
            - Independent: the step is a self-contained, executable action with a clear, verifiable outcome.
            - Ordered: each step may only depend on the results of steps that come before it; no circular dependencies.
            - Non-redundant: no two steps cover the same work.
            - Atomic: the step describes exactly one action — do not bundle multiple actions into one step.

            Output format rules:
            - Output ONLY the numbered list, no introduction or closing remarks.
            - Each line must follow the exact format:  <number>. <step description>
            - Use plain text only, no markdown headings or bullet symbols.

            Example output:
            1. Identify the known quantities given in the problem.
            2. Calculate the result of the first operation using the values from step 1.
            3. Apply the result of step 2 to compute the final answer.
            """;

    /** Matches a leading number + period, e.g. "1. " or "12. " */
    private static final Pattern STEP_PREFIX = Pattern.compile("^\\d+\\.\\s*");

    private final LlmClient llm;

    public Planner(LlmClient llm) {
        this.llm = llm;
    }

    /**
     * Produces a plan for the given task.
     *
     * @param task natural-language task description
     * @return ordered list of sub-step descriptions (leading numbers stripped)
     */
    public List<String> plan(String task) {
        String raw = llm.chat(List.of(
                Message.system(SYSTEM),
                Message.user(task)));
        return parse(raw);
    }

    private List<String> parse(String raw) {
        List<String> steps = Arrays.stream(raw.split("\\R"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .filter(line -> STEP_PREFIX.matcher(line).find())
                .map(line -> STEP_PREFIX.matcher(line).replaceFirst(""))
                .collect(Collectors.toList());
        if (steps.isEmpty()) {
            throw new PlanningException(
                    "Planner returned no valid steps. Model response was: " + raw);
        }
        return steps;
    }
}