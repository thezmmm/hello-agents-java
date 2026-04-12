package com.helloagents.demo;

import com.helloagents.agents.PlanAndSolveAgent;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.CalculatorTool;

/**
 * Demo for PlanAndSolveAgent — covers two scenarios:
 * <ol>
 *   <li>Multi-step reasoning (no tools)</li>
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

    // -------------------------------------------------------------------------

    /** Scenario 1: multi-step reasoning without tools. */
    private static void demoReasoning(OpenAiClient llm) {
        printHeader("1. Multi-step Reasoning");
        var agent = new PlanAndSolveAgent(llm);

        String task = "小明有15个苹果，他给了小红一半，小红又给了小刚3个，小刚现在有多少个苹果？（假设小刚原来没有苹果）";
        solve(agent, task);
    }

    /** Scenario 2: multi-step reasoning with CalculatorTool in the Solver phase. */
    private static void demoToolCalling(OpenAiClient llm) {
        printHeader("2. Multi-step Reasoning + Tool Calling");
        var agent = new PlanAndSolveAgent("MathAgent", llm);
        agent.addTool(new CalculatorTool());

        System.out.println("Tools: " + agent.listTools());

        String task = "一家水果店，周一卖了15个苹果，周二销量是周一的两倍，周三比周二少5个，三天共卖了多少个苹果？";
        solve(agent, task);
    }

    // -------------------------------------------------------------------------

    private static void solve(PlanAndSolveAgent agent, String task) {
        System.out.println("Task : " + task);
        System.out.println();
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
