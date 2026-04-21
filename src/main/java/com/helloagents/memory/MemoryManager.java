package com.helloagents.memory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

/**
 * Memory management layer — coordinates all cognitive memory types.
 *
 * <p>Holds one {@link MemoryStore} per {@link MemoryType} and routes each
 * operation to the appropriate type store. Aggregates cross-type results
 * for callers that need a unified view.
 *
 * <p>Business logic (search ranking, forget strategies, consolidation) belongs
 * in {@link MemoryService}.
 */
public class MemoryManager {

    private final Map<MemoryType, MemoryStore> stores = new EnumMap<>(MemoryType.class);

    /** Default setup: standard implementations for all four types. */
    public MemoryManager() {
        stores.put(MemoryType.PERCEPTUAL, new PerceptualMemory());
        stores.put(MemoryType.WORKING,    new WorkingMemory());
        stores.put(MemoryType.EPISODIC,   new EpisodicMemory());
        stores.put(MemoryType.SEMANTIC,   new SemanticMemory());
    }

    /** Custom setup: supply your own store per type (partial maps are not supported). */
    public MemoryManager(Map<MemoryType, MemoryStore> stores) {
        this.stores.putAll(stores);
    }

    /** Direct access to the store for a specific type (for consolidation, testing, etc.). */
    public MemoryStore storeFor(MemoryType type) {
        return stores.get(type);
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /**
     * Add a new entry to the store for the given type.
     * ID generation and type-specific constraints are handled by each store.
     */
    public String add(MemoryType type, String content, double importance) {
        return add(type, content, importance, Map.of());
    }

    public String add(MemoryType type, String content, double importance, Map<String, String> metadata) {
        MemoryStore store = stores.get(type);
        if (store instanceof WorkingMemory   wm) return wm.add(content, importance, metadata);
        if (store instanceof EpisodicMemory  em) return em.add(content, importance, metadata);
        if (store instanceof SemanticMemory  sm) return sm.add(content, importance, metadata);
        if (store instanceof PerceptualMemory pm) return pm.add(content, importance, metadata);
        throw new IllegalStateException("No add() handler for type: " + type);
    }

    /** Retrieve an entry by ID, searching across all type stores. */
    public Optional<MemoryEntry> get(String id) {
        for (MemoryStore store : stores.values()) {
            Optional<MemoryEntry> found = store.get(id);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /**
     * Replace the content and importance of an existing entry.
     *
     * @return {@code true} if the entry was found and updated
     */
    public boolean update(String id, String newContent, double newImportance) {
        for (MemoryStore store : stores.values()) {
            Optional<MemoryEntry> found = store.get(id);
            if (found.isPresent()) {
                store.save(found.get().withContent(newContent,
                        WorkingMemory.clamp(newImportance), System.currentTimeMillis()));
                return true;
            }
        }
        return false;
    }

    /** Delete an entry by ID from whichever type store holds it. */
    public boolean remove(String id) {
        for (MemoryStore store : stores.values()) {
            if (store.delete(id)) return true;
        }
        return false;
    }

    /** Save an existing entry to the store matching its type (used for consolidation). */
    public void save(MemoryEntry entry) {
        stores.get(entry.type()).save(entry);
    }

    // ── queries ───────────────────────────────────────────────────────────────

    /** All entries for a specific memory type. */
    public List<MemoryEntry> listByType(MemoryType type) {
        return stores.get(type).listAll();
    }

    /** All entries across every type store. */
    public List<MemoryEntry> listAll() {
        return stores.values().stream().flatMap(s -> s.listAll().stream()).toList();
    }

    /** Clear all entries from every type store. */
    public void clearAll() {
        stores.values().forEach(MemoryStore::clear);
    }
}