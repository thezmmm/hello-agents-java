package com.helloagents.demo;

import com.helloagents.agents.ReActAgent;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.HttpTool;
import com.helloagents.tools.WebSearchTool;

import java.time.LocalDate;
import java.util.Map;

/**
 * Demo for {@link HttpTool} — three scenarios:
 * <ol>
 *   <li>Direct GET — call a public JSON API</li>
 *   <li>Direct POST — submit data to a test endpoint</li>
 *   <li>ReActAgent combining WebSearchTool + HttpTool:
 *       search finds a URL, then agent fetches full content</li>
 * </ol>
 *
 * <p>Scenarios 1 and 2 use <a href="https://jsonplaceholder.typicode.com">JSONPlaceholder</a>
 * — a free public REST API, no key required.
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.HttpToolDemo"
 * </pre>
 */
public class HttpToolDemo {

    public static void main(String[] args) {
        var http = new HttpTool();

        demoGet(http);
        demoPost(http);
        demoReActSearchAndFetch();
    }

    // ── Scenario 1: direct GET ────────────────────────────────────────────────

    private static void demoGet(HttpTool http) {
        printHeader("1. Direct GET — public JSON API");

        String url = "https://jsonplaceholder.typicode.com/posts/1";
        System.out.println("URL   : " + url);
        System.out.println("Result: " + http.execute(Map.of("url", url)));
        System.out.println();
    }

    // ── Scenario 2: direct POST ───────────────────────────────────────────────

    private static void demoPost(HttpTool http) {
        printHeader("2. Direct POST — submit JSON body");

        String url  = "https://jsonplaceholder.typicode.com/posts";
        String body = "{\"title\":\"hello-agents\",\"body\":\"Testing HttpTool POST\",\"userId\":1}";

        System.out.println("URL   : " + url);
        System.out.println("Body  : " + body);
        System.out.println("Result: " + http.execute(Map.of(
                "url", url,
                "method", "POST",
                "body", body,
                "headers", "{\"Content-Type\":\"application/json\"}"
        )));
        System.out.println();
    }

    // ── Scenario 3: ReActAgent — search then fetch ────────────────────────────

    private static void demoReActSearchAndFetch() {
        printHeader("3. ReActAgent: WebSearchTool + HttpTool");

        var llm    = OpenAiClient.fromEnv();
        var search = WebSearchTool.fromEnv();
        var http   = new HttpTool();

        String systemPrompt = """
                You are a research assistant. Today's date is %s.
                Available tools:
                - web_search: find relevant URLs and short summaries
                - http_fetch: read the full content of a specific URL when summaries are insufficient
                Workflow: search first, then fetch a specific URL if you need more detail.
                When you have enough information, call finish with your answer.
                """.formatted(LocalDate.now());

        var agent = new ReActAgent("ResearchAgent", llm, systemPrompt, 6);
        agent.addTool(search);
        agent.addTool(http);

        String task = "What is JSONPlaceholder and what endpoints does it offer? Fetch the site to confirm.";
        System.out.println("Task : " + task);
        System.out.print("Reply: ");
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");

        agent.printTrace();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void printHeader(String title) {
        System.out.println("=".repeat(60));
        System.out.println(" " + title);
        System.out.println("=".repeat(60));
    }
}