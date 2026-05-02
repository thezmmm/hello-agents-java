package com.helloagents.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TerminalToolTest {

    @TempDir
    Path workspace;

    // ── helpers ───────────────────────────────────────────────────────────────

    private TerminalTool mockTool(String response) {
        return new TerminalTool(workspace, 30, 8_000, (cmd, dir, timeout) -> response);
    }

    private TerminalTool capturingTool(String[] capturedCmd, Path[] capturedDir, String response) {
        return new TerminalTool(workspace, 30, 8_000, (cmd, dir, timeout) -> {
            capturedCmd[0] = cmd;
            capturedDir[0] = dir;
            return response;
        });
    }

    // ── metadata ──────────────────────────────────────────────────────────────

    @Test
    void toolMetadata() {
        var tool = mockTool("ok");
        assertEquals("terminal", tool.name());
        assertFalse(tool.description().isBlank());
        assertFalse(tool.parameters().isEmpty());
    }

    @Test
    void descriptionListsAllowedCommands() {
        String desc = mockTool("ok").description();
        assertTrue(desc.contains("grep"), "description should mention allowed commands");
        assertTrue(desc.contains("cat"),  "description should mention allowed commands");
    }

    // ── input validation ──────────────────────────────────────────────────────

    @Test
    void rejectsBlankCommand() {
        assertTrue(mockTool("ok").execute(Map.of()).startsWith("Error:"));
    }

    @Test
    void rejectsEmptyCommandParam() {
        assertTrue(mockTool("ok").execute(Map.of("command", "  ")).startsWith("Error:"));
    }

    @Test
    void rejectsUnknownCommand() {
        String result = mockTool("ok").execute(Map.of("command", "rm -rf /"));
        assertTrue(result.startsWith("Error:"), result);
        assertTrue(result.contains("rm"),       result);
    }

    @Test
    void errorMessageListsAllowedCommands() {
        String result = mockTool("ok").execute(Map.of("command", "sudo whoami"));
        assertTrue(result.contains("grep"), "error should list allowed commands");
    }

    @Test
    void rejectsCommandWithDisallowedFirstToken() {
        // Even if args are harmless, the command token must be in the whitelist
        String result = mockTool("ok").execute(Map.of("command", "chmod 777 file.txt"));
        assertTrue(result.startsWith("Error:"));
    }

    // ── command forwarding ────────────────────────────────────────────────────

    @Test
    void allowedCommandIsForwardedToRunner() {
        var capturedCmd = new String[1];
        var capturedDir = new Path[1];
        var tool = capturingTool(capturedCmd, capturedDir, "output");

        tool.execute(Map.of("command", "ls -la"));

        assertEquals("ls -la", capturedCmd[0]);
        assertEquals(workspace.toAbsolutePath().normalize(), capturedDir[0]);
    }

    @Test
    void runnerOutputIsReturned() {
        String result = mockTool("hello from runner").execute(Map.of("command", "echo hello"));
        assertEquals("hello from runner", result);
    }

    @Test
    void longOutputIsTruncated() {
        String huge = "x".repeat(10_000);
        String result = mockTool(huge).execute(Map.of("command", "cat bigfile"));
        assertTrue(result.contains("[... truncated"), "long output should be truncated");
        assertTrue(result.length() < 9_000);
    }

    @Test
    void shortOutputIsNotTruncated() {
        String result = mockTool("short").execute(Map.of("command", "echo short"));
        assertEquals("short", result);
    }

    @Test
    void runnerIOExceptionReturnsError() {
        var tool = new TerminalTool(workspace, 30, 8_000, (cmd, dir, timeout) -> {
            throw new IOException("disk read error");
        });
        String result = tool.execute(Map.of("command", "cat file.txt"));
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("disk read error"));
    }

    // ── cd: happy path ────────────────────────────────────────────────────────

    @Test
    void cdToValidSubdirectory() throws Exception {
        Path sub = Files.createDirectory(workspace.resolve("src"));
        var tool = mockTool("ok");

        String result = tool.execute(Map.of("command", "cd src"));
        assertFalse(result.startsWith("Error:"), result);
        assertEquals(sub, tool.currentDir());
    }

    @Test
    void cdWithNoArgReturnsToWorkspace() throws Exception {
        Path sub = Files.createDirectory(workspace.resolve("src"));
        var tool = mockTool("ok");
        tool.execute(Map.of("command", "cd src"));

        tool.execute(Map.of("command", "cd"));
        assertEquals(workspace.toAbsolutePath().normalize(), tool.currentDir());
    }

    @Test
    void cdTildeReturnsToWorkspace() throws Exception {
        Files.createDirectory(workspace.resolve("deep"));
        var tool = mockTool("ok");
        tool.execute(Map.of("command", "cd deep"));

        tool.execute(Map.of("command", "cd ~"));
        assertEquals(workspace.toAbsolutePath().normalize(), tool.currentDir());
    }

    @Test
    void cdDotDotNavigatesUp() throws Exception {
        Path sub = Files.createDirectory(workspace.resolve("src"));
        var tool = mockTool("ok");
        tool.execute(Map.of("command", "cd src"));
        assertEquals(sub, tool.currentDir());

        tool.execute(Map.of("command", "cd .."));
        assertEquals(workspace.toAbsolutePath().normalize(), tool.currentDir());
    }

    // ── cd: sandbox enforcement ───────────────────────────────────────────────

    @Test
    void cdDotDotEscapeIsBlocked() throws Exception {
        var tool = mockTool("ok");  // currentDir = workspace root
        String result = tool.execute(Map.of("command", "cd .."));
        assertTrue(result.startsWith("Error:"), result);
        assertEquals(workspace.toAbsolutePath().normalize(), tool.currentDir(),
                "currentDir must not change on rejected cd");
    }

    @Test
    void cdTraversalEscapeIsBlocked() {
        var tool = mockTool("ok");
        String result = tool.execute(Map.of("command", "cd ../../../etc"));
        assertTrue(result.startsWith("Error:"), result);
    }

    @Test
    void cdAbsolutePathOutsideWorkspaceIsBlocked() {
        var tool = mockTool("ok");
        String result = tool.execute(Map.of("command", "cd /tmp"));
        // /tmp is outside workspace → blocked (unless workspace happens to be /tmp)
        if (!Path.of("/tmp").normalize().startsWith(workspace.toAbsolutePath().normalize())) {
            assertTrue(result.startsWith("Error:"), result);
        }
    }

    @Test
    void cdToNonExistentDirectoryReturnsError() {
        var tool = mockTool("ok");
        String result = tool.execute(Map.of("command", "cd nonexistent"));
        assertTrue(result.startsWith("Error:"), result);
        assertTrue(result.contains("not found"), result);
    }

    @Test
    void cdToFileReturnsError() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "hi");
        var tool = mockTool("ok");
        String result = tool.execute(Map.of("command", "cd README.md"));
        assertTrue(result.startsWith("Error:"), result);
        assertTrue(result.contains("directory"), result);
    }

    // ── cd affects subsequent commands ────────────────────────────────────────

    @Test
    void commandAfterCdRunsInNewDirectory() throws Exception {
        Path sub = Files.createDirectory(workspace.resolve("sub"));
        var capturedDir = new Path[1];
        var tool = new TerminalTool(workspace, 30, 8_000, (cmd, dir, timeout) -> {
            capturedDir[0] = dir;
            return "ok";
        });

        tool.execute(Map.of("command", "cd sub"));
        tool.execute(Map.of("command", "ls"));

        assertEquals(sub, capturedDir[0]);
    }

    // ── state accessors ───────────────────────────────────────────────────────

    @Test
    void currentDirStartsAtWorkspace() {
        assertEquals(workspace.toAbsolutePath().normalize(), mockTool("ok").currentDir());
    }

    @Test
    void workspaceReturnsNormalizedRoot() {
        assertEquals(workspace.toAbsolutePath().normalize(), mockTool("ok").workspace());
    }
}