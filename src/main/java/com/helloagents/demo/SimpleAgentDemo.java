package com.helloagents.demo;

import com.helloagents.agents.SimpleAgent;
import com.helloagents.context.CompressedHistory;
import com.helloagents.context.ContextConfig;
import com.helloagents.context.SystemPromptBuilder;
import com.helloagents.llm.Message;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.CalculatorTool;
import com.helloagents.tools.ToolParameter;
import com.helloagents.tools.ToolRegistry;

import java.util.List;

/**
 * Demo for SimpleAgent — covers five scenarios:
 * <ol>
 *   <li>Basic Q&A (no tools)</li>
 *   <li>Multi-turn conversation with history</li>
 *   <li>Tool-calling with CalculatorTool + execution trace inspection</li>
 *   <li>CompressedHistory for long conversations</li>
 *   <li>Function registration via lambda</li>
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

        demoBasic(llm);
        demoHistory(llm);
        demoToolCallingWithTrace(llm);
        demoCompressedHistory(llm);
        demoFunctionRegistration(llm);
    }

    // ── Scenario 1: single-turn Q&A ──────────────────────────────────────────

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

    // ── Scenario 2: multi-turn conversation ──────────────────────────────────

    private static void demoHistory(OpenAiClient llm) {
        printHeader("2. Multi-turn Conversation");
        var agent = new SimpleAgent(llm);

        ask(agent, "我叫小明，我在学习 Java。");
        ask(agent, "我之前提到我在学什么？");

        System.out.println("History: " + agent.getHistory().size() + " messages");
        System.out.println("Runs:    " + agent.getExecutionTrace().size() + " traces\n");
    }

    // ── Scenario 3: tool calling + execution trace ────────────────────────────

    private static void demoToolCallingWithTrace(OpenAiClient llm) {
        printHeader("3. Tool Calling + Execution Trace");
        var agent = new SimpleAgent("MathAgent", llm, null, null);
        agent.addTool(new CalculatorTool());

        String task = "请计算 (123 + 456) * 7 - 89 的结果。";
        printTask(task);
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
                String preview = m.content() != null && m.content().length() > 60
                        ? m.content().substring(0, 60) + "..."
                        : m.content();
                System.out.printf("[%d] %-10s → %s%n", i, m.role(), preview);
            }
        }
        System.out.println();
    }

    // ── Scenario 4: CompressedHistory ────────────────────────────────────────

    private static void demoCompressedHistory(OpenAiClient llm) {
        printHeader("4. CompressedHistory");

        // low threshold to trigger compression quickly in demo
        var compressedHistory = new CompressedHistory(llm, 200, 80);
        var spb = new SystemPromptBuilder(ContextConfig.defaults());

        var agent = new SimpleAgent(llm, "You are a helpful assistant.");
        agent.withCompressedHistory(compressedHistory)
             .withSystemPromptBuilder(spb);

        ask(agent, "Java 是什么？");
        ask(agent, "它有哪些主要特性？");
        ask(agent, "JVM 的作用是什么？");

        System.out.println("History size:  " + agent.getHistory().size() + " messages");
        System.out.println("Has summary:   " + compressedHistory.hasSummary());
        System.out.println("Recent size:   " + compressedHistory.recentSize() + " messages\n");
    }

    // ── Scenario 5: function registration via lambda ──────────────────────────

    private static void demoFunctionRegistration(OpenAiClient llm) {
        printHeader("5. Function Registration");

        var textParam = ToolParameter.of(ToolParameter.Param.required("text", "Input text", "string"));
        var registry = new ToolRegistry()
                .register("uppercase", "Converts text to uppercase", textParam,
                        params -> params.getOrDefault("text", "").toUpperCase())
                .register("word_count", "Counts words in text",
                        ToolParameter.of(ToolParameter.Param.required("text", "Text to count", "string")),
                        params -> String.valueOf(params.getOrDefault("text", "").trim().split("\\s+").length))
                .register("reverse", "Reverses characters in a string", textParam,
                        params -> new StringBuilder(params.getOrDefault("text", "")).reverse().toString());

        var agent = new SimpleAgent("FunctionAgent", llm, null, registry);
        System.out.println("Tools: " + agent.listTools());

        String task = "请把 'hello world' 转换为大写，然后统计 'The quick brown fox' 的单词数量。";
        printTask(task);
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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