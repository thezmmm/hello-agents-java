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
 * Tools are used during the Solver phase; the Planner always runs without tools.
 *
 * <p>Construction:
 * <pre>
 *   // minimal — planner and solver share the same LLM
 *   new PlanAndSolveAgent(llm)
 *
 *   // with custom name
 *   new PlanAndSolveAgent("MyAgent", llm)
 *
 *   // advanced — separate LLMs for each phase (e.g. cheaper model for planning)
 *   new PlanAndSolveAgent("MyAgent", plannerLlm, solverLlm)
 * </pre>
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
    public String name() {
        return agentName;
    }

    // --- run / stream --------------------------------------------------------

    @Override
    public String run(String task) {
        List<String> steps = planner.plan(task);
        String response = solver.solve(task, steps, toolRegistry);
        addMessage(Message.user(task));
        addMessage(Message.assistant(response));
        return response;
    }

    @Override
    public void stream(String task, Consumer<String> onToken) {
        List<String> steps = planner.plan(task);
        StringBuilder buf = new StringBuilder();
        solver.stream(task, steps, toolRegistry, token -> {
            buf.append(token);
            onToken.accept(token);
        });
        addMessage(Message.user(task));
        addMessage(Message.assistant(buf.toString()));
    }

}
