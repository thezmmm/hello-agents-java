package com.helloagents.demo;

import com.helloagents.agents.ReActAgent;
import com.helloagents.llm.Message;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.CalculatorTool;

import java.util.List;

/**
 * Demo for ReActAgent — covers two scenarios:
 * <ol>
 *   <li>Single task with tool calling + execution trace inspection</li>
 *   <li>Multi-turn conversation across several runs</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.ReActAgentDemo"
 * </pre>
 */
public class ReActAgentDemo {

    public static void main(String[] args) {
        var llm = OpenAiClient.fromEnv();

        demoToolCalling(llm);
        demoMultiTurn(llm);
    }

    // ── Scenario 1: tool calling + trace ──────────────────────────────────────

    private static void demoToolCalling(OpenAiClient llm) {
        printHeader("1. Tool Calling + Execution Trace");

        var agent = new ReActAgent("MathAgent", llm, null, 5);
        agent.addTool(new CalculatorTool());

        String task = "If a rectangle has width 7 and height 5, what is its area? Then multiply that by 3.";
        System.out.println("Task : " + task);
        System.out.print("Reply: ");
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");

        // inspect execution trace
        System.out.println("── Execution Trace ──");
        List<Message> trace = agent.getLastExecution();
        for (int i = 0; i < trace.size(); i++) {
            Message m = trace.get(i);
            if (!m.toolCalls().isEmpty()) {
                System.out.printf("[%d] assistant → tool_calls: %s%n", i,
                        m.toolCalls().stream()
                                .map(c -> c.name() + "(" + c.arguments() + ")")
                                .toList());
            } else if ("tool".equals(m.role())) {
                System.out.printf("[%d] tool      → %s%n", i, m.content());
            } else {
                String preview = m.content() != null && m.content().length() > 70
                        ? m.content().substring(0, 70) + "..."
                        : m.content();
                System.out.printf("[%d] %-10s → %s%n", i, m.role(), preview);
            }
        }
        System.out.println();
    }

    // ── Scenario 2: multi-turn conversation ───────────────────────────────────

    private static void demoMultiTurn(OpenAiClient llm) {
        printHeader("2. Multi-turn Conversation");

        var agent = new ReActAgent(llm);
        agent.addTool(new CalculatorTool());

        ask(agent, "What is 15 * 8?");
        ask(agent, "Now divide that result by 4.");
        ask(agent, "What calculations have we done so far?");

        System.out.println("History : " + agent.getHistory().size() + " messages");
        System.out.println("Runs    : " + agent.getExecutionTrace().size() + " traces");
        System.out.println();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void ask(ReActAgent agent, String task) {
        System.out.println("Task : " + task);
        System.out.print("Reply: ");
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println();
    }

    private static void printHeader(String title) {
        System.out.println("=".repeat(55));
        System.out.println(" " + title);
        System.out.println("=".repeat(55));
    }
}