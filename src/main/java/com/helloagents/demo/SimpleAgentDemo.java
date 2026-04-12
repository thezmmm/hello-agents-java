package com.helloagents.demo;

import com.helloagents.agents.SimpleAgent;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.CalculatorTool;
import com.helloagents.tools.ToolParameter;
import com.helloagents.tools.ToolRegistry;

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
//        demoToolCalling(llm);
        demoFunctionRegistration(llm);
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

    /** Scenario 4: register plain functions (lambda) as tools without implementing Tool. */
    private static void demoFunctionRegistration(OpenAiClient llm) {
        printHeader("4. Function Registration");

        // register via lambda — no Tool class needed
        var registry = new ToolRegistry()
                .register("uppercase", "Converts text to uppercase",
                        String::toUpperCase)
                .register("word_count", "Counts the number of words in the given text",
                        ToolParameter.of(ToolParameter.Param.required("text", "Text to count words in", "string")),
                        input -> String.valueOf(input.trim().split("\\s+").length))
                .register("reverse", "Reverses the characters in a string",
                        input -> new StringBuilder(input).reverse().toString());

        var agent = new SimpleAgent("FunctionAgent", llm, null, registry);
        System.out.println("Tools: " + agent.listTools());

        String task = "请把 'hello world' 转换为大写，然后统计 'The quick brown fox jumps over the lazy dog' 的单词数量。";
        printTask(task);
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");
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
