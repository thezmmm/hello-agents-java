package com.helloagents.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;

/**
 * Writes text content to a local file.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li>{@code overwrite} (default) — replaces the entire file</li>
 *   <li>{@code append} — adds content after any existing content</li>
 * </ul>
 *
 * <p>Parent directories are created automatically if they do not exist.
 */
public class FileWriteTool implements Tool {

    private static final Set<String> VALID_MODES = Set.of("overwrite", "append");

    @Override
    public String name() {
        return "file_write";
    }

    @Override
    public String description() {
        return """
                Write text content to a local file. \
                Set mode to "overwrite" (default) to replace the file, or "append" to add to it. \
                Parent directories are created automatically if they do not exist.""";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
                ToolParameter.Param.required("path", "Absolute or relative path to the file", "string"),
                ToolParameter.Param.required("content", "Text content to write", "string"),
                ToolParameter.Param.optional("mode",
                        "Write mode: \"overwrite\" (default, replaces file) or \"append\" (adds to end)", "string")
        );
    }

    @Override
    public String execute(Map<String, String> params) {
        String rawPath = params.getOrDefault("path", "").strip();
        if (rawPath.isBlank()) {
            return "Error: 'path' is required.";
        }

        String content = params.get("content");
        if (content == null) {
            return "Error: 'content' is required.";
        }

        String mode = params.getOrDefault("mode", "overwrite").strip().toLowerCase();
        if (!VALID_MODES.contains(mode)) {
            return "Error: 'mode' must be \"overwrite\" or \"append\", got: " + mode;
        }

        Path target;
        try {
            target = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return "Error: invalid path — " + e.getMessage();
        }

        try {
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if ("append".equals(mode)) {
                Files.writeString(target, content,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(target, content,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            return "OK: wrote " + content.length() + " characters to " + target
                    + " (mode=" + mode + ")";
        } catch (IOException e) {
            return "Error: could not write file — " + e.getMessage();
        }
    }
}