package com.helloagents.demo;

import com.helloagents.agents.SimpleAgent;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.CalculatorTool;

/**
 * Demo for SimpleAgent — covers three scenarios:
 * <ol>
 *   <li>Basic Q&A (no tools)</li>
 *   <li>Multi-turn conversation using history</li>
 *   <li>Tool-calling with CalculatorTool</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.SimpleAgentDemo"
 * </pre>
 */
public class SimpleAgentDemo {

    public static void main(String[] args) {
        var llm = OpenAiClient.fromEnv();

//        demoBasic(llm);
//        demoHistory(llm);
        demoToolCalling(llm);
    }

    // -------------------------------------------------------------------------

    /** Scenario 1: single-turn Q&A, no tools. */
    private static void demoBasic(OpenAiClient llm) {
        printHeader("1. Basic Q&A");
        var agent = new SimpleAgent(llm);

        String task = "用一句话解释什么是智能体（Agent）。";
        printTask(task);
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");
    }

    /** Scenario 2: multi-turn conversation — history is preserved across calls. */
    private static void demoHistory(OpenAiClient llm) {
        printHeader("2. Multi-turn Conversation");
        var agent = new SimpleAgent(llm);

        ask(agent, "我叫小明，我在学习 Java。");
        ask(agent, "我之前提到我在学什么？");

        System.out.println("History size: " + agent.getHistory().size() + " messages\n");
    }

    /** Scenario 3: agent equipped with CalculatorTool, triggered via [TOOL_CALL:...]. */
    private static void demoToolCalling(OpenAiClient llm) {
        printHeader("3. Tool Calling (Calculator)");
        var agent = new SimpleAgent("MathAgent", llm, null, null);
        agent.addTool(new CalculatorTool());

        System.out.println("Tools: " + agent.listTools());

        String task = "请计算 (123 + 456) * 7 - 89 的结果。";
        printTask(task);
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");

        // remove tool and verify
        agent.removeTool("calculate");
        System.out.println("After remove — hasTools: " + agent.hasTools());
        System.out.println();
    }

    // -------------------------------------------------------------------------

    private static void ask(SimpleAgent agent, String task) {
        printTask(task);
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

    private static void printTask(String task) {
        System.out.println("Task : " + task);
        System.out.print("Reply: ");
    }
}
