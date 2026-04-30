package com.helloagents.agents;

import com.helloagents.context.CompressedHistory;
import com.helloagents.context.SystemPromptBuilder;
import com.helloagents.context.ContextConfig;
import com.helloagents.llm.FunctionCall;
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

class SimpleAgentTest {

    private LlmClient stubLlm;
    private SimpleAgent agent;

    @BeforeEach
    void setUp() {
        stubLlm = fixedLlm("stub answer");
        agent   = new SimpleAgent(stubLlm);
    }

    // ── history ───────────────────────────────────────────────────────────────

    @Test
    void runAppendsUserAndAssistantToHistory() {
        agent.run("hello");
        List<Message> h = agent.getHistory();
        assertEquals(2, h.size());
        assertEquals("user",      h.get(0).role());
        assertEquals("hello",     h.get(0).content());
        assertEquals("assistant", h.get(1).role());
        assertEquals("stub answer", h.get(1).content());
    }

    @Test
    void multipleRunsAccumulateHistory() {
        agent.run("first");
        agent.run("second");
        assertEquals(4, agent.getHistory().size());
    }

    @Test
    void clearHistoryResetsHistory() {
        agent.run("hello");
        agent.clearHistory();
        assertTrue(agent.getHistory().isEmpty());
    }

    // ── executionTrace (no tools) ─────────────────────────────────────────────

    @Test
    void runWithoutToolsTraceHasUserAndAssistant() {
        agent.run("hello");
        List<Message> trace = agent.getLastExecution();
        assertEquals(2, trace.size());
        assertEquals("user",        trace.get(0).role());
        assertEquals("assistant",   trace.get(1).role());
    }

    @Test
    void multipleRunsAccumulateInExecutionTrace() {
        agent.run("first");
        agent.run("second");
        assertEquals(2, agent.getExecutionTrace().size());
    }

    @Test
    void getLastExecutionReturnsLatestRun() {
        agent.run("first");
        agent = new SimpleAgent(fixedLlm("second answer"));
        agent.run("second");
        assertEquals("second answer", agent.getLastExecution().get(1).content());
    }

    @Test
    void getLastExecutionReturnsEmptyListBeforeAnyRun() {
        assertTrue(agent.getLastExecution().isEmpty());
    }

    @Test
    void clearExecutionTraceClearsAllTraces() {
        agent.run("hello");
        agent.clearExecutionTrace();
        assertTrue(agent.getExecutionTrace().isEmpty());
    }

    // ── executionTrace (with tools) ───────────────────────────────────────────

    @Test
    void runWithToolsTraceIncludesToolExchange() {
        // first chat(messages, tools) → tool call; second chat(messages) → final answer
        AtomicInteger callCount = new AtomicInteger(0);
        LlmClient toolLlm = new LlmClient() {
            @Override
            public String chat(List<Message> messages) {
                return "The answer is 4";
            }
            @Override
            public LlmResponse chat(List<Message> messages, List<Tool> tools) {
                if (callCount.getAndIncrement() == 0) {
                    return LlmResponse.ofToolCalls(
                            List.of(new FunctionCall("id1", "calculate", "{\"expression\":\"2+2\"}")));
                }
                return LlmResponse.ofContent("The answer is 4");
            }
        };

        SimpleAgent toolAgent = new SimpleAgent(toolLlm);
        toolAgent.addTool(new CalculatorTool());
        toolAgent.run("what is 2+2?");

        List<Message> trace = toolAgent.getLastExecution();
        // user → assistantWithToolCalls → tool → assistant
        assertEquals(4, trace.size());
        assertEquals("user",      trace.get(0).role());
        assertEquals("assistant", trace.get(1).role()); // assistantWithToolCalls
        assertFalse(trace.get(1).toolCalls().isEmpty());
        assertEquals("tool",      trace.get(2).role());
        assertEquals("assistant", trace.get(3).role());
        assertTrue(trace.get(3).toolCalls().isEmpty());  // plain assistant (final answer)
    }

    @Test
    void traceIsImmutable() {
        agent.run("hello");
        List<Message> trace = agent.getLastExecution();
        assertThrows(UnsupportedOperationException.class, () -> trace.add(Message.user("x")));
    }

    // ── CompressedHistory integration ─────────────────────────────────────────

    @Test
    void clearHistoryAlsoClearsCompressedHistory() {
        CompressedHistory ch = new CompressedHistory(stubLlm);
        agent.withCompressedHistory(ch);
        agent.run("hello");
        agent.clearHistory();
        assertFalse(ch.hasSummary());
        assertEquals(0, ch.recentSize());
    }

    @Test
    void compressedHistoryReceivesHistoryOnBuildMessages() {
        CompressedHistory ch = new CompressedHistory(stubLlm);
        agent.withCompressedHistory(ch);

        agent.run("turn one");
        // After first run history has [user, assistant]; second run should sync them
        agent.run("turn two");

        // CompressedHistory should have consumed the history written after turn one
        assertTrue(ch.recentSize() > 0);
    }

    // ── SystemPromptBuilder integration ──────────────────────────────────────

    @Test
    void systemPromptBuilderOutputAppearsInSystemMessage() {
        // Capture the messages list seen by the LLM
        List<Message>[] captured = new List[1];
        LlmClient capturingLlm = messages -> {
            captured[0] = messages;
            return "ok";
        };

        SystemPromptBuilder spb = new SystemPromptBuilder(ContextConfig.defaults());
        SimpleAgent spbAgent = new SimpleAgent(capturingLlm);
        spbAgent.withSystemPromptBuilder(spb);
        spbAgent.run("test query");

        assertNotNull(captured[0]);
        Message system = captured[0].get(0);
        assertEquals("system", system.role());
        // SystemPromptBuilder always adds [Output] section
        assertTrue(system.content().contains("[Output]"));
    }

    // ── stream ────────────────────────────────────────────────────────────────

    @Test
    void streamEmitsTokensAndUpdatesHistory() {
        StringBuilder received = new StringBuilder();
        agent.stream("hello", received::append);

        assertEquals("stub answer", received.toString());
        assertEquals(2, agent.getHistory().size());
    }

    @Test
    void streamWithoutToolsAddsTraceEntry() {
        agent.stream("hello", token -> {});
        assertEquals(1, agent.getExecutionTrace().size());
        List<Message> trace = agent.getLastExecution();
        assertEquals("user",      trace.get(0).role());
        assertEquals("assistant", trace.get(1).role());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static LlmClient fixedLlm(String response) {
        return new LlmClient() {
            @Override public String chat(List<Message> messages) { return response; }
            @Override public LlmResponse chat(List<Message> messages, List<Tool> tools) {
                return LlmResponse.ofContent(response);
            }
            @Override public void stream(List<Message> messages, Consumer<String> onToken) {
                onToken.accept(response);
            }
        };
    }
}