package com.helloagents.demo;

import com.helloagents.agents.ReActAgent;
import com.helloagents.llm.Message;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.WebSearchTool;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Demo for {@link WebSearchTool} — two scenarios:
 * <ol>
 *   <li>Direct tool invocation (no LLM)</li>
 *   <li>ReActAgent using WebSearchTool to answer a real-time question</li>
 * </ol>
 *
 * <p>Required environment variables (set in {@code .env}):
 * <ul>
 *   <li>{@code TAVILY_API_KEY} — Tavily Search API key</li>
 *   <li>{@code LLM_API_KEY} — your LLM provider key (for scenario 2)</li>
 * </ul>
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.WebSearchToolDemo"
 * </pre>
 */
public class WebSearchToolDemo {

    public static void main(String[] args) {
        demoDirectSearch();
        demoReActWithSearch();
    }

    // ── Scenario 1: direct tool call ──────────────────────────────────────────

    private static void demoDirectSearch() {
        printHeader("1. Direct WebSearchTool Call");

        WebSearchTool tool = WebSearchTool.fromEnv();

        String query = "latest developments in AI agents 2025";
        System.out.println("Query: " + query);
        System.out.println();

        String result = tool.execute(Map.of("query", query, "max_results", "3"));
        System.out.println(result);
        System.out.println();
    }

    // ── Scenario 2: ReActAgent + WebSearchTool ────────────────────────────────

    private static void demoReActWithSearch() {
        printHeader("2. ReActAgent + WebSearchTool");

        var llm  = OpenAiClient.fromEnv();
        var tool = WebSearchTool.fromEnv();

        String systemPrompt = """
                You are a helpful research assistant. Today's date is %s.
                Use the web_search tool with topic=news to find recent news articles.
                When you have enough information, call finish with a concise summary.
                """.formatted(LocalDate.now());

        var agent = new ReActAgent("SearchAgent", llm, systemPrompt, 5);
        agent.addTool(tool);

        String task = "What are the most recent highlights from the AI world this week?";
        System.out.println("Task : " + task);
        System.out.print("Reply: ");
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");

        // Show execution trace
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
                String preview = m.content() != null && m.content().length() > 120
                        ? m.content().substring(0, 120) + "..."
                        : m.content();
                System.out.printf("[%d] tool      → %s%n", i, preview);
            } else {
                String preview = m.content() != null && m.content().length() > 80
                        ? m.content().substring(0, 80) + "..."
                        : m.content();
                System.out.printf("[%d] %-10s → %s%n", i, m.role(), preview);
            }
        }
        System.out.println();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void printHeader(String title) {
        System.out.println("=".repeat(60));
        System.out.println(" " + title);
        System.out.println("=".repeat(60));
    }
}