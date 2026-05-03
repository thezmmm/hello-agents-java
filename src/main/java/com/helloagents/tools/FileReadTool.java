package com.helloagents.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Reads a local file and returns its content as a string.
 *
 * <p>File content is truncated to {@value #MAX_CONTENT_CHARS} characters to avoid
 * overflowing the LLM context window.
 *
 * <p>If constructed with a workspace path, reads outside the workspace are rejected.
 */
public class FileReadTool implements Tool {

    private static final int MAX_CONTENT_CHARS = 8000;

    private final Path workspace;

    public FileReadTool() {
        this.workspace = null;
    }

    public FileReadTool(Path workspace) {
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "file_read";
    }

    @Override
    public String description() {
        return """
                Read a local file and return its text content (truncated to 8000 characters). \
                Use this tool when the user asks you to read, summarise, or analyse a file on disk.""";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
                ToolParameter.Param.required("path", "Absolute or relative path to the file", "string")
        );
    }

    @Override
    public String execute(Map<String, String> params) {
        String rawPath = params.getOrDefault("path", "").strip();
        if (rawPath.isBlank()) {
            return "Error: 'path' is required.";
        }

        Path target;
        try {
            target = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return "Error: invalid path — " + e.getMessage();
        }

        if (workspace != null && !target.startsWith(workspace)) {
            return "Error: path is outside the workspace — " + target;
        }

        if (!Files.exists(target)) {
            return "Error: file not found — " + target;
        }
        if (Files.isDirectory(target)) {
            return "Error: path is a directory, not a file — " + target;
        }

        try {
            String content = Files.readString(target);
            return truncate(content, MAX_CONTENT_CHARS);
        } catch (IOException e) {
            return "Error: could not read file — " + e.getMessage();
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.isBlank()) return "(empty file)";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n[... truncated at " + maxChars + " characters]";
    }
}