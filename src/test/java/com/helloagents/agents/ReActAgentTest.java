package com.helloagents.agents;

import com.helloagents.context.CompressedHistory;
import com.helloagents.context.ContextConfig;
import com.helloagents.context.SystemPromptBuilder;
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

class ReActAgentTest {

    private LlmClient   finishLlm;  // always returns a finish tool call
    private ReActAgent  agent;

    @BeforeEach
    void setUp() {
        finishLlm = finishLlm("stub answer");
        agent     = new ReActAgent(finishLlm);
    }

    // ── history ───────────────────────────────────────────────────────────────

    @Test
    void runAppendsUserAndAssistantToHistory() {
        agent.run("hello");
        List<Message> h = agent.getHistory();
        assertEquals(2, h.size());
        assertEquals("user",        h.get(0).role());
        assertEquals("assistant",   h.get(1).role());
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

    // ── executionTrace — finish tool path ─────────────────────────────────────

    @Test
    void runWithFinishToolTraceHasFourMessages() {
        agent.run("hello");
        // [user, assistantWithToolCalls(finish), tool(answer), assistant(answer)]
        List<Message> trace = agent.getLastExecution();
        assertEquals(4, trace.size());
        assertEquals("user",      trace.get(0).role());
        assertFalse(trace.get(1).toolCalls().isEmpty());      // assistantWithToolCalls
        assertEquals("tool",      trace.get(2).role());
        assertEquals("assistant", trace.get(3).role());
        assertEquals("stub answer", trace.get(3).content());
    }

    @Test
    void runWithDirectAnswerTraceHasTwoMessages() {
        // model answers directly without tool calls
        ReActAgent directAgent = new ReActAgent(directAnswerLlm("direct"));
        directAgent.run("hello");

        List<Message> trace = directAgent.getLastExecution();
        assertEquals(2, trace.size());
        assertEquals("user",      trace.get(0).role());
        assertEquals("assistant", trace.get(1).role());
    }

    @Test
    void runWithIntermediateToolThenFinishTraceIsComplete() {
        // round 1: calculate tool; round 2: finish
        AtomicInteger call = new AtomicInteger(0);
        ReActAgent toolAgent = new ReActAgent(new LlmClient() {
            @Override public String chat(List<Message> messages) { return ""; }
            @Override public LlmResponse chat(List<Message> messages, List<Tool> tools) {
                return call.getAndIncrement() == 0
                        ? LlmResponse.ofToolCalls(List.of(
                                new FunctionCall("c1", "calculate", "{\"expression\":\"2+2\"}")))
                        : LlmResponse.ofToolCalls(List.of(
                                new FunctionCall("f1", "finish", "{\"answer\":\"4\"}")));
            }
        });
        toolAgent.addTool(new CalculatorTool());
        toolAgent.run("what is 2+2?");

        List<Message> trace = toolAgent.getLastExecution();
        // [user, assistantWithToolCalls(calc), tool(calc_result),
        //        assistantWithToolCalls(finish), tool(finish_result), assistant(final)]
        assertEquals(6, trace.size());
        assertEquals("user",      trace.get(0).role());
        assertEquals("tool",      trace.get(2).role());  // calc result
        assertEquals("tool",      trace.get(4).role());  // finish result
        assertEquals("assistant", trace.get(5).role());
        assertEquals("4",         trace.get(5).content());
    }

    @Test
    void multipleRunsAccumulateInExecutionTrace() {
        agent.run("first");
        agent.run("second");
        assertEquals(2, agent.getExecutionTrace().size());
    }

    @Test
    void getLastExecutionEmptyBeforeAnyRun() {
        assertTrue(agent.getLastExecution().isEmpty());
    }

    @Test
    void traceIsImmutable() {
        agent.run("hello");
        assertThrows(UnsupportedOperationException.class,
                () -> agent.getLastExecution().add(Message.user("x")));
    }

    // ── CompressedHistory & SystemPromptBuilder ───────────────────────────────

    @Test
    void clearHistoryAlsoClearsCompressedHistory() {
        CompressedHistory ch = new CompressedHistory(finishLlm);
        agent.withCompressedHistory(ch);
        agent.run("hello");
        agent.clearHistory();
        assertEquals(0, ch.recentSize());
    }

    @Test
    void systemPromptBuilderOutputAppearsInSystemMessage() {
        List<Message>[] captured = new List[1];
        LlmClient capturingLlm = new LlmClient() {
            @Override public String chat(List<Message> msgs) { return ""; }
            @Override public LlmResponse chat(List<Message> msgs, List<Tool> tools) {
                captured[0] = msgs;
                return LlmResponse.ofToolCalls(
                        List.of(new FunctionCall("f1", "finish", "{\"answer\":\"ok\"}")));
            }
        };

        ReActAgent spbAgent = new ReActAgent(capturingLlm);
        spbAgent.withSystemPromptBuilder(new SystemPromptBuilder(ContextConfig.defaults()));
        spbAgent.run("test");

        assertNotNull(captured[0]);
        assertTrue(captured[0].get(0).content().contains("[Output]"));
    }

    // ── stream ────────────────────────────────────────────────────────────────

    @Test
    void streamAddsTraceEntry() {
        agent.stream("hello", token -> {});
        assertEquals(1, agent.getExecutionTrace().size());
    }

    @Test
    void streamUpdatesHistory() {
        agent.stream("hello", token -> {});
        assertEquals(2, agent.getHistory().size());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Stub that always responds with a finish tool call carrying {@code answer}. */
    private static LlmClient finishLlm(String answer) {
        return new LlmClient() {
            @Override public String chat(List<Message> messages) { return answer; }
            @Override public LlmResponse chat(List<Message> messages, List<Tool> tools) {
                return LlmResponse.ofToolCalls(
                        List.of(new FunctionCall("f1", "finish",
                                "{\"answer\":\"" + answer + "\"}")));
            }
            @Override public void stream(List<Message> messages, Consumer<String> onToken) {
                onToken.accept(answer);
            }
            @Override public LlmResponse stream(List<Message> messages, List<Tool> tools,
                                                Consumer<String> onToken) {
                return LlmResponse.ofToolCalls(
                        List.of(new FunctionCall("f1", "finish",
                                "{\"answer\":\"" + answer + "\"}")));
            }
        };
    }

    /** Stub that always returns a plain text answer (no tool calls). */
    private static LlmClient directAnswerLlm(String answer) {
        return new LlmClient() {
            @Override public String chat(List<Message> messages) { return answer; }
            @Override public LlmResponse chat(List<Message> messages, List<Tool> tools) {
                return LlmResponse.ofContent(answer);
            }
            @Override public void stream(List<Message> messages, Consumer<String> onToken) {
                onToken.accept(answer);
            }
            @Override public LlmResponse stream(List<Message> messages, List<Tool> tools,
                                                Consumer<String> onToken) {
                return LlmResponse.ofContent(answer);
            }
        };
    }
}