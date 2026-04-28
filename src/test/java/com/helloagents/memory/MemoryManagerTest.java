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
    void addAndGetAcrossAllPersistentTypes() {
        for (MemoryType type : MemoryType.values()) {
            String id = manager.add(type, "content for " + type);
            assertTrue(manager.get(id).isPresent(), "get() should find entry for type " + type);
            assertEquals(type, manager.get(id).get().type());
        }
    }

    @Test
    void getMissingIdReturnsEmpty() {
        assertTrue(manager.get("nonexistent").isEmpty());
    }

    @Test
    void updateChangesContent() {
        String id = manager.add(MemoryType.FEEDBACK, "old");
        assertTrue(manager.update(id, "new"));
        assertEquals("new", manager.get(id).get().content());
    }

    @Test
    void updateMissingIdReturnsFalse() {
        assertFalse(manager.update("bad-id", "x"));
    }

    @Test
    void removeDeletesEntry() {
        String id = manager.add(MemoryType.PROJECT, "convention");
        assertTrue(manager.remove(id));
        assertTrue(manager.get(id).isEmpty());
    }

    @Test
    void removeMissingIdReturnsFalse() {
        assertFalse(manager.remove("missing"));
    }

    @Test
    void listByTypeIsolated() {
        manager.add(MemoryType.USER,     "U");
        manager.add(MemoryType.FEEDBACK, "F");
        List<MemoryEntry> users = manager.listByType(MemoryType.USER);
        assertEquals(1, users.size());
        assertEquals(MemoryType.USER, users.get(0).type());
    }

    @Test
    void listAllAggregatesAllPersistentTypes() {
        manager.add(MemoryType.USER,      "U");
        manager.add(MemoryType.FEEDBACK,  "F");
        manager.add(MemoryType.PROJECT,   "P");
        manager.add(MemoryType.REFERENCE, "R");
        assertEquals(4, manager.listAll().size());
    }

    @Test
    void clearAllRemovesEverything() {
        manager.add(MemoryType.USER,     "U");
        manager.add(MemoryType.FEEDBACK, "F");
        manager.clearAll();
        assertEquals(0, manager.listAll().size());
    }

    @Test
    void saveRoutesToCorrectTypeStore() {
        String id = manager.add(MemoryType.FEEDBACK, "original");
        MemoryEntry entry = manager.get(id).get();
        MemoryEntry moved = entry.withType(MemoryType.PROJECT, System.currentTimeMillis());
        manager.remove(id);
        manager.save(moved);
        assertTrue(manager.get(moved.id()).isPresent());
        assertEquals(MemoryType.PROJECT, manager.get(moved.id()).get().type());
        assertEquals(0, manager.listByType(MemoryType.FEEDBACK).size());
        assertEquals(1, manager.listByType(MemoryType.PROJECT).size());
    }
}