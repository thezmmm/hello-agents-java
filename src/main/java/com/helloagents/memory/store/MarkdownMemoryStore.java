package com.helloagents.memory.store;

import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.memory.core.MemoryStore;
import com.helloagents.memory.core.MemoryType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link MemoryStore} backed by a directory of Markdown files.
 *
 * <p>Each entry is persisted as {@code {id}.md} using YAML frontmatter:
 * <pre>
 * ---
 * id: abc12345
 * name: prefer_tabs
 * description: User prefers tabs for indentation
 * type: user
 * created_at: 1714233600000
 * last_accessed_at: 1714233600000
 * access_count: 0
 * session_id: session_2024-...
 * ---
 * The user explicitly prefers tabs over spaces when editing source files.
 * </pre>
 * Additional metadata key-value pairs are appended as extra frontmatter lines.
 * Malformed files are silently skipped during {@link #listAll()}.
 */
public class MarkdownMemoryStore implements MemoryStore {

    private final Path dir;

    public MarkdownMemoryStore() {
        this(Path.of("memory"));
    }

    public MarkdownMemoryStore(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── MemoryStore ───────────────────────────────────────────────────────────

    @Override
    public void save(MemoryEntry entry) {
        try {
            Files.writeString(filePath(entry.id()), serialize(entry));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<MemoryEntry> get(String id) {
        Path file = filePath(id);
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(parse(Files.readString(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean delete(String id) {
        try {
            return Files.deleteIfExists(filePath(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<MemoryEntry> listAll() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            List<MemoryEntry> result = new ArrayList<>();
            for (Path file : stream) {
                try {
                    result.add(parse(Files.readString(file)));
                } catch (Exception ignored) { /* skip malformed files */ }
            }
            return Collections.unmodifiableList(result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void clear() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path file : stream) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int size() {
        return listAll().size();
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    static String serialize(MemoryEntry e) {
        StringBuilder sb = new StringBuilder("---\n");
        sb.append("id: ").append(e.id()).append("\n");
        String name = e.metadata().getOrDefault("name", "");
        if (!name.isEmpty())          sb.append("name: ").append(name).append("\n");
        if (!e.description().isEmpty()) sb.append("description: ").append(e.description()).append("\n");
        sb.append("type: ").append(e.type().name().toLowerCase()).append("\n");
        sb.append("created_at: ").append(e.createdAt()).append("\n");
        sb.append("last_accessed_at: ").append(e.lastAccessedAt()).append("\n");
        sb.append("access_count: ").append(e.accessCount()).append("\n");
        // remaining metadata (name already written above)
        e.metadata().forEach((k, v) -> {
            if (!"name".equals(k)) sb.append(k).append(": ").append(v).append("\n");
        });
        sb.append("---\n");
        sb.append(e.content());
        return sb.toString();
    }

    static MemoryEntry parse(String text) {
        // normalize line endings so files edited on Windows round-trip correctly
        String[] lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        if (lines.length < 2 || !"---".equals(lines[0])) {
            throw new IllegalArgumentException("Missing opening frontmatter delimiter");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        int i = 1;
        while (i < lines.length && !"---".equals(lines[i])) {
            String line = lines[i++];
            int colon = line.indexOf(':');
            if (colon > 0) {
                fields.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
            }
        }
        if (i >= lines.length) {
            throw new IllegalArgumentException("Unclosed frontmatter — missing closing ---");
        }
        i++; // skip closing ---
        String content = String.join("\n", Arrays.copyOfRange(lines, i, lines.length)).strip();

        String     id             = fields.remove("id");
        String     description    = fields.remove("description");
        String     typeName       = fields.remove("type");
        long       createdAt      = parseLong(fields.remove("created_at"),       0L);
        long       lastAccessedAt = parseLong(fields.remove("last_accessed_at"), 0L);
        int        accessCount    = parseInt (fields.remove("access_count"),      0);

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Missing required frontmatter field: id");
        }
        MemoryType type = MemoryType.fromString(typeName != null ? typeName : "feedback");

        return new MemoryEntry(id, type, content, description, createdAt, lastAccessedAt, accessCount, fields);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Path filePath(String id) {
        return dir.resolve(id + ".md");
    }

    private static long parseLong(String v, long fallback) {
        if (v == null) return fallback;
        try { return Long.parseLong(v.strip()); } catch (NumberFormatException e) { return fallback; }
    }

    private static int parseInt(String v, int fallback) {
        if (v == null) return fallback;
        try { return Integer.parseInt(v.strip()); } catch (NumberFormatException e) { return fallback; }
    }
}