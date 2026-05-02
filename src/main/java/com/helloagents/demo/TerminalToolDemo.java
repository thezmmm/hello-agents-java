package com.helloagents.demo;

import com.helloagents.agents.ReActAgent;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.TerminalTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Demo for {@link TerminalTool} — three scenarios:
 * <ol>
 *   <li>Direct navigation — cd + ls + cat</li>
 *   <li>Search and grep — find files by pattern, inspect content</li>
 *   <li>ReActAgent — agent explores a mock project and answers questions about it</li>
 * </ol>
 *
 * <p>On Windows, {@link TerminalTool} automatically uses Git Bash's {@code sh.exe} when
 * available, so all Unix commands work out of the box. Falls back to {@code cmd.exe}
 * ({@code dir}, {@code findstr}, {@code type}) when Git is not installed.
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.TerminalToolDemo"
 * </pre>
 */
public class TerminalToolDemo {

    public static void main(String[] args) throws IOException {
        Path workspace = buildMockProject();
        System.out.println("Workspace: " + workspace);
        System.out.println();

        var terminal = new TerminalTool(workspace);

        demoDirectNavigation(terminal);
        demoSearchAndRead(terminal);
        demoReActAgent(workspace);
    }

    // ── Scenario 1: direct navigation ────────────────────────────────────────

    private static void demoDirectNavigation(TerminalTool terminal) {
        printHeader("1. Direct navigation — cd + ls + cat");

        run(terminal, "ls -la");
        run(terminal, "cd src");
        run(terminal, "ls");
        run(terminal, "cat Main.java");
        run(terminal, "cd ~");
    }

    // ── Scenario 2: search and grep ───────────────────────────────────────────

    private static void demoSearchAndRead(TerminalTool terminal) {
        printHeader("2. Search and grep");

        run(terminal, "find . -name '*.java'");
        run(terminal, "grep -r 'class' src/");
        run(terminal, "wc -l src/*.java");
    }

    // ── Scenario 3: ReActAgent ────────────────────────────────────────────────

    private static void demoReActAgent(Path workspace) {
        printHeader("3. ReActAgent — explore the project");

        var llm      = OpenAiClient.fromEnv();
        var terminal = new TerminalTool(workspace);

        String systemPrompt = """
                You are a code-exploration assistant. Use the terminal tool to answer questions
                about the project. Useful commands: ls, cat, grep, find, head, wc.
                Always explore the directory structure before reading files.
                """;

        var agent = new ReActAgent("ExplorerAgent", llm, systemPrompt, 8);
        agent.addTool(terminal);

        String task = "How many Java source files are in this project, and what public classes do they define?";
        System.out.println("Task : " + task);
        System.out.print("Reply: ");
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println("\n");

        agent.printTrace();
    }

    // ── workspace setup ───────────────────────────────────────────────────────

    private static Path buildMockProject() throws IOException {
        Path root = Files.createTempDirectory("terminal-demo-");
        Path src  = Files.createDirectory(root.resolve("src"));
        Files.createDirectory(root.resolve("test"));

        Files.writeString(src.resolve("Main.java"), """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello, Agents!");
                    }
                }
                """);

        Files.writeString(src.resolve("Agent.java"), """
                public class Agent {
                    private final String name;
                    public Agent(String name) { this.name = name; }
                    public String run(String task) { return name + ": " + task; }
                }
                """);

        Files.writeString(root.resolve("test").resolve("AgentTest.java"), """
                public class AgentTest {
                    public void testRun() {
                        Agent agent = new Agent("test");
                        assert agent.run("hello").contains("hello");
                    }
                }
                """);

        Files.writeString(root.resolve("README.md"), """
                # Demo Project
                A tiny mock Java project for the TerminalTool demo.
                - src/Main.java       entry point
                - src/Agent.java      agent class
                - test/AgentTest.java unit test
                """);

        return root;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void run(TerminalTool terminal, String command) {
        System.out.println("$ " + command);
        System.out.println(terminal.execute(Map.of("command", command)));
    }

    private static void printHeader(String title) {
        System.out.println("=".repeat(60));
        System.out.println(" " + title);
        System.out.println("=".repeat(60));
    }
}