package com.helloagents;

import com.helloagents.agents.ReActAgent;
import com.helloagents.agents.SimpleAgent;
import com.helloagents.llm.FunctionCall;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.LlmResponse;
import com.helloagents.llm.Message;
import com.helloagents.tools.CalculatorTool;
import com.helloagents.tools.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for native function calling — uses a hand-rolled mock LlmClient
 * so no real API key is needed.
 */
class NativeFunctionCallingTest {

    // ── FunctionCall ─────────────────────────────────────────────────────────

    @Test
    void functionCallParsesArguments() {
        var call = new FunctionCall("call_1", "calculate", "{\"expression\":\"2+2\"}");
        Map<String, String> args = call.parseArguments();
        assertEquals("2+2", args.get("expression"));
    }

    @Test
    void functionCallHandlesMissingArguments() {
        var call = new FunctionCall("call_1", "calculate", "{}");
        assertTrue(call.parseArguments().isEmpty());
    }

    // ── LlmResponse ──────────────────────────────────────────────────────────

    @Test
    void llmResponseWithContentHasNoToolCalls() {
        var resp = LlmResponse.ofContent("hello");
        assertFalse(resp.hasToolCalls());
        assertEquals("hello", resp.content());
    }

    @Test
    void llmResponseWithToolCallsHasNoContent() {
        var call = new FunctionCall("id1", "calculate", "{}");
        var resp = LlmResponse.ofToolCalls(List.of(call));
        assertTrue(resp.hasToolCalls());
        assertNull(resp.content());
        assertEquals(1, resp.toolCalls().size());
    }

    // ── Message ───────────────────────────────────────────────────────────────

    @Test
    void messageBackwardCompat_twoArg() {
        var msg = Message.user("hello");
        assertEquals("user", msg.role());
        assertEquals("hello", msg.content());
        assertNull(msg.toolCallId());
        assertTrue(msg.toolCalls().isEmpty());
    }

    @Test
    void messageAssistantWithToolCalls() {
        var call = new FunctionCall("id1", "calc", "{}");
        var msg = Message.assistantWithToolCalls(List.of(call));
        assertEquals("assistant", msg.role());
        assertNull(msg.content());
        assertEquals(1, msg.toolCalls().size());
        assertEquals("calc", msg.toolCalls().get(0).name());
    }

    // ── SimpleAgent tool-calling loop ─────────────────────────────────────────

    @Test
    void simpleAgent_oneToolCallThenAnswer() {
        // Round 1: model requests calculate("2+2")
        // Round 2: model returns "The answer is 4"
        var mock = sequentialMock(
                LlmResponse.ofToolCalls(List.of(new FunctionCall("id1", "calculate", "{\"expression\":\"2+2\"}"))),
                LlmResponse.ofContent("The answer is 4")
        );

        var agent = new SimpleAgent(mock);
        agent.addTool(new CalculatorTool());

        String result = agent.run("What is 2+2?");

        assertEquals("The answer is 4", result);

        // history should have user + assistant
        assertEquals(2, agent.getHistory().size());
        assertEquals("user",      agent.getHistory().get(0).role());
        assertEquals("assistant", agent.getHistory().get(1).role());
    }

    @Test
    void simpleAgent_directAnswerNoToolCall() {
        var mock = sequentialMock(LlmResponse.ofContent("The sky is blue."));
        var agent = new SimpleAgent(mock);
        agent.addTool(new CalculatorTool());

        String result = agent.run("Why is the sky blue?");
        assertEquals("The sky is blue.", result);
    }

    @Test
    void simpleAgent_multipleToolCallsInOneRound() {
        // Model calls two tools at once
        var round1 = LlmResponse.ofToolCalls(List.of(
                new FunctionCall("id1", "calculate", "{\"expression\":\"3+4\"}"),
                new FunctionCall("id2", "calculate", "{\"expression\":\"10*2\"}")
        ));
        var round2 = LlmResponse.ofContent("3+4=7 and 10×2=20");
        var mock   = sequentialMock(round1, round2);

        var agent = new SimpleAgent(mock);
        agent.addTool(new CalculatorTool());

        String result = agent.run("Calculate two things");
        assertEquals("3+4=7 and 10×2=20", result);
    }

    // ── ReActAgent tool-calling loop ─────────────────────────────────────────

    @Test
    void reactAgent_callsToolThenFinishes() {
        // Round 1: call calculate
        // Round 2: call finish with the answer
        var round1 = LlmResponse.ofToolCalls(List.of(
                new FunctionCall("id1", "calculate", "{\"expression\":\"7*5\"}")
        ));
        var round2 = LlmResponse.ofToolCalls(List.of(
                new FunctionCall("id2", "finish", "{\"answer\":\"The area is 35.\"}")
        ));
        var mock = sequentialMock(round1, round2);

        var agent = new ReActAgent(mock);
        agent.addTool(new CalculatorTool());

        String result = agent.run("What is 7 times 5?");
        assertEquals("The area is 35.", result);
    }

    @Test
    void reactAgent_directTextAnswerWithoutToolCall() {
        var mock = sequentialMock(LlmResponse.ofContent("42"));
        var agent = new ReActAgent(mock);

        String result = agent.run("What is the answer to everything?");
        assertEquals("42", result);
    }

    @Test
    void reactAgent_maxStepsReturnsGracefully() {
        // Always requests tool calls, never calls finish
        var callForever = LlmResponse.ofToolCalls(List.of(
                new FunctionCall("id", "calculate", "{\"expression\":\"1+1\"}")
        ));
        // Need maxSteps+1 responses in the queue
        var mock = new MockLlmClient() {
            @Override
            public LlmResponse chat(List<Message> messages, List<Tool> tools) {
                return callForever;
            }
        };

        var agent = new ReActAgent(mock, 3);
        agent.addTool(new CalculatorTool());

        String result = agent.run("Loop forever");
        assertEquals("Max steps reached without a final answer.", result);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Creates a mock that returns responses in order on each chat call. */
    private static LlmClient sequentialMock(LlmResponse... responses) {
        Queue<LlmResponse> queue = new ArrayBlockingQueue<>(responses.length);
        for (var r : responses) queue.add(r);
        return new MockLlmClient() {
            @Override
            public LlmResponse chat(List<Message> messages, List<Tool> tools) {
                LlmResponse next = queue.poll();
                assertNotNull(next, "Mock ran out of responses — test setup has too few entries");
                return next;
            }
        };
    }

    /**
     * Base mock — throws on every method so tests fail clearly if unexpected calls happen.
     * Subclasses override only the methods they need.
     * {@code stream(messages, tools, onToken)} delegates to {@code chat(messages, tools)} by default,
     * so tests that override {@code chat} automatically get streaming behaviour too.
     */
    private abstract static class MockLlmClient implements LlmClient {
        @Override
        public String chat(List<Message> messages) {
            throw new UnsupportedOperationException("unexpected chat() call in this test");
        }
        @Override
        public void stream(List<Message> messages, Consumer<String> onToken) {
            throw new UnsupportedOperationException("unexpected stream() call in this test");
        }
    }
}
