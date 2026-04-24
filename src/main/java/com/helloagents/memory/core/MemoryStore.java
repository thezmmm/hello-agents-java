package com.helloagents.memory.core;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for memory entries of a single cognitive type.
 *
 * <p>Each {@link MemoryType} maps to one {@code MemoryStore} implementation
 * ({@code WorkingMemory}, {@code EpisodicMemory}, etc.) that handles its own
 * storage and type-specific constraints.
 * {@link com.helloagents.memory.MemoryManager} holds one instance per type and routes operations
 * to the appropriate store.
 */
public interface MemoryStore {

    /** Persist an entry, overwriting any existing entry with the same ID. */
    void save(MemoryEntry entry);

    /** Retrieve an entry by ID. No side effects. */
    Optional<MemoryEntry> get(String id);

    /** Delete the entry with the given ID. Returns {@code true} if it existed. */
    boolean delete(String id);

    /** Return a snapshot of all entries managed by this store. */
    List<MemoryEntry> listAll();

    /** Remove every entry. */
    void clear();

    /** Number of entries currently stored. */
    int size();
}