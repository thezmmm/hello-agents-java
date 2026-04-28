package com.helloagents.memory;

import com.helloagents.memory.core.ForgetStrategy;
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
 * <p>Responsible for search ranking, forget strategies, consolidation rules,
 * and aggregate statistics. Does not manage entry lifecycles directly; delegates
 * all persistence to the underlying {@link MemoryManager}.
 */
public class MemoryService {

    static final double PERCEPTUAL_PROMOTE_THRESHOLD = 0.5;
    static final double WORKING_PROMOTE_THRESHOLD    = 0.7;

    private final MemoryManager manager;
    private String currentSessionId;

    public MemoryService(MemoryManager manager) {
        this.manager = manager;
    }

    public MemoryManager getManager() { return manager; }
    public String getCurrentSessionId() { return currentSessionId; }
    public void setCurrentSessionId(String sessionId) { this.currentSessionId = sessionId; }

    // ── remember ─────────────────────────────────────────────────────────────

    /**
     * Store a new memory entry with full metadata support.
     *
     * <p>Automatically attaches session_id and timestamp to every entry.
     * For PERCEPTUAL memories, {@code filePath} and {@code modality} are recorded
     * in metadata (modality is inferred from the file extension when not provided).
     *
     * @param type      target memory type
     * @param content   text content to remember
     * @param importance relevance weight [0.0, 1.0]
     * @param filePath  optional file path (perceptual memories only)
     * @param modality  optional modality hint; inferred from filePath when absent
     * @param extra     additional metadata key-value pairs
     * @return the generated entry ID
     */
    public String remember(MemoryType type, String content, double importance,
                           String filePath, String modality, Map<String, String> extra) {
        ensureSession();
        Map<String, String> metadata = new HashMap<>(extra != null ? extra : Map.of());

        if (type == MemoryType.PERCEPTUAL && filePath != null) {
            String m = modality != null ? modality : inferModality(filePath);
            metadata.putIfAbsent("modality", m);
            metadata.putIfAbsent("raw_data", filePath);
        }

        metadata.put("session_id", currentSessionId);
        metadata.put("timestamp", Instant.now().toString());

        return manager.add(type, content, importance, metadata);
    }

    /** Convenience overload — no file or extra metadata. */
    public String remember(MemoryType type, String content, double importance) {
        return remember(type, content, importance, null, null, null);
    }

    /** Convenience overload — defaults to WORKING memory with importance 0.5. */
    public String remember(String content) {
        return remember(MemoryType.WORKING, content, 0.5, null, null, null);
    }

    private void ensureSession() {
        if (currentSessionId == null) {
            currentSessionId = "session_" + Instant.now().toString().replace(":", "-");
        }
    }

    private static String inferModality(String filePath) {
        int dot = filePath.lastIndexOf('.');
        if (dot < 0) return "text";
        return switch (filePath.substring(dot + 1).toLowerCase()) {
            case "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "image";
            case "mp3", "wav", "ogg", "flac", "aac"         -> "audio";
            case "mp4", "avi", "mov", "mkv", "webm"         -> "video";
            case "pdf", "doc", "docx", "txt", "md"          -> "document";
            default                                          -> "text";
        };
    }

    // ── search ────────────────────────────────────────────────────────────────

    /**
     * Keyword search, optionally restricted to specific memory types.
     * Results are ranked by importance descending.
     */
    public List<MemoryEntry> search(String query, MemoryType... types) {
        return search(query, Integer.MAX_VALUE, 0.0, types);
    }

    /**
     * Keyword search with limit and minimum importance threshold.
     *
     * @param limit         maximum number of results to return
     * @param minImportance entries below this importance are excluded [0.0, 1.0]
     */
    public List<MemoryEntry> search(String query, int limit, double minImportance, MemoryType... types) {
        String q = query.toLowerCase();
        List<MemoryEntry> candidates = types.length == 0
                ? manager.listAll()
                : Arrays.stream(types).flatMap(t -> manager.listByType(t).stream()).toList();
        return candidates.stream()
                .filter(e -> e.importance() >= minImportance)
                .filter(e -> e.content().toLowerCase().contains(q) || e.id().equalsIgnoreCase(q))
                .sorted(Comparator.comparingDouble(MemoryEntry::importance).reversed())
                .limit(limit)
                .toList();
    }

    // ── summary ───────────────────────────────────────────────────────────────

    /** All entries grouped by memory type, sorted by importance within each group. */
    public Map<MemoryType, List<MemoryEntry>> summary() {
        Map<MemoryType, List<MemoryEntry>> result = new LinkedHashMap<>();
        for (MemoryType t : MemoryType.values()) {
            result.put(t, manager.listByType(t).stream()
                    .sorted(Comparator.comparingDouble(MemoryEntry::importance).reversed())
                    .toList());
        }
        return result;
    }

    // ── stats ─────────────────────────────────────────────────────────────────

    /** Aggregate statistics: total, per-type counts, average importance, most-accessed ID. */
    public Map<String, Object> stats() {
        List<MemoryEntry> all = manager.listAll();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("total", all.size());
        for (MemoryType t : MemoryType.values()) {
            s.put(t.name().toLowerCase() + "_count", manager.listByType(t).size());
        }
        double avg = all.stream().mapToDouble(MemoryEntry::importance).average().orElse(0.0);
        s.put("avg_importance", Math.round(avg * 100.0) / 100.0);
        s.put("most_accessed", all.stream()
                .max(Comparator.comparingInt(MemoryEntry::accessCount))
                .map(MemoryEntry::id).orElse("none"));
        return s;
    }

    // ── forget ────────────────────────────────────────────────────────────────

    /**
     * Evict up to {@code count} entries according to the given strategy.
     *
     * @return the entries that were removed
     */
    public List<MemoryEntry> forget(ForgetStrategy strategy, int count) {
        Comparator<MemoryEntry> order = switch (strategy) {
            case LRU               -> Comparator.comparingLong(MemoryEntry::lastAccessedAt);
            case LFU               -> Comparator.comparingInt(MemoryEntry::accessCount);
            case OLDEST            -> Comparator.comparingLong(MemoryEntry::createdAt);
            case LOWEST_IMPORTANCE -> Comparator.comparingDouble(MemoryEntry::importance);
        };
        List<MemoryEntry> toForget = manager.listAll().stream()
                .sorted(order).limit(count).toList();
        toForget.forEach(e -> manager.remove(e.id()));
        return toForget;
    }

    // ── consolidate ───────────────────────────────────────────────────────────

    /**
     * Promote short-lived memories toward longer-term storage:
     * PERCEPTUAL (importance ≥ 0.5) → WORKING,
     * WORKING (importance ≥ 0.7) → EPISODIC or SEMANTIC.
     *
     * @return the entries that were promoted
     */
    public List<MemoryEntry> consolidate() {
        long now = System.currentTimeMillis();
        List<MemoryEntry> promoted = manager.listAll().stream()
                .filter(this::isPromotable)
                .map(e -> promote(e, now))
                .toList();
        promoted.forEach(e -> {
            manager.remove(e.id());   // remove from old type store
            manager.save(e);          // save to new type store
        });
        return promoted;
    }

    // ── consolidation helpers ─────────────────────────────────────────────────

    private boolean isPromotable(MemoryEntry e) {
        return (e.type() == MemoryType.PERCEPTUAL && e.importance() >= PERCEPTUAL_PROMOTE_THRESHOLD)
            || (e.type() == MemoryType.WORKING    && e.importance() >= WORKING_PROMOTE_THRESHOLD);
    }

    private MemoryEntry promote(MemoryEntry e, long now) {
        MemoryType target = switch (e.type()) {
            case PERCEPTUAL -> MemoryType.WORKING;
            case WORKING    -> looksLikeFact(e.content()) ? MemoryType.SEMANTIC : MemoryType.EPISODIC;
            default         -> e.type();
        };
        return e.withType(target, now);
    }

    // ── static parse/utility helpers (used by tool subpackage) ───────────────

    public static boolean looksLikeFact(String content) {
        String s = content.trim();
        return s.endsWith(".") || s.endsWith("\u3002")
                || s.startsWith("The ") || s.startsWith("A ")
                || s.contains(" is ") || s.contains(" are ")
                || s.contains("\u662f") || s.contains("\u5b9a\u4e49");
    }

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