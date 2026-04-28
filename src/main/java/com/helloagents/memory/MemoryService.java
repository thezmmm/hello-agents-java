package com.helloagents.memory;

import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.memory.core.MemoryType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

/**
 * Memory usage layer — intelligent operations on top of {@link MemoryManager}.
 *
 * <p>Responsible for search ranking, forget strategies, and aggregate statistics.
 * Delegates all persistence to the underlying {@link MemoryManager}.
 */
public class MemoryService {

    private final MemoryManager manager;
    private String currentSessionId;

    public MemoryService(MemoryManager manager) {
        this.manager = manager;
    }

    public String getCurrentSessionId() { return currentSessionId; }
    public void setCurrentSessionId(String sessionId) { this.currentSessionId = sessionId; }

    // ── remember ─────────────────────────────────────────────────────────────

    /**
     * Store a new memory entry and return its ID.
     *
     * <p>Automatically attaches {@code session_id} and {@code timestamp} to every entry.
     *
     * @param name        short slug used in the index (e.g. {@code prefer_tabs}); stored in metadata
     * @param description one-liner for the index (e.g. {@code User prefers tabs}); stored in metadata
     * @param extra       additional metadata key-value pairs
     */
    public String remember(MemoryType type, String content,
                           String name, String description, Map<String, String> extra) {
        ensureSession();
        Map<String, String> metadata = new HashMap<>(extra != null ? extra : Map.of());
        if (name != null && !name.isBlank()) metadata.put("name", name.strip());
        metadata.put("session_id", currentSessionId);
        metadata.put("timestamp",  Instant.now().toString());
        return manager.add(type, content, description, metadata);
    }

    /** Convenience overload — no name, description, or extra metadata. */
    public String remember(MemoryType type, String content) {
        return remember(type, content, null, null, null);
    }

    /** Convenience overload — defaults to FEEDBACK type. */
    public String remember(String content) {
        return remember(MemoryType.FEEDBACK, content, null, null, null);
    }

    private void ensureSession() {
        if (currentSessionId == null) {
            currentSessionId = "session_" + Instant.now().toString().replace(":", "-");
        }
    }

    // ── search ────────────────────────────────────────────────────────────────

    /** Keyword search across all persistent types (or restricted to the given subset). */
    public List<MemoryEntry> search(String query, MemoryType... types) {
        return search(query, Integer.MAX_VALUE, types);
    }

    /** Keyword search with result limit. */
    public List<MemoryEntry> search(String query, int limit, MemoryType... types) {
        String q = query.toLowerCase();
        List<MemoryEntry> candidates = types.length == 0
                ? manager.listAll()
                : Arrays.stream(types).flatMap(t -> manager.listByType(t).stream()).toList();
        return candidates.stream()
                .filter(e -> e.content().toLowerCase().contains(q) || e.id().equalsIgnoreCase(q))
                .limit(limit)
                .toList();
    }

    // ── summary ───────────────────────────────────────────────────────────────

    /** All persistent entries grouped by type. */
    public Map<MemoryType, List<MemoryEntry>> summary() {
        Map<MemoryType, List<MemoryEntry>> result = new LinkedHashMap<>();
        for (MemoryType t : MemoryType.values()) {
            result.put(t, manager.listByType(t));
        }
        return result;
    }

    // ── index ─────────────────────────────────────────────────────────────────

    /**
     * Build a MEMORY.md-style index for injection into the model's context.
     *
     * <p>Each line follows the format:
     * <pre>- {name}: {description} [{type}]</pre>
     * where {@code name} falls back to the entry ID and {@code description} falls back
     * to a truncated first line of the content when not explicitly set.
     *
     * <p>Entries are sorted by type name.
     */
    public String buildIndex() {
        List<MemoryEntry> all = manager.listAll().stream()
                .sorted(Comparator.comparing(e -> e.type().name()))
                .toList();
        if (all.isEmpty()) return "(no memories saved)";
        StringBuilder sb = new StringBuilder();
        for (MemoryEntry e : all) {
            String name = e.metadata().getOrDefault("name", e.id());
            String desc = !e.description().isEmpty() ? e.description() : firstLine(e.content());
            sb.append("- ").append(name).append(": ").append(desc)
              .append(" [").append(e.type().name().toLowerCase()).append("]\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String firstLine(String content) {
        String first = content.lines().findFirst().orElse("").strip();
        return first.length() > 80 ? first.substring(0, 77) + "..." : first;
    }

    // ── stats ─────────────────────────────────────────────────────────────────

    /** Aggregate statistics: total, per-type counts, most-accessed ID. */
    public Map<String, Object> stats() {
        List<MemoryEntry> all = manager.listAll();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("total", all.size());
        for (MemoryType t : MemoryType.values()) {
            s.put(t.name().toLowerCase() + "_count", manager.listByType(t).size());
        }
        s.put("most_accessed", all.stream()
                .max(Comparator.comparingInt(MemoryEntry::accessCount))
                .map(MemoryEntry::id).orElse("none"));
        return s;
    }

    // ── update / delete delegates ─────────────────────────────────────────────

    /** Update an existing entry's content. Returns false if not found. */
    public boolean update(String id, String newContent) {
        return manager.update(id, newContent);
    }

    /** Delete an entry by ID. Returns false if not found. */
    public boolean delete(String id) {
        return manager.remove(id);
    }

    /** Retrieve an entry by ID. */
    public java.util.Optional<MemoryEntry> get(String id) {
        return manager.get(id);
    }

    /** All entries of a specific type. */
    public List<MemoryEntry> listByType(MemoryType type) {
        return manager.listByType(type);
    }

    // ── static parse/utility helpers ─────────────────────────────────────────

    public static double parseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try { return Double.parseDouble(value.strip()); }
        catch (NumberFormatException e) { return fallback; }
    }

    public static MemoryType[] parseTypes(String typeParam) {
        if (typeParam == null || typeParam.isBlank()) return new MemoryType[0];
        return Arrays.stream(typeParam.split(","))
                .map(String::strip).filter(s -> !s.isEmpty())
                .map(MemoryType::fromString)
                .toArray(MemoryType[]::new);
    }
}