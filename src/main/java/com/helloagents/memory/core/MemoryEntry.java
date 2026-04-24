package com.helloagents.memory.core;

import java.util.Map;

/**
 * An immutable snapshot of a single memory record.
 *
 * @param id             unique identifier (UUID short form)
 * @param type           cognitive memory type
 * @param content        the actual memory content
 * @param importance     relevance weight in [0.0, 1.0]
 * @param createdAt      epoch-millis when first stored
 * @param lastAccessedAt epoch-millis of last retrieval or update
 * @param accessCount    number of times this entry has been read
 * @param metadata       arbitrary key-value annotations (session_id, timestamp, modality, etc.)
 */
public record MemoryEntry(
        String id,
        MemoryType type,
        String content,
        double importance,
        long createdAt,
        long lastAccessedAt,
        int accessCount,
        Map<String, String> metadata
) {
    public MemoryEntry {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /** Convenience factory — no metadata. */
    public static MemoryEntry of(String id, MemoryType type, String content,
                                 double importance, long createdAt, long lastAccessedAt, int accessCount) {
        return new MemoryEntry(id, type, content, importance, createdAt, lastAccessedAt, accessCount, Map.of());
    }

    public MemoryEntry withAccessed(long now) {
        return new MemoryEntry(id, type, content, importance, createdAt, now, accessCount + 1, metadata);
    }

    public MemoryEntry withContent(String newContent, double newImportance, long now) {
        return new MemoryEntry(id, type, newContent, newImportance, createdAt, now, accessCount, metadata);
    }

    public MemoryEntry withType(MemoryType newType, long now) {
        return new MemoryEntry(id, newType, content, importance, createdAt, now, accessCount, metadata);
    }

    @Override
    public String toString() {
        String meta = metadata.isEmpty() ? "" : " " + metadata;
        return "[%s|%s|importance=%.2f]%s %s".formatted(id, type.displayName, importance, meta, content);
    }
}