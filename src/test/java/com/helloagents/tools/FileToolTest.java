package com.helloagents.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileToolTest {

    @TempDir
    Path tempDir;

    // ═══════════════════════════════════════════════════════════════════════════
    // FileReadTool
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void readToolMetadata() {
        var tool = new FileReadTool();
        assertEquals("file_read", tool.name());
        assertFalse(tool.description().isBlank());
        assertFalse(tool.parameters().isEmpty());
    }

    @Test
    void readRequiresPath() {
        assertTrue(new FileReadTool().execute(Map.of()).startsWith("Error:"));
    }

    @Test
    void readRejectsBlankPath() {
        assertTrue(new FileReadTool().execute(Map.of("path", "   ")).startsWith("Error:"));
    }

    @Test
    void readRejectsMissingFile() {
        String result = new FileReadTool().execute(
                Map.of("path", tempDir.resolve("no-such-file.txt").toString()));
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void readRejectsDirectory() {
        String result = new FileReadTool().execute(Map.of("path", tempDir.toString()));
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("directory"));
    }

    @Test
    void readReturnsFileContent() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "Hello, world!");

        String result = new FileReadTool().execute(Map.of("path", file.toString()));
        assertEquals("Hello, world!", result);
    }

    @Test
    void readEmptyFileReturnsEmptyMarker() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "");

        String result = new FileReadTool().execute(Map.of("path", file.toString()));
        assertEquals("(empty file)", result);
    }

    @Test
    void readTruncatesLargeFiles() throws IOException {
        Path file = tempDir.resolve("large.txt");
        Files.writeString(file, "A".repeat(10_000));

        String result = new FileReadTool().execute(Map.of("path", file.toString()));
        assertTrue(result.contains("[... truncated"), "Large file should be truncated");
        assertTrue(result.length() < 9000);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FileWriteTool
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void writeToolMetadata() {
        var tool = new FileWriteTool();
        assertEquals("file_write", tool.name());
        assertFalse(tool.description().isBlank());
        assertFalse(tool.parameters().isEmpty());
    }

    @Test
    void writeRequiresPath() {
        assertTrue(new FileWriteTool().execute(Map.of("content", "hello")).startsWith("Error:"));
    }

    @Test
    void writeRequiresContent() {
        assertTrue(new FileWriteTool().execute(
                Map.of("path", tempDir.resolve("out.txt").toString())).startsWith("Error:"));
    }

    @Test
    void writeRejectsInvalidMode() {
        String result = new FileWriteTool().execute(Map.of(
                "path", tempDir.resolve("out.txt").toString(),
                "content", "hello",
                "mode", "truncate"
        ));
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("mode"));
    }

    @Test
    void writeCreatesNewFile() throws IOException {
        Path file = tempDir.resolve("new.txt");
        String result = new FileWriteTool().execute(
                Map.of("path", file.toString(), "content", "created"));

        assertFalse(result.startsWith("Error:"), result);
        assertEquals("created", Files.readString(file));
    }

    @Test
    void writeOverwritesExistingFile() throws IOException {
        Path file = tempDir.resolve("existing.txt");
        Files.writeString(file, "old content");

        new FileWriteTool().execute(
                Map.of("path", file.toString(), "content", "new content", "mode", "overwrite"));

        assertEquals("new content", Files.readString(file));
    }

    @Test
    void writeDefaultModeIsOverwrite() throws IOException {
        Path file = tempDir.resolve("default.txt");
        Files.writeString(file, "original");

        new FileWriteTool().execute(Map.of("path", file.toString(), "content", "replaced"));

        assertEquals("replaced", Files.readString(file));
    }

    @Test
    void writeAppendsToExistingFile() throws IOException {
        Path file = tempDir.resolve("append.txt");
        Files.writeString(file, "line1\n");

        new FileWriteTool().execute(
                Map.of("path", file.toString(), "content", "line2\n", "mode", "append"));

        assertEquals("line1\nline2\n", Files.readString(file));
    }

    @Test
    void writeAppendCreatesFileIfMissing() throws IOException {
        Path file = tempDir.resolve("appendNew.txt");

        new FileWriteTool().execute(
                Map.of("path", file.toString(), "content", "first", "mode", "append"));

        assertTrue(Files.exists(file));
        assertEquals("first", Files.readString(file));
    }

    @Test
    void writeResultMentionsCharCount() {
        Path file = tempDir.resolve("report.txt");
        String result = new FileWriteTool().execute(
                Map.of("path", file.toString(), "content", "hello"));

        assertTrue(result.startsWith("OK:"), result);
        assertTrue(result.contains("5"), "Should mention the number of characters written");
    }

    @Test
    void writeCreatesParentDirectories() throws IOException {
        Path file = tempDir.resolve("sub").resolve("dir").resolve("nested.txt");

        new FileWriteTool().execute(Map.of("path", file.toString(), "content", "deep"));

        assertTrue(Files.exists(file));
        assertEquals("deep", Files.readString(file));
    }
}