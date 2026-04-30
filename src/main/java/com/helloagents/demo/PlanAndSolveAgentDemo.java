package com.helloagents.demo;

import com.helloagents.agents.PlanAndSolveAgent;
import com.helloagents.llm.Message;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.CalculatorTool;

import java.util.List;

/**
 * Demo for PlanAndSolveAgent — covers two scenarios:
 * <ol>
 *   <li>Multi-step reasoning without tools + execution trace</li>
 *   <li>Multi-step reasoning with CalculatorTool</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.PlanAndSolveAgentDemo"
 * </pre>
 */
public class PlanAndSolveAgentDemo {

    public static void main(String[] args) {
        var llm = OpenAiClient.fromEnv();

        demoReasoning(llm);
        demoToolCalling(llm);
    }

    // ── Scenario 1: multi-step reasoning + trace ──────────────────────────────

    private static void demoReasoning(OpenAiClient llm) {
        printHeader("1. Multi-step Reasoning + Execution Trace");

        var agent = new PlanAndSolveAgent(llm);
        String task = "小明有15个苹果，他给了小红一半，小红又给了小刚3个，小刚现在有多少个苹果？（假设小刚原来没有苹果）";

        System.out.println("Task: " + task);
        System.out.println();
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");

        // trace: [user, assistant(plan), assistant(final)]
        System.out.println("── Execution Trace ──");
        List<Message> trace = agent.getLastExecution();
        String[] labels = {"user", "plan", "final"};
        for (int i = 0; i < trace.size(); i++) {
            String label   = i < labels.length ? labels[i] : trace.get(i).role();
            String content = trace.get(i).content();
            String preview = content != null && content.length() > 100
                    ? content.substring(0, 100) + "..." : content;
            System.out.printf("[%d] %-6s → %s%n", i, label, preview);
        }
        System.out.println();
    }

    // ── Scenario 2: multi-step reasoning with tools ───────────────────────────

    private static void demoToolCalling(OpenAiClient llm) {
        printHeader("2. Multi-step Reasoning + Tool Calling");

        var agent = new PlanAndSolveAgent("MathAgent", llm);
        agent.addTool(new CalculatorTool());
        System.out.println("Tools: " + agent.listTools());

        String task = "一家水果店，周一卖了15个苹果，周二销量是周一的两倍，周三比周二少5个，三天共卖了多少个苹果？";
        System.out.println("Task: " + task);
        System.out.println();
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void printHeader(String title) {
        System.out.println("=".repeat(55));
        System.out.println(" " + title);
        System.out.println("=".repeat(55));
    }
}