package com.helloagents.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Executes shell commands within a confined workspace directory.
 *
 * <p>Safety layers:
 * <ol>
 *   <li><b>Command whitelist</b> — only read-only, non-destructive commands are permitted.</li>
 *   <li><b>Workspace sandbox</b> — {@code cd} cannot navigate outside the workspace root.</li>
 *   <li><b>Timeout</b> — each command is forcibly killed after {@code timeoutSecs}.</li>
 *   <li><b>Output cap</b> — output is truncated to {@code maxOutputChars} characters.</li>
 * </ol>
 *
 * <p>The working directory is stateful: a successful {@code cd} call persists across subsequent
 * {@link #execute} calls on the same instance. This mirrors how a real shell session works.
 */
public class TerminalTool implements Tool {

    /** Read-only commands the agent is allowed to run. */
    static final Set<String> ALLOWED_COMMANDS = Set.of(
            // directory listing
            "ls", "dir", "tree",
            // file content
            "cat", "head", "tail", "more",
            "type",           // Windows: print file content (like cat)
            // search
            "find", "grep", "egrep", "fgrep",
            "findstr",        // Windows: grep equivalent
            // text processing
            "wc", "sort", "uniq", "cut", "awk", "sed",
            // navigation
            "pwd", "cd",
            // file metadata
            "file", "stat", "du", "df",
            // misc
            "echo", "which", "where", "whereis"
    );

    private static final int DEFAULT_TIMEOUT_SECS    = 30;
    private static final int DEFAULT_MAX_OUTPUT_CHARS = 8_000;

    // ── testability hook ───────────────────────────────────────────────────────

    @FunctionalInterface
    interface CommandRunner {
        String run(String command, Path workDir, int timeoutSecs)
                throws IOException, InterruptedException;
    }

    // ── state ─────────────────────────────────────────────────────────────────

    private final Path          workspace;
    private       Path          currentDir;   // mutated by cd
    private final int           timeoutSecs;
    private final int           maxOutputChars;
    private final CommandRunner runner;

    // ── constructors ───────────────────────────────────────────────────────────

    public TerminalTool(Path workspace) {
        this(workspace, DEFAULT_TIMEOUT_SECS, DEFAULT_MAX_OUTPUT_CHARS);
    }

    public TerminalTool(Path workspace, int timeoutSecs, int maxOutputChars) {
        this(workspace, timeoutSecs, maxOutputChars, buildRunner());
    }

    TerminalTool(Path workspace, int timeoutSecs, int maxOutputChars, CommandRunner runner) {
        this.workspace      = workspace.toAbsolutePath().normalize();
        this.currentDir     = this.workspace;
        this.timeoutSecs    = timeoutSecs;
        this.maxOutputChars = maxOutputChars;
        this.runner         = runner;
    }

    // ── Tool interface ─────────────────────────────────────────────────────────

    @Override
    public String name() {
        return "terminal";
    }

    @Override
    public String description() {
        return """
                Execute a shell command inside the workspace and return its output \
                (truncated to %d characters). \
                Permitted commands: %s. \
                The working directory persists between calls — use cd to navigate within the workspace. \
                cd cannot leave the workspace root."""
                .formatted(DEFAULT_MAX_OUTPUT_CHARS,
                        ALLOWED_COMMANDS.stream().sorted().toList());
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
                ToolParameter.Param.required("command",
                        "Shell command to execute (read-only commands only)", "string")
        );
    }

    @Override
    public String execute(Map<String, String> params) {
        String command = params.getOrDefault("command", "").strip();
        if (command.isBlank()) {
            return "Error: 'command' is required.";
        }

        // Layer 1: whitelist — check first token (the base command)
        String firstToken = command.split("\\s+", 2)[0];
        if (!ALLOWED_COMMANDS.contains(firstToken)) {
            return "Error: command not allowed: '" + firstToken
                    + "'. Allowed: " + ALLOWED_COMMANDS.stream().sorted().toList();
        }

        // cd is a shell built-in — handle without spawning a process
        if ("cd".equals(firstToken)) {
            String[] parts = command.split("\\s+", 2);
            return handleCd(parts.length > 1 ? parts[1].strip() : "");
        }

        try {
            String output = runner.run(command, currentDir, timeoutSecs);
            return truncate(output, maxOutputChars);
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: command interrupted";
        }
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    /** Current working directory — updated by each successful {@code cd} call. */
    public Path currentDir() { return currentDir; }

    /** Workspace root this tool is confined to. */
    public Path workspace()  { return workspace; }

    // ── cd logic ──────────────────────────────────────────────────────────────

    private String handleCd(String target) {
        Path newDir;
        if (target.isBlank() || "~".equals(target)) {
            newDir = workspace;
        } else {
            try {
                Path p = Path.of(target);
                newDir = (p.isAbsolute() ? p : currentDir.resolve(target)).normalize();
            } catch (InvalidPathException e) {
                return "Error: invalid path — " + e.getMessage();
            }
        }

        // Layer 2: workspace sandbox
        if (!newDir.startsWith(workspace)) {
            return "Error: path is outside the workspace — " + newDir;
        }
        if (!Files.exists(newDir)) {
            return "Error: directory not found — " + newDir;
        }
        if (!Files.isDirectory(newDir)) {
            return "Error: not a directory — " + newDir;
        }

        currentDir = newDir;
        return "Changed directory to: " + currentDir;
    }

    // ── runner ────────────────────────────────────────────────────────────────

    private static CommandRunner buildRunner() {
        ShellInfo shell = resolveShell();
        return (command, workDir, timeoutSecs) -> {
            String actualCommand = shell.isCmdExe()
                    // Force UTF-8 code page so dir/findstr output is readable
                    ? "chcp 65001 > nul & " + command
                    : command;

            ProcessBuilder pb = new ProcessBuilder(shell.prefix()[0], shell.prefix()[1], actualCommand);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            // Git Bash needs LANG so it outputs UTF-8
            if (!shell.isCmdExe()) {
                pb.environment().put("LANG",   "en_US.UTF-8");
                pb.environment().put("LC_ALL", "en_US.UTF-8");
            }

            Process process = pb.start();

            // Read output concurrently to prevent pipe-buffer deadlock
            var output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                } catch (IOException ignored) {}
            });
            reader.setDaemon(true);
            reader.start();

            // Layer 3: timeout
            boolean finished = process.waitFor(timeoutSecs, TimeUnit.SECONDS);
            reader.join(2_000);

            if (!finished) {
                process.destroyForcibly();
                return "Error: command timed out after " + timeoutSecs + " seconds";
            }

            int exitCode = process.exitValue();
            String text  = output.toString();

            if (text.isEmpty()) {
                return exitCode == 0 ? "(no output)" : "Warning: exit code " + exitCode;
            }
            return exitCode == 0 ? text : "Warning: exit code " + exitCode + "\n" + text;
        };
    }

    /**
     * Detects the best available shell.
     *
     * <p>Linux/macOS: {@code /bin/sh}.
     * Windows (in priority order):
     * <ol>
     *   <li>{@code where.exe git} → derive sh.exe from git.exe location (any drive, any path)</li>
     *   <li>{@code where.exe sh}  → sh already in PATH</li>
     *   <li>Windows registry      → {@code HKLM\SOFTWARE\GitForWindows\InstallPath}</li>
     *   <li>{@code cmd.exe}       → fallback, Unix commands unavailable</li>
     * </ol>
     */
    private static ShellInfo resolveShell() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return new ShellInfo(new String[]{"/bin/sh", "-c"}, false);
        }
        String sh;
        if ((sh = findShViaGit())      != null) return new ShellInfo(new String[]{sh, "-c"}, false);
        if ((sh = findShInPath())      != null) return new ShellInfo(new String[]{sh, "-c"}, false);
        if ((sh = findShViaRegistry()) != null) return new ShellInfo(new String[]{sh, "-c"}, false);
        return new ShellInfo(new String[]{"cmd.exe", "/c"}, true);
    }

    /**
     * Locates git.exe via {@code where.exe}, then derives the Git root and resolves sh.exe.
     * Works regardless of drive letter or install path.
     */
    private static String findShViaGit() {
        try {
            Process p = new ProcessBuilder("where.exe", "git")
                    .redirectErrorStream(true).start();
            if (!p.waitFor(5, TimeUnit.SECONDS) || p.exitValue() != 0) return null;
            String gitPath = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .trim().split("[\r\n]+")[0].trim();
            if (gitPath.isBlank()) return null;
            // git.exe lives at <root>/cmd/git.exe; sh.exe at <root>/bin/ or <root>/usr/bin/
            Path gitRoot = Path.of(gitPath).getParent().getParent();
            for (String rel : new String[]{"bin\\sh.exe", "usr\\bin\\sh.exe"}) {
                File candidate = gitRoot.resolve(rel).toFile();
                if (candidate.isFile()) return candidate.getAbsolutePath();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Finds sh.exe directly via {@code where.exe sh} — works when Git Bash bin is in PATH. */
    private static String findShInPath() {
        try {
            Process p = new ProcessBuilder("where.exe", "sh")
                    .redirectErrorStream(true).start();
            if (!p.waitFor(5, TimeUnit.SECONDS) || p.exitValue() != 0) return null;
            String found = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .trim().split("[\r\n]+")[0].trim();
            return (!found.isBlank() && new File(found).isFile()) ? found : null;
        } catch (Exception ignored) {}
        return null;
    }

    /** Reads the Git install path written by the Git for Windows installer into the Windows registry. */
    private static String findShViaRegistry() {
        try {
            Process p = new ProcessBuilder("reg.exe", "query",
                    "HKEY_LOCAL_MACHINE\\SOFTWARE\\GitForWindows", "/v", "InstallPath")
                    .redirectErrorStream(true).start();
            if (!p.waitFor(5, TimeUnit.SECONDS) || p.exitValue() != 0) return null;
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String line : out.split("[\r\n]+")) {
                if (line.contains("REG_SZ")) {
                    String installPath = line.replaceAll(".*REG_SZ\\s+", "").trim();
                    for (String rel : new String[]{"bin\\sh.exe", "usr\\bin\\sh.exe"}) {
                        File candidate = new File(installPath, rel);
                        if (candidate.isFile()) return candidate.getAbsolutePath();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private record ShellInfo(String[] prefix, boolean isCmdExe) {}

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n[... truncated at " + maxChars + " characters]";
    }
}