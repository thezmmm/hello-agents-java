package com.helloagents.demo;

import com.helloagents.agents.ReActAgent;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.FileReadTool;
import com.helloagents.tools.FileWriteTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Demo for {@link FileReadTool} and {@link FileWriteTool} — three scenarios:
 * <ol>
 *   <li>Direct write then read — basic round-trip</li>
 *   <li>Append mode — accumulate lines into a file</li>
 *   <li>ReActAgent — agent writes a summary file then reads it back</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.FileToolDemo"
 * </pre>
 */
public class FileToolDemo {

    public static void main(String[] args) throws IOException {
        Path workDir = Files.createTempDirectory("file-tool-demo-");
        System.out.println("Working directory: " + workDir);
        System.out.println();

        var reader = new FileReadTool();
        var writer = new FileWriteTool();

        demoRoundTrip(workDir, reader, writer);
        demoAppend(workDir, reader, writer);
        demoReActAgent(workDir, reader, writer);
    }

    // ── Scenario 1: write then read ───────────────────────────────────────────

    private static void demoRoundTrip(Path workDir, FileReadTool reader, FileWriteTool writer) {
        printHeader("1. Write then Read — round-trip");

        Path file = workDir.resolve("note.txt");
        String content = "Hello from FileWriteTool!\nThis is a test note.";

        System.out.println("Writing to: " + file);
        String writeResult = writer.execute(Map.of("path", file.toString(), "content", content));
        System.out.println("Write result: " + writeResult);

        System.out.println("Reading back:");
        String readResult = reader.execute(Map.of("path", file.toString()));
        System.out.println(readResult);
        System.out.println();
    }

    // ── Scenario 2: append mode ───────────────────────────────────────────────

    private static void demoAppend(Path workDir, FileReadTool reader, FileWriteTool writer) {
        printHeader("2. Append mode — accumulate lines");

        Path log = workDir.resolve("log.txt");

        for (int i = 1; i <= 3; i++) {
            String line = "Log entry #" + i + "\n";
            writer.execute(Map.of("path", log.toString(), "content", line, "mode", "append"));
            System.out.println("Appended: " + line.strip());
        }

        System.out.println("\nFinal file contents:");
        System.out.println(reader.execute(Map.of("path", log.toString())));
    }

    // ── Scenario 3: ReActAgent uses file tools ────────────────────────────────

    private static void demoReActAgent(Path workDir, FileReadTool reader, FileWriteTool writer) {
        printHeader("3. ReActAgent — write a plan then read it back");

        var llm = OpenAiClient.fromEnv();

        String systemPrompt = """
                You are a planning assistant. You have two tools:
                - file_write: write text to a file
                - file_read: read text from a file
                When asked to create a plan, write it to a file, then read it back to confirm it was saved correctly.
                The allowed work directory is: %s
                Always use absolute paths within that directory.
                """.formatted(workDir);

        var agent = new ReActAgent("PlannerAgent", llm, systemPrompt, 6);
        agent.addTool(writer);
        agent.addTool(reader);

        String task = "Write a 3-step study plan for learning Java agents to '"
                + workDir.resolve("study-plan.txt") + "', then read it back to confirm.";

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