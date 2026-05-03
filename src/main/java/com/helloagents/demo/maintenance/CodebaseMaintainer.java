package com.helloagents.demo.maintenance;

import com.helloagents.agents.ReActAgent;
import com.helloagents.context.CompressedHistory;
import com.helloagents.context.ContextConfig;
import com.helloagents.context.SystemPromptBuilder;
import com.helloagents.llm.FunctionCall;
import com.helloagents.llm.Message;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.memory.MemoryManager;
import com.helloagents.memory.MemoryService;
import com.helloagents.memory.store.MarkdownMemoryStore;
import com.helloagents.memory.tool.MemoryToolkit;
import com.helloagents.tools.FileReadTool;
import com.helloagents.tools.FileWriteTool;
import com.helloagents.tools.TerminalTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 长程代码库维护助手 — 主控类。
 *
 * <p>运行一个持续交互的 REPL 会话，使用 {@link ReActAgent} + {@link CompressedHistory}
 * 帮助开发者维护 Java 代码库。每轮任务复用同一个 Agent 实例，历史自动压缩以支持长会话。
 *
 * <p>工具配置：
 * <ul>
 *   <li>{@code terminal}   — 沙箱化只读 Shell（ls / grep / find / cat 等）</li>
 *   <li>{@code file_read}  — 读取代码文件</li>
 *   <li>{@code file_write} — 修改 / 生成文件</li>
 * </ul>
 *
 * <p>运行方式：
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.maintenance.CodebaseMaintainer"
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.maintenance.CodebaseMaintainer" \
 *                 -Dexec.args="/path/to/repo"
 * </pre>
 */
public class CodebaseMaintainer {

    private static final String SYSTEM_PROMPT = """
            You are an experienced Java codebase maintenance engineer. Your role is to help
            analyze, review, and improve Java codebases through careful exploration and targeted edits.

            Core capabilities:
            - Code quality review: identify smells, anti-patterns, and duplication
            - TODO/FIXME scanning: find and catalogue pending work items
            - Documentation: generate or improve Javadoc and README files
            - Bug detection: spot null pointer risks, resource leaks, and logic errors
            - Refactoring suggestions: propose clear, justified improvements
            - Dependency analysis: trace how classes and methods relate

            Workflow rules:
            1. Explore directory structure first (terminal: ls, find, tree)
            2. Read files before commenting on their content (file_read)
            3. Explain proposed changes before writing them (file_write)
            4. Preserve existing indentation and code style when editing

            Memory rules:
            - A [Memory] index is injected into your context automatically — review it before acting.
            - Use search_memory to retrieve full content of a specific memory when needed.
            - After completing analysis, use save_memory to persist discoveries worth remembering
              across sessions: architectural patterns, recurring issues, key design decisions.
              Do NOT save current task progress or anything re-derivable from the code.

            When you have a final answer or have completed the task, call the `finish` tool.
            Your finish answer must summarize: what you explored, what you found, and what files
            you modified (if any). This summary is the only context available to future tasks.
            """;

    // ── Session statistics ────────────────────────────────────────────────────

    /**
     * Immutable-by-reference record tracking mutable session state.
     * The List and Set fields are intentionally mutable to allow incremental recording.
     */
    record SessionStats(Instant startTime, List<String> completedTasks, Set<String> modifiedFiles) {

        static SessionStats create() {
            return new SessionStats(Instant.now(), new ArrayList<>(), new LinkedHashSet<>());
        }

        void recordTask(String task)     { completedTasks.add(task); }
        void recordFile(String filePath) { modifiedFiles.add(filePath); }

        Duration elapsed() {
            return Duration.between(startTime, Instant.now());
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        Path workspace = resolveWorkspace(args);
        printWelcome(workspace);

        var llm        = OpenAiClient.fromEnv();
        var history    = new CompressedHistory(llm);
        var termTool   = new TerminalTool(workspace);
        var memToolkit = buildMemoryToolkit(workspace);
        var spBuilder  = new SystemPromptBuilder(ContextConfig.defaults())
                             .withMemory(memToolkit.getService());
        var agent      = buildAgent(llm, history, workspace, termTool, memToolkit, spBuilder);
        var stats      = SessionStats.create();

        runInteractiveLoop(agent, termTool, stats);
        printSessionSummary(stats);
    }

    // ── Agent setup ───────────────────────────────────────────────────────────

    private static MemoryToolkit buildMemoryToolkit(Path workspace) {
        Path memDir  = workspace.resolve(".memory");
        var  store   = new MarkdownMemoryStore(memDir);
        var  manager = new MemoryManager(store);
        var  service = new MemoryService(manager);
        return new MemoryToolkit(service);
    }

    private static ReActAgent buildAgent(OpenAiClient llm, CompressedHistory history,
                                         Path workspace, TerminalTool termTool,
                                         MemoryToolkit memToolkit, SystemPromptBuilder spBuilder) {
        var agent = new ReActAgent("MaintenanceAgent", llm, SYSTEM_PROMPT, 20);
        agent.withCompressedHistory(history);
        agent.withSystemPromptBuilder(spBuilder);
        agent.addTool(termTool);
        agent.addTool(new FileReadTool(workspace));
        agent.addTool(new FileWriteTool(workspace));
        memToolkit.getTools().forEach(agent::addTool);
        return agent;
    }

    // ── Interactive loop ──────────────────────────────────────────────────────

    private static void runInteractiveLoop(ReActAgent agent, TerminalTool termTool, SessionStats stats) {
        var scanner = new Scanner(System.in);
        System.out.println("Ready. Type a task, or /help for commands.\n");

        while (true) {
            System.out.print("> ");
            System.out.flush();

            if (!scanner.hasNextLine()) break;

            String input = scanner.nextLine().strip();
            if (input.isBlank()) continue;

            if (input.startsWith("/")) {
                if (!handleCommand(input, agent, stats)) break;
            } else {
                termTool.reset();
                executeTask(input, agent, stats);
            }
        }
    }

    private static void executeTask(String input, ReActAgent agent, SessionStats stats) {
        System.out.println();
        try {
            agent.stream(input, token -> {
                System.out.print(token);
                System.out.flush();
            });
            System.out.println();
            stats.recordTask(input);
            scanForModifiedFiles(agent, stats);
        } catch (Exception e) {
            System.err.println("[Error] " + e.getMessage());
        }
    }

    // ── Command handling ──────────────────────────────────────────────────────

    /** Returns {@code false} to signal the loop should exit. */
    private static boolean handleCommand(String input, ReActAgent agent, SessionStats stats) {
        String cmd = input.split("\\s+", 2)[0].toLowerCase();
        return switch (cmd) {
            case "/exit", "/quit" -> {
                System.out.println("Goodbye.");
                yield false;
            }
            case "/status" -> {
                printStatus(stats, agent);
                yield true;
            }
            case "/trace" -> {
                agent.printTrace();
                yield true;
            }
            case "/history" -> {
                printHistory(agent);
                yield true;
            }
            case "/tools" -> {
                System.out.println("Registered tools: " + agent.listTools());
                yield true;
            }
            case "/clear" -> {
                agent.clearHistory();
                agent.clearExecutionTrace();
                System.out.println("History cleared.");
                yield true;
            }
            case "/help" -> {
                printHelp();
                yield true;
            }
            default -> {
                System.out.println("Unknown command '" + cmd + "'. Type /help for help.");
                yield true;
            }
        };
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    private static void printWelcome(Path workspace) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║    长程代码库维护助手  Maintenance Agent                ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println("Workspace : " + workspace);
        System.out.println("Tools     : terminal (sandbox), file_read, file_write, memory (persistent)");
        System.out.println("Context   : CompressedHistory + SystemPromptBuilder (memory index auto-injected)");
        System.out.println("Max steps : 20 per task");
        System.out.println();
    }

    private static void printStatus(SessionStats stats, ReActAgent agent) {
        Duration d = stats.elapsed();
        System.out.println("── Session Status ──────────────────────────────────────");
        System.out.printf("  Elapsed      : %dm %02ds%n", d.toMinutes(), d.toSecondsPart());
        System.out.printf("  Tasks done   : %d%n", stats.completedTasks().size());
        System.out.printf("  History msgs : %d%n", agent.getHistory().size());
        System.out.println("  Files modified:");
        if (stats.modifiedFiles().isEmpty()) {
            System.out.println("    (none)");
        } else {
            stats.modifiedFiles().forEach(f -> System.out.println("    * " + f));
        }
        System.out.println("────────────────────────────────────────────────────────");
    }

    private static void printSessionSummary(SessionStats stats) {
        Duration d = stats.elapsed();
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║                  Session Summary                     ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.printf("  Duration     : %dm %02ds%n", d.toMinutes(), d.toSecondsPart());
        System.out.printf("  Tasks done   : %d%n", stats.completedTasks().size());
        System.out.printf("  Files touched: %d%n", stats.modifiedFiles().size());
        if (!stats.completedTasks().isEmpty()) {
            System.out.println("  Tasks:");
            stats.completedTasks().forEach(t -> System.out.println("    * " + preview(t, 70)));
        }
        if (!stats.modifiedFiles().isEmpty()) {
            System.out.println("  Modified files:");
            stats.modifiedFiles().forEach(f -> System.out.println("    * " + f));
        }
        System.out.println();
    }

    private static void printHistory(ReActAgent agent) {
        var history = agent.getHistory();
        System.out.println("── Conversation History (" + history.size() + " messages) ──────────");
        for (int i = 0; i < history.size(); i++) {
            Message m = history.get(i);
            String content = preview(m.content(), 80);
            System.out.printf("  [%d] %-11s %s%n", i, m.role(), content);
        }
        System.out.println("────────────────────────────────────────────────────────");
    }

    private static void printHelp() {
        System.out.println("""
                Commands:
                  /status   — session stats (elapsed, tasks done, files modified)
                  /trace    — print last execution trace (tool call chain + results)
                  /history  — print conversation history
                  /tools    — list registered tools
                  /clear    — clear conversation history (session stats are preserved)
                  /help     — show this help
                  /exit     — quit and print session summary

                Example tasks:
                  Find all TODO and FIXME comments in the project
                  Review error handling in src/main/java/com/example/OrderService.java
                  Add Javadoc to all public methods in UserRepository.java
                  Check for potential null pointer exceptions in the service layer
                  List all public classes that have no corresponding test class
                  Identify duplicated utility methods across the codebase""");
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static Path resolveWorkspace(String[] args) {
        if (args.length > 0) {
            Path candidate = Path.of(args[0]).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate)) return candidate;
            System.err.println("Warning: '" + candidate + "' is not a directory — using cwd.");
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    /**
     * Scans the last execution trace for {@code file_write} tool calls
     * and records the target paths in {@code stats}.
     */
    private static void scanForModifiedFiles(ReActAgent agent, SessionStats stats) {
        var trace = agent.getLastExecution();
        if (trace == null) return;
        for (Message m : trace) {
            for (FunctionCall call : m.toolCalls()) {
                if (!"file_write".equals(call.name())) continue;
                String path = call.parseArguments().get("path");
                if (path != null && !path.isBlank()) stats.recordFile(path);
            }
        }
    }

    private static String preview(String text, int maxLen) {
        if (text == null) return "(null)";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }
}