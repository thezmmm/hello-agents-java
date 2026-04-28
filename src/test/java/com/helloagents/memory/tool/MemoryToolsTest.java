package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryManager;
import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryToolsTest {

    private MemoryService     service;
    private MemoryAddTool     addTool;
    private MemorySearchTool  searchTool;
    private MemorySummaryTool summaryTool;
    private MemoryUpdateTool  updateTool;
    private MemoryRemoveTool  removeTool;

    @BeforeEach
    void setUp() {
        service     = new MemoryService(new MemoryManager());
        addTool     = new MemoryAddTool(service);
        searchTool  = new MemorySearchTool(service);
        summaryTool = new MemorySummaryTool(service);
        updateTool  = new MemoryUpdateTool(service);
        removeTool  = new MemoryRemoveTool(service);
    }

    // ── save_memory ───────────────────────────────────────────────────────────

    @Test
    void saveReturnsId() {
        String result = addTool.execute(Map.of(
                "name", "no_mock_db", "description", "Do not mock the database",
                "type", "feedback", "content", "Do not mock DB"));
        assertTrue(result.contains("id="));
        assertFalse(result.startsWith("Error:"));
    }

    @Test
    void saveMissingNameReturnsError() {
        String result = addTool.execute(Map.of("type", "user", "description", "d", "content", "c"));
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void saveMissingContentReturnsError() {
        String result = addTool.execute(Map.of("name", "n", "description", "d", "type", "user"));
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void saveInvalidTypeReturnsError() {
        String result = addTool.execute(Map.of("name", "n", "description", "d", "type", "bogus", "content", "x"));
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void saveStoresNameAndDescriptionInMetadata() {
        addTool.execute(Map.of(
                "name", "prefer_tabs", "description", "User prefers tabs",
                "type", "user", "content", "User prefers tabs over spaces."));
        var entry = service.listByType(MemoryType.USER).get(0);
        assertEquals("prefer_tabs",      entry.metadata().get("name"));
        assertEquals("User prefers tabs", entry.description());
    }

    @Test
    void saveAttachesSessionId() {
        addTool.execute(Map.of(
                "name", "n", "description", "d",
                "type", "project", "content", "convention"));
        var entry = service.listByType(MemoryType.PROJECT).get(0);
        assertTrue(entry.metadata().containsKey("session_id"));
        assertTrue(entry.metadata().containsKey("timestamp"));
    }

    // ── search_memory ─────────────────────────────────────────────────────────

    @Test
    void searchFindsMatch() {
        addTool.execute(Map.of("name", "compliance_rewrite", "description", "Auth rewrite is compliance-driven",
                "type", "project", "content", "compliance-driven rewrite"));
        String result = searchTool.execute(Map.of("query", "compliance"));
        assertTrue(result.contains("compliance"));
        assertTrue(result.contains("Found 1"));
    }

    @Test
    void searchNoMatchMessage() {
        String result = searchTool.execute(Map.of("query", "xyz"));
        assertTrue(result.contains("No memories found"));
    }

    @Test
    void searchMissingQueryReturnsError() {
        String result = searchTool.execute(Map.of());
        assertTrue(result.startsWith("Error:"));
    }

    // ── list_memories ─────────────────────────────────────────────────────────

    @Test
    void listMemoriesContainsAllTypes() {
        String result = summaryTool.execute(Map.of());
        for (MemoryType type : MemoryType.values()) {
            assertTrue(result.contains(type.displayName), "list should mention " + type.displayName);
        }
    }

    // ── update_memory ─────────────────────────────────────────────────────────

    @Test
    void updateChangesEntry() {
        String addResult = addTool.execute(Map.of("name", "n", "description", "d", "type", "user", "content", "old"));
        String id = addResult.split("id=")[1].split(" ")[0];
        String result = updateTool.execute(Map.of("id", id, "content", "new"));
        assertFalse(result.startsWith("Error:"));
        assertEquals("new", service.get(id).get().content());
    }

    @Test
    void updateMissingIdReturnsError() {
        String result = updateTool.execute(Map.of("content", "x"));
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void updateNotFoundReturnsError() {
        String result = updateTool.execute(Map.of("id", "nope", "content", "x"));
        assertTrue(result.contains("not found") || result.startsWith("Error:"));
    }

    // ── delete_memory ─────────────────────────────────────────────────────────

    @Test
    void deleteRemovesEntry() {
        String addResult = addTool.execute(Map.of("name", "linear_ingest", "description", "Pipeline bugs in Linear INGEST",
                "type", "reference", "content", "Linear INGEST"));
        String id = addResult.split("id=")[1].split(" ")[0];
        String result = removeTool.execute(Map.of("id", id));
        assertFalse(result.startsWith("Error:"));
        assertTrue(service.get(id).isEmpty());
    }

    @Test
    void deleteMissingIdReturnsError() {
        String result = removeTool.execute(Map.of());
        assertTrue(result.startsWith("Error:"));
    }

    // ── MemoryToolkit ─────────────────────────────────────────────────────────

    @Test
    void toolkitRegistersFiveTools() {
        MemoryToolkit kit = new MemoryToolkit();
        assertEquals(5, kit.getTools().size());
    }

    @Test
    void toolkitToolNamesAreUnique() {
        MemoryToolkit kit = new MemoryToolkit();
        long distinct = kit.getTools().stream().map(t -> t.name()).distinct().count();
        assertEquals(5, distinct);
    }

    @Test
    void toolkitToolNamesMatchTutorial() {
        MemoryToolkit kit = new MemoryToolkit();
        var names = kit.getTools().stream().map(t -> t.name()).toList();
        assertTrue(names.contains("save_memory"));
        assertTrue(names.contains("search_memory"));
        assertTrue(names.contains("list_memories"));
        assertTrue(names.contains("update_memory"));
        assertTrue(names.contains("delete_memory"));
    }
}