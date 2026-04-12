package com.helloagents.agents;

import com.helloagents.core.AbstractAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;

import java.util.List;
import java.util.function.Consumer;

/**
 * Plan-and-Solve agent.
 *
 * <p>Two-phase approach from <em>Plan-and-Solve Prompting</em> (Wang et al., 2023):
 * <ol>
 *   <li>{@link Planner} — decomposes the task into a numbered list of sub-steps</li>
 *   <li>{@link Solver}  — works through each sub-step and produces the final answer</li>
 * </ol>
 *
 * <p>Typical construction:
 * <pre>
 *   // convenience: both components share the same LlmClient
 *   PlanAndSolveAgent agent = PlanAndSolveAgent.of(llm);
 *
 *   // advanced: inject different clients (e.g. a cheaper model for planning)
 *   PlanAndSolveAgent agent = new PlanAndSolveAgent(new Planner(fastLlm), new Solver(smartLlm));
 * </pre>
 */
public class PlanAndSolveAgent extends AbstractAgent {

    private final Planner planner;
    private final Solver solver;

    public PlanAndSolveAgent(Planner planner, Solver solver) {
        this.planner = planner;
        this.solver = solver;
    }

    /** Factory method: both components share the same {@link LlmClient}. */
    public static PlanAndSolveAgent of(LlmClient llm) {
        return new PlanAndSolveAgent(new Planner(llm), new Solver(llm));
    }

    @Override
    public String run(String task) {
        List<String> steps = planner.plan(task);
        String response = solver.solve(task, steps);
        addMessage(Message.user(task));
        addMessage(Message.assistant(response));
        return response;
    }

    @Override
    public void stream(String task, Consumer<String> onToken) {
        List<String> steps = planner.plan(task);
        StringBuilder buf = new StringBuilder();
        solver.stream(task, steps, token -> {
            buf.append(token);
            onToken.accept(token);
        });
        addMessage(Message.user(task));
        addMessage(Message.assistant(buf.toString()));
    }
}