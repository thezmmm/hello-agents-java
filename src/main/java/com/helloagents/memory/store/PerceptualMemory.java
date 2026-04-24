package com.helloagents.memory.store;

import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.memory.core.MemoryType;

import java.util.Map;

/**
 * Perceptual memory — immediate sensory input, ultra-short-lived.
 *
 * <p>Ring-buffer with a fixed capacity (default 5). When full, the oldest
 * entry is overwritten by the newest perception.
 */
public class PerceptualMemory extends InMemoryStore {

    public static final int DEFAULT_CAPACITY = 5;

    private final int capacity;

    public PerceptualMemory() {
        this(DEFAULT_CAPACITY);
    }

    public PerceptualMemory(int capacity) {
        this.capacity = capacity;
    }

    public String add(String content, double importance) {
        return add(content, importance, Map.of());
    }

    public String add(String content, double importance, Map<String, String> metadata) {
        String id = WorkingMemory.newId();
        long now = System.currentTimeMillis();
        if (size() >= capacity) evictOldest();
        save(new MemoryEntry(id, MemoryType.PERCEPTUAL, content, WorkingMemory.clamp(importance), now, now, 0, metadata));
        return id;
    }

    private void evictOldest() {
        listAll().stream().findFirst().ifPresent(e -> delete(e.id()));
    }
}