package com.helloagents.memory;

import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.memory.core.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryManagerTest {

    private MemoryManager manager;

    @BeforeEach
    void setUp() {
        manager = new MemoryManager();
    }

    @Test
    void addAndGetAcrossAllTypes() {
        for (MemoryType type : MemoryType.values()) {
            String id = manager.add(type, "content for " + type, 0.5);
            assertTrue(manager.get(id).isPresent(), "get() should find entry for type " + type);
            assertEquals(type, manager.get(id).get().type());
        }
    }

    @Test
    void getMissingIdReturnsEmpty() {
        assertTrue(manager.get("nonexistent").isEmpty());
    }

    @Test
    void updateChangesContentAndImportance() {
        String id = manager.add(MemoryType.WORKING, "old", 0.3);
        assertTrue(manager.update(id, "new", 0.9));
        MemoryEntry updated = manager.get(id).get();
        assertEquals("new", updated.content());
        assertEquals(0.9, updated.importance(), 0.001);
    }

    @Test
    void updateMissingIdReturnsFalse() {
        assertFalse(manager.update("bad-id", "x", 0.5));
    }

    @Test
    void removeDeletesEntry() {
        String id = manager.add(MemoryType.EPISODIC, "event", 0.6);
        assertTrue(manager.remove(id));
        assertTrue(manager.get(id).isEmpty());
    }

    @Test
    void removeMissingIdReturnsFalse() {
        assertFalse(manager.remove("missing"));
    }

    @Test
    void listByTypeIsolated() {
        manager.add(MemoryType.WORKING, "W", 0.5);
        manager.add(MemoryType.SEMANTIC, "S", 0.5);
        List<MemoryEntry> working = manager.listByType(MemoryType.WORKING);
        assertEquals(1, working.size());
        assertEquals(MemoryType.WORKING, working.get(0).type());
    }

    @Test
    void listAllAggregatesAllTypes() {
        manager.add(MemoryType.WORKING, "W", 0.5);
        manager.add(MemoryType.EPISODIC, "E", 0.5);
        manager.add(MemoryType.SEMANTIC, "S", 0.5);
        assertEquals(3, manager.listAll().size());
    }

    @Test
    void clearAllRemovesEverything() {
        manager.add(MemoryType.WORKING, "W", 0.5);
        manager.add(MemoryType.EPISODIC, "E", 0.5);
        manager.clearAll();
        assertEquals(0, manager.listAll().size());
    }

    @Test
    void saveRoutesToCorrectTypeStore() {
        String id = manager.add(MemoryType.WORKING, "W", 0.5);
        MemoryEntry entry = manager.get(id).get();
        MemoryEntry promoted = entry.withType(MemoryType.SEMANTIC, System.currentTimeMillis());
        manager.remove(id);
        manager.save(promoted);
        assertTrue(manager.get(promoted.id()).isPresent());
        assertEquals(MemoryType.SEMANTIC, manager.get(promoted.id()).get().type());
        assertEquals(0, manager.listByType(MemoryType.WORKING).size());
        assertEquals(1, manager.listByType(MemoryType.SEMANTIC).size());
    }
}