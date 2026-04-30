package com.helloagents.agents;

import com.helloagents.llm.LlmClient;
import com.helloagents.llm.LlmResponse;
import com.helloagents.llm.Message;
import com.helloagents.tools.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionAgentTest {

    // Sequential stub: generate → draft, reflect → critique, refine → final
    private LlmClient      sequentialLlm;
    private ReflectionAgent agent;

    @BeforeEach
    void setUp() {
        sequentialLlm = sequentialLlm("draft answer", "critique text", "refined answer");
        agent         = new ReflectionAgent(sequentialLlm);
    }

    // ── history ───────────────────────────────────────────────────────────────

    @Test
    void runAppendsOnlyFinalAnswerToHistory() {
        agent.run("explain X");
        List<Message> h = agent.getHistory();
        assertEquals(2, h.size());
        assertEquals("user",            h.get(0).role());
        assertEquals("refined answer",  h.get(1).content());
    }

    @Test
    void multipleRunsAccumulateHistory() {
        agent.run("first");
        agent = new ReflectionAgent(
                sequentialLlm("d2", "c2", "r2"));
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
    void runTraceHasFourMessages() {
        agent.run("explain X");
        List<Message> trace = agent.getLastExecution();
        // [user, assistant(draft), assistant(critique), assistant(final)]
        assertEquals(4, trace.size());
        assertEquals("user",           trace.get(0).role());
        assertEquals("draft answer",   trace.get(1).content());
        assertEquals("critique text",  trace.get(2).content());
        assertEquals("refined answer", trace.get(3).content());
    }

    @Test
    void multipleRunsAccumulateInExecutionTrace() {
        ReflectionAgent twoRunAgent = new ReflectionAgent(
                sequentialLlm("d1", "c1", "r1", "d2", "c2", "r2"));
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

    // ── stream ────────────────────────────────────────────────────────────────

    @Test
    void streamUpdatesHistoryWithFinalAnswer() {
        agent.stream("explain X", token -> {});
        assertEquals(2, agent.getHistory().size());
        assertEquals("refined answer", agent.getHistory().get(1).content());
    }

    @Test
    void streamAddsTraceEntry() {
        agent.stream("explain X", token -> {});
        assertFalse(agent.getExecutionTrace().isEmpty());
    }

    @Test
    void streamEmitsAllPhaseTokens() {
        StringBuilder received = new StringBuilder();
        agent.stream("explain X", received::append);
        String output = received.toString();
        assertTrue(output.contains("Draft"),    "should emit draft header");
        assertTrue(output.contains("Critique"), "should emit critique header");
        assertTrue(output.contains("Final"),    "should emit final header");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns {@code responses[0]} on first chat(), {@code responses[1]} on second, etc.
     * Streams the same response via onToken.
     */
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