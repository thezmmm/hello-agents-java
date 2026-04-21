package com.helloagents.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * General-purpose {@link MemoryStore} backed by a {@link LinkedHashMap}.
 * Preserves insertion order. Not thread-safe.
 *
 * <p>Serves as the base implementation for all cognitive type stores
 * ({@code WorkingMemory}, {@code EpisodicMemory}, etc.).
 * Subclasses override to add type-specific constraints or retrieval logic.
 */
public class InMemoryStore implements MemoryStore {

    private final Map<String, MemoryEntry> entries = new LinkedHashMap<>();

    @Override
    public void save(MemoryEntry entry) {
        entries.put(entry.id(), entry);
    }

    @Override
    public Optional<MemoryEntry> get(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public boolean delete(String id) {
        return entries.remove(id) != null;
    }

    @Override
    public List<MemoryEntry> listAll() {
        return List.copyOf(entries.values());
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public int size() {
        return entries.size();
    }
}