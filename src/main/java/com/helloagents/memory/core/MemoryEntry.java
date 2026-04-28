package com.helloagents.memory.core;

import java.util.Map;

/**
 * An immutable snapshot of a single memory record.
 *
 * @param id             unique identifier (UUID short form)
 * @param type           cognitive memory type
 * @param content        the actual memory content
 * @param description    one-liner summary shown in the index (empty string if not set)
 * @param createdAt      epoch-millis when first stored
 * @param lastAccessedAt epoch-millis of last retrieval or update
 * @param accessCount    number of times this entry has been read
 * @param metadata       arbitrary key-value annotations (session_id, timestamp, name, etc.)
 */
public record MemoryEntry(
        String id,
        MemoryType type,
        String content,
        String description,
        long createdAt,
        long lastAccessedAt,
        int accessCount,
        Map<String, String> metadata
) {
    public MemoryEntry {
        description = description != null ? description.strip() : "";
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /** Convenience factory — no metadata. */
    public static MemoryEntry of(String id, MemoryType type, String content, String description,
                                 long createdAt, long lastAccessedAt, int accessCount) {
        return new MemoryEntry(id, type, content, description, createdAt, lastAccessedAt, accessCount, Map.of());
    }

    public MemoryEntry withAccessed(long now) {
        return new MemoryEntry(id, type, content, description, createdAt, now, accessCount + 1, metadata);
    }

    public MemoryEntry withContent(String newContent, long now) {
        return new MemoryEntry(id, type, newContent, description, createdAt, now, accessCount, metadata);
    }

    public MemoryEntry withType(MemoryType newType, long now) {
        return new MemoryEntry(id, newType, content, description, createdAt, now, accessCount, metadata);
    }

    @Override
    public String toString() {
        String name = metadata.getOrDefault("name", id);
        return "[%s|%s] %s".formatted(name, type.displayName, content);
    }
}