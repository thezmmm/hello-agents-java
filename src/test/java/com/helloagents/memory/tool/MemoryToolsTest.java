package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryManager;
import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryToolsTest {

    private MemoryManager manager;
    private MemoryService  service;

    private MemoryAddTool         addTool;
    private MemoryUpdateTool      updateTool;
    private MemoryRemoveTool      removeTool;
    private MemoryClearTool       clearTool;
    private MemorySearchTool      searchTool;
    private MemorySummaryTool     summaryTool;
    private MemoryStatsTool       statsTool;
    private MemoryForgetTool      forgetTool;
    private MemoryConsolidateTool consolidateTool;

    @BeforeEach
    void setUp() {
        manager         = new MemoryManager();
        service         = new MemoryService(manager);
        addTool         = new MemoryAddTool(service);
        updateTool      = new MemoryUpdateTool(manager);
        removeTool      = new MemoryRemoveTool(manager);
        clearTool       = new MemoryClearTool(manager);
        searchTool      = new MemorySearchTool(service);
        summaryTool     = new MemorySummaryTool(service);
        statsTool       = new MemoryStatsTool(service);
        forgetTool      = new MemoryForgetTool(service);
        consolidateTool = new MemoryConsolidateTool(service);
    }

    // ── memory_add ────────────────────────────────────────────────────────────

    @Test
    void addToolReturnsId() {
        String result = addTool.execute(Map.of("type", "working", "content", "task A", "importance", "0.7"));
        assertTrue(result.contains("id="));
        assertFalse(result.startsWith("Error:"));
    }

    @Test
    void addToolMissingContentReturnsError() {
        String result = addTool.execute(Map.of("type", "working"));
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void addToolInvalidTypeReturnsError() {
        String result = addTool.execute(Map.of("type", "bogus", "content", "x"));
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void addToolDefaultsToWorking() {
        String result = addTool.execute(Map.of("content", "hello"));
        assertEquals(1, manager.listByType(MemoryType.WORKING).size());
        assertFalse(result.startsWith("Error:"));
    }

    @Test
    void addToolPerceptualWithFilePath() {
        addTool.execute(Map.of("type", "perceptual", "content", "dog photo", "importance", "0.7", "file_path", "dog.jpg"));
        var entry = manager.listByType(MemoryType.PERCEPTUAL).get(0);
        assertEquals("image", entry.metadata().get("modality"));
        assertEquals("dog.jpg", entry.metadata().get("raw_data"));
    }

    @Test
    void addToolAttachesSessionId() {
        addTool.execute(Map.of("type", "working", "content", "task", "importance", "0.5"));
        var entry = manager.listByType(MemoryType.WORKING).get(0);
        assertTrue(entry.metadata().containsKey("session_id"));
        assertTrue(entry.metadata().containsKey("timestamp"));
    }

    // ── memory_update ─────────────────────────────────────────────────────────

    @Test
    void updateToolChangesEntry() {
        String addResult = addTool.execute(Map.of("type", "semantic", "content", "old", "importance", "0.3"));
        String id = addResult.split("id=")[1].split(" ")[0];
        String result = updateTool.execute(Map.of("id", id, "content", "new", "importance", "0.9"));
        assertFalse(result.startsWith("Error:"));
        assertEquals("new", manager.get(id).get().content());
    }

    @Test
    void updateToolMissingIdReturnsError() {
        String result = updateTool.execute(Map.of("content", "x", "importance", "0.5"));
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void updateToolNotFoundReturnsError() {
        String result = updateTool.execute(Map.of("id", "nope", "content", "x", "importance", "0.5"));
        assertTrue(result.contains("not found") || result.startsWith("Error:"));
    }

    // ── memory_remove ─────────────────────────────────────────────────────────

    @Test
    void removeToolDeletesEntry() {
        String addResult = addTool.execute(Map.of("type", "episodic", "content", "event", "importance", "0.5"));
        String id = addResult.split("id=")[1].split(" ")[0];
        String result = removeTool.execute(Map.of("id", id));
        assertFalse(result.startsWith("Error:"));
        assertTrue(manager.get(id).isEmpty());
    }

    @Test
    void removeToolMissingIdReturnsError() {
        String result = removeTool.execute(Map.of());
        assertTrue(result.startsWith("Error:"));
    }

    // ── memory_clear ──────────────────────────────────────────────────────────

    @Test
    void clearToolRemovesAll() {
        addTool.execute(Map.of("type", "working", "content", "A", "importance", "0.5"));
        addTool.execute(Map.of("type", "semantic", "content", "B", "importance", "0.5"));
        clearTool.execute(Map.of());
        assertEquals(0, manager.listAll().size());
    }

    // ── memory_search ─────────────────────────────────────────────────────────

    @Test
    void searchToolFindsMatch() {
        addTool.execute(Map.of("type", "semantic", "content", "Java programming", "importance", "0.8"));
        String result = searchTool.execute(Map.of("query", "Java"));
        assertTrue(result.contains("Java"));
        assertTrue(result.contains("Found 1"));
    }

    @Test
    void searchToolNoMatchMessage() {
        String result = searchTool.execute(Map.of("query", "xyz"));
        assertTrue(result.contains("No memories found"));
    }

    @Test
    void searchToolMissingQueryReturnsError() {
        String result = searchTool.execute(Map.of());
        assertTrue(result.startsWith("Error:"));
    }

    // ── memory_stats ──────────────────────────────────────────────────────────

    @Test
    void statsToolShowsTotal() {
        addTool.execute(Map.of("type", "working", "content", "X", "importance", "0.5"));
        String result = statsTool.execute(Map.of());
        assertTrue(result.contains("total"));
        assertTrue(result.contains("1"));
    }

    // ── memory_summary ────────────────────────────────────────────────────────

    @Test
    void summaryToolListsAllTypes() {
        String result = summaryTool.execute(Map.of());
        for (MemoryType type : MemoryType.values()) {
            assertTrue(result.contains(type.displayName), "summary should mention " + type.displayName);
        }
    }

    // ── memory_forget ─────────────────────────────────────────────────────────

    @Test
    void forgetToolEvictsEntries() {
        addTool.execute(Map.of("type", "semantic", "content", "A", "importance", "0.1"));
        addTool.execute(Map.of("type", "semantic", "content", "B", "importance", "0.9"));
        String result = forgetTool.execute(Map.of("strategy", "lowest_importance", "count", "1"));
        assertFalse(result.startsWith("Error:"));
        assertEquals(1, manager.listAll().size());
        assertEquals("B", manager.listAll().get(0).content());
    }

    @Test
    void forgetToolDefaultsToLruCount1() {
        addTool.execute(Map.of("type", "working", "content", "only", "importance", "0.5"));
        String result = forgetTool.execute(Map.of());
        assertFalse(result.startsWith("Error:"));
    }

    // ── memory_consolidate ────────────────────────────────────────────────────

    @Test
    void consolidateToolPromotesEligibleEntries() {
        addTool.execute(Map.of("type", "perceptual", "content", "hot signal", "importance", "0.8"));
        String result = consolidateTool.execute(Map.of());
        assertTrue(result.contains("1") || result.contains("promoted"));
        assertEquals(0, manager.listByType(MemoryType.PERCEPTUAL).size());
        assertEquals(1, manager.listByType(MemoryType.WORKING).size());
    }

    @Test
    void consolidateToolNothingToPromote() {
        addTool.execute(Map.of("type", "perceptual", "content", "faint", "importance", "0.1"));
        String result = consolidateTool.execute(Map.of());
        assertTrue(result.contains("No memories") || result.contains("eligible"));
    }

    // ── MemoryToolkit ─────────────────────────────────────────────────────────

    @Test
    void toolkitRegistersNineTools() {
        MemoryToolkit kit = new MemoryToolkit();
        assertEquals(9, kit.getTools().size());
    }

    @Test
    void toolkitToolNamesAreUnique() {
        MemoryToolkit kit = new MemoryToolkit();
        long distinct = kit.getTools().stream().map(t -> t.name()).distinct().count();
        assertEquals(9, distinct);
    }
}