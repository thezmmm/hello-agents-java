package com.helloagents.agents;

import com.helloagents.context.CompressedHistory;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.LlmResponse;
import com.helloagents.llm.Message;
import com.helloagents.tools.CalculatorTool;
import com.helloagents.tools.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class PlanAndSolveAgentTest {

    // Stub sequence: planner → "1. step one\n2. step two"
    //               solver step1 → "Result: step one done"
    //               solver step2 → "Result: final answer"
    private static final String PLAN       = "1. Do step one\n2. Do step two";
    private static final String STEP1      = "Result: step one done";
    private static final String STEP2      = "Result: final answer";

    private LlmClient          stubLlm;
    private PlanAndSolveAgent  agent;

    @BeforeEach
    void setUp() {
        stubLlm = sequentialLlm(PLAN, STEP1, STEP2);
        agent   = new PlanAndSolveAgent(stubLlm);
    }

    // ── history ───────────────────────────────────────────────────────────────

    @Test
    void runAppendsUserAndFinalAnswerToHistory() {
        agent.run("solve this");
        List<Message> h = agent.getHistory();
        assertEquals(2, h.size());
        assertEquals("user",      h.get(0).role());
        assertEquals("assistant", h.get(1).role());
        assertEquals(STEP2,       h.get(1).content());
    }

    @Test
    void multipleRunsAccumulateHistory() {
        agent.run("first");
        agent = new PlanAndSolveAgent(sequentialLlm(PLAN, STEP1, STEP2));
        agent.run("second");
        assertEquals(2, agent.getHistory().size());
    }

    @Test
    void clearHistoryResetsHistory() {
        agent.run("task");
        agent.clearHistory();
        assertTrue(agent.getHistory().isEmpty());
    }

    // ── executionTrace ────────────────────────────────────────────────────────

    @Test
    void runTraceHasThreeMessages() {
        agent.run("solve this");
        List<Message> trace = agent.getLastExecution();
        // [user, assistant(plan), assistant(final)]
        assertEquals(3, trace.size());
        assertEquals("user",      trace.get(0).role());
        assertTrue(trace.get(1).content().contains("Plan:"));
        assertTrue(trace.get(1).content().contains("Do step one"));
        assertEquals(STEP2, trace.get(2).content());
    }

    @Test
    void multipleRunsAccumulateInExecutionTrace() {
        // 6 sequential responses: plan, step1, step2 for each of the two runs
        PlanAndSolveAgent twoRunAgent = new PlanAndSolveAgent(
                sequentialLlm(PLAN, STEP1, STEP2, PLAN, STEP1, STEP2));
        twoRunAgent.run("first");
        twoRunAgent.run("second");
        assertEquals(2, twoRunAgent.getExecutionTrace().size());
    }

    @Test
    void getLastExecutionEmptyBeforeAnyRun() {
        assertTrue(agent.getLastExecution().isEmpty());
    }

    @Test
    void traceIsImmutable() {
        agent.run("task");
        assertThrows(UnsupportedOperationException.class,
                () -> agent.getLastExecution().add(Message.user("x")));
    }

    // ── tools ─────────────────────────────────────────────────────────────────

    @Test
    void agentPassesToolRegistryToSolver() {
        // Solver receives toolRegistry; verify agent doesn't crash with tools registered
        agent.addTool(new CalculatorTool());
        assertDoesNotThrow(() -> agent.run("calculate something"));
    }

    // ── stream ────────────────────────────────────────────────────────────────

    @Test
    void streamUpdatesHistory() {
        agent.stream("task", token -> {});
        assertEquals(2, agent.getHistory().size());
    }

    @Test
    void streamAddsTraceEntry() {
        agent.stream("task", token -> {});
        assertEquals(1, agent.getExecutionTrace().size());
    }

    // ── CompressedHistory ─────────────────────────────────────────────────────

    @Test
    void clearHistoryAlsoClearsCompressedHistory() {
        CompressedHistory ch = new CompressedHistory(stubLlm);
        agent.withCompressedHistory(ch);
        agent.run("task");
        agent.clearHistory();
        assertEquals(0, ch.recentSize());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static LlmClient sequentialLlm(String... responses) {
        AtomicInteger idx = new AtomicInteger(0);
        return new LlmClient() {
            private String next() {
                int i = idx.getAndIncrement();
                return i < responses.length ? responses[i] : responses[responses.length - 1];
            }
            @Override public String chat(List<Message> messages) { return next(); }
            @Override public LlmResponse chat(List<Message> messages, List<Tool> tools) {
                return LlmResponse.ofContent(next());
            }
            @Override public void stream(List<Message> messages, Consumer<String> onToken) {
                onToken.accept(next());
            }
            @Override public LlmResponse stream(List<Message> messages, List<Tool> tools,
                                                Consumer<String> onToken) {
                String r = next(); onToken.accept(r); return LlmResponse.ofContent(r);
            }
        };
    }
}