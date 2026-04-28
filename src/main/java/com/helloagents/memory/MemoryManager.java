package com.helloagents.memory;

import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.memory.core.MemoryStore;
import com.helloagents.memory.core.MemoryType;
import com.helloagents.memory.store.InMemoryStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Memory management layer — all four persistent types share one {@link MemoryStore}.
 *
 * <p>Type isolation is maintained by filtering on {@link MemoryEntry#type()};
 * the default implementation is {@link InMemoryStore}.
 */
public class MemoryManager {

    private final MemoryStore store;

    public MemoryManager() {
        this(new InMemoryStore());
    }

    public MemoryManager(MemoryStore store) {
        this.store = store;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public String add(MemoryType type, String content) {
        return add(type, content, null, Map.of());
    }

    public String add(MemoryType type, String content, Map<String, String> metadata) {
        return add(type, content, null, metadata);
    }

    public String add(MemoryType type, String content, String description, Map<String, String> metadata) {
        String id  = newId();
        long   now = System.currentTimeMillis();
        store.save(new MemoryEntry(id, type, content, description, now, now, 0, metadata));
        return id;
    }

    public Optional<MemoryEntry> get(String id) {
        return store.get(id);
    }

    public boolean update(String id, String newContent) {
        Optional<MemoryEntry> found = store.get(id);
        if (found.isEmpty()) return false;
        store.save(found.get().withContent(newContent, System.currentTimeMillis()));
        return true;
    }

    public boolean remove(String id) {
        return store.delete(id);
    }

    public void save(MemoryEntry entry) {
        store.save(entry);
    }

    // ── queries ───────────────────────────────────────────────────────────────

    public List<MemoryEntry> listByType(MemoryType type) {
        return store.listAll().stream().filter(e -> e.type() == type).toList();
    }

    public List<MemoryEntry> listAll() {
        return store.listAll();
    }

    public void clearAll() {
        store.clear();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}