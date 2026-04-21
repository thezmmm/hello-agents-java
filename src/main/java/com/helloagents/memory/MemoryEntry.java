package com.helloagents.memory;

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
 */
public record MemoryEntry(
        String id,
        MemoryType type,
        String content,
        double importance,
        long createdAt,
        long lastAccessedAt,
        int accessCount
) {
    public MemoryEntry withAccessed(long now) {
        return new MemoryEntry(id, type, content, importance, createdAt, now, accessCount + 1);
    }

    public MemoryEntry withContent(String newContent, double newImportance, long now) {
        return new MemoryEntry(id, type, newContent, newImportance, createdAt, now, accessCount);
    }

    public MemoryEntry withType(MemoryType newType, long now) {
        return new MemoryEntry(id, newType, content, importance, createdAt, now, accessCount);
    }

    @Override
    public String toString() {
        return "[%s|%s|importance=%.2f] %s".formatted(id, type.displayName, importance, content);
    }
}