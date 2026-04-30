package com.helloagents.agents;

import com.helloagents.core.AbstractAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Plan-and-Solve agent.
 *
 * <p>Two-phase approach:
 * <ol>
 *   <li>{@link Planner} — decomposes the task into ordered sub-steps (no tools)</li>
 *   <li>{@link Solver}  — executes each step, calling tools where needed</li>
 * </ol>
 *
 * <p>{@link com.helloagents.context.CompressedHistory} and
 * {@link com.helloagents.context.SystemPromptBuilder} are held at this layer.
 * The Solver receives the context-aware system message when building its step prompts.
 */
public class PlanAndSolveAgent extends AbstractAgent {

    private static final String DEFAULT_NAME = "PlanAndSolveAgent";

    private final String  agentName;
    private final Planner planner;
    private final Solver  solver;

    // --- constructors --------------------------------------------------------

    public PlanAndSolveAgent(LlmClient llm) {
        this(DEFAULT_NAME, llm, llm);
    }

    public PlanAndSolveAgent(String name, LlmClient llm) {
        this(name, llm, llm);
    }

    public PlanAndSolveAgent(String name, LlmClient plannerLlm, LlmClient solverLlm) {
        this.agentName = (name != null && !name.isBlank()) ? name : DEFAULT_NAME;
        this.planner   = new Planner(plannerLlm);
        this.solver    = new Solver(solverLlm);
    }

    @Override
    public String name() { return agentName; }

    // --- run -----------------------------------------------------------------

    @Override
    public String run(String task) {
        syncHistory();

        List<Message> trace = new ArrayList<>();
        trace.add(Message.user(task));

        List<String> steps    = planner.plan(task);
        String       response = solver.solve(task, steps, toolRegistry);

        trace.add(Message.assistant(formatPlan(steps)));
        trace.add(Message.assistant(response));

        addMessage(Message.user(task));
        addMessage(Message.assistant(response));
        addExecutionTrace(trace);
        return response;
    }

    // --- stream --------------------------------------------------------------

    @Override
    public void stream(String task, Consumer<String> onToken) {
        syncHistory();

        List<Message> trace = new ArrayList<>();
        trace.add(Message.user(task));

        List<String>  steps = planner.plan(task);
        StringBuilder buf   = new StringBuilder();
        solver.stream(task, steps, toolRegistry, token -> {
            buf.append(token);
            onToken.accept(token);
        });

        trace.add(Message.assistant(formatPlan(steps)));
        trace.add(Message.assistant(buf.toString()));

        addMessage(Message.user(task));
        addMessage(Message.assistant(buf.toString()));
        addExecutionTrace(trace);
    }

    // --- helpers -------------------------------------------------------------

    /** Syncs CompressedHistory before each run so it stays current. */
    private void syncHistory() {
        if (compressedHistory != null) {
            compressedHistory.sync(getHistory());
        }
    }

    private static String formatPlan(List<String> steps) {
        StringBuilder sb = new StringBuilder("Plan:\n");
        for (int i = 0; i < steps.size(); i++) {
            sb.append(i + 1).append(". ").append(steps.get(i)).append('\n');
        }
        return sb.toString();
    }
}