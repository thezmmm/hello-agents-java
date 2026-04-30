package com.helloagents.demo;

import com.helloagents.agents.ReflectionAgent;
import com.helloagents.llm.Message;
import com.helloagents.llm.OpenAiClient;

import java.util.List;

/**
 * Demo for ReflectionAgent — covers two scenarios:
 * <ol>
 *   <li>Single task showing all three phases + execution trace</li>
 *   <li>Multi-turn conversation with history</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.ReflectionAgentDemo"
 * </pre>
 */
public class ReflectionAgentDemo {

    public static void main(String[] args) {
        var llm = OpenAiClient.fromEnv();

        demoThreePhases(llm);
        demoMultiTurn(llm);
    }

    // ── Scenario 1: three-phase streaming + trace ─────────────────────────────

    private static void demoThreePhases(OpenAiClient llm) {
        printHeader("1. Generate → Reflect → Refine");

        var agent = new ReflectionAgent(llm);
        String task = "Explain the difference between a process and a thread in operating systems.";

        System.out.println("Task: " + task);
        System.out.println();
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");

        // trace: [user, assistant(draft), assistant(critique), assistant(final)]
        System.out.println("── Execution Trace ──");
        List<Message> trace = agent.getLastExecution();
        String[] labels = {"user", "draft", "critique", "final"};
        for (int i = 0; i < trace.size(); i++) {
            String label   = i < labels.length ? labels[i] : trace.get(i).role();
            String content = trace.get(i).content();
            String preview = content != null && content.length() > 80
                    ? content.substring(0, 80) + "..." : content;
            System.out.printf("[%d] %-9s → %s%n", i, label, preview);
        }
        System.out.println();
    }

    // ── Scenario 2: multi-turn conversation ───────────────────────────────────

    private static void demoMultiTurn(OpenAiClient llm) {
        printHeader("2. Multi-turn Conversation");

        var agent = new ReflectionAgent(llm);

        ask(agent, "What is a deadlock in concurrent programming?");
        ask(agent, "How can we prevent the problem you just described?");

        System.out.println("History : " + agent.getHistory().size() + " messages");
        System.out.println("Runs    : " + agent.getExecutionTrace().size() + " traces");
        System.out.println();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void ask(ReflectionAgent agent, String task) {
        System.out.println("Task: " + task);
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");
    }

    private static void printHeader(String title) {
        System.out.println("=".repeat(55));
        System.out.println(" " + title);
        System.out.println("=".repeat(55));
    }
}