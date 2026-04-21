package com.helloagents.memory;

/**
 * Episodic memory — personal experiences and events, timeline-ordered.
 *
 * <p>Append-friendly; no capacity limit by default. Entries are stored in
 * chronological order and retrieved in the same order.
 */
public class EpisodicMemory extends InMemoryStore {

    public String add(String content, double importance) {
        return add(content, importance, java.util.Map.of());
    }

    public String add(String content, double importance, java.util.Map<String, String> metadata) {
        String id = WorkingMemory.newId();
        long now = System.currentTimeMillis();
        save(new MemoryEntry(id, MemoryType.EPISODIC, content, WorkingMemory.clamp(importance), now, now, 0, metadata));
        return id;
    }
}