package com.helloagents.memory;

/**
 * Semantic memory — facts, rules, and general knowledge; long-term and stable.
 *
 * <p>No capacity limit. Future implementations can add keyword indexing or
 * vector-store backing for similarity search.
 */
public class SemanticMemory extends InMemoryStore {

    public String add(String content, double importance) {
        return add(content, importance, java.util.Map.of());
    }

    public String add(String content, double importance, java.util.Map<String, String> metadata) {
        String id = WorkingMemory.newId();
        long now = System.currentTimeMillis();
        save(new MemoryEntry(id, MemoryType.SEMANTIC, content, WorkingMemory.clamp(importance), now, now, 0, metadata));
        return id;
    }
}