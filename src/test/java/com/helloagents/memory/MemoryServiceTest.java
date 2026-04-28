package com.helloagents.memory;

import com.helloagents.memory.core.ForgetStrategy;
import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.memory.core.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryServiceTest {

    private MemoryManager manager;
    private MemoryService  service;

    @BeforeEach
    void setUp() {
        manager = new MemoryManager();
        service = new MemoryService(manager);
    }

    // ── remember ─────────────────────────────────────────────────────────────

    @Test
    void rememberWithTypeStoresEntry() {
        String id = service.remember(MemoryType.SEMANTIC, "Java is a language.", 0.9);
        assertNotNull(id);
        assertTrue(manager.get(id).isPresent());
        assertEquals(MemoryType.SEMANTIC, manager.get(id).get().type());
    }

    @Test
    void rememberShortcutDefaultsToWorking() {
        String id = service.remember("quick note");
        assertEquals(MemoryType.WORKING, manager.get(id).get().type());
        assertEquals(0.5, manager.get(id).get().importance(), 0.001);
    }

    @Test
    void rememberAttachesSessionIdAndTimestamp() {
        String id = service.remember(MemoryType.WORKING, "task", 0.5);
        Map<String, String> meta = manager.get(id).get().metadata();
        assertTrue(meta.containsKey("session_id"));
        assertTrue(meta.containsKey("timestamp"));
    }

    @Test
    void rememberAutoGeneratesSessionId() {
        assertNull(service.getCurrentSessionId());
        service.remember("first");
        assertNotNull(service.getCurrentSessionId());
        assertTrue(service.getCurrentSessionId().startsWith("session_"));
    }

    @Test
    void rememberReusesSessionIdAcrossCalls() {
        service.remember("A");
        String sessionId = service.getCurrentSessionId();
        service.remember("B");
        assertEquals(sessionId, service.getCurrentSessionId());
        assertEquals(sessionId, manager.listAll().get(1).metadata().get("session_id"));
    }

    @Test
    void rememberPerceptualWithFilePathAttachesModality() {
        String id = service.remember(MemoryType.PERCEPTUAL, "cat photo", 0.6,
                                     "photo.jpg", null, null);
        Map<String, String> meta = manager.get(id).get().metadata();
        assertEquals("image",    meta.get("modality"));
        assertEquals("photo.jpg", meta.get("raw_data"));
    }

    @Test
    void rememberPerceptualModalityInferredFromExtension() {
        String id = service.remember(MemoryType.PERCEPTUAL, "podcast", 0.5, "ep1.mp3", null, null);
        assertEquals("audio", manager.get(id).get().metadata().get("modality"));
    }

    @Test
    void rememberExplicitModalityNotOverridden() {
        String id = service.remember(MemoryType.PERCEPTUAL, "doc", 0.5, "file.pdf", "document", null);
        assertEquals("document", manager.get(id).get().metadata().get("modality"));
    }

    @Test
    void rememberNonPerceptualIgnoresFilePath() {
        String id = service.remember(MemoryType.SEMANTIC, "fact", 0.8, "file.jpg", null, null);
        Map<String, String> meta = manager.get(id).get().metadata();
        assertFalse(meta.containsKey("modality"));
        assertFalse(meta.containsKey("raw_data"));
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    void searchFindsMatchingContent() {
        manager.add(MemoryType.SEMANTIC, "Java is a language", 0.8);
        manager.add(MemoryType.SEMANTIC, "Python is a language", 0.6);
        List<MemoryEntry> results = service.search("Java");
        assertEquals(1, results.size());
        assertTrue(results.get(0).content().contains("Java"));
    }

    @Test
    void searchRanksByImportanceDescending() {
        manager.add(MemoryType.SEMANTIC, "alpha", 0.3);
        manager.add(MemoryType.SEMANTIC, "alpha beta", 0.9);
        List<MemoryEntry> results = service.search("alpha");
        assertEquals(0.9, results.get(0).importance(), 0.001);
    }

    @Test
    void searchRestrictedToType() {
        manager.add(MemoryType.WORKING,  "target working", 0.5);
        manager.add(MemoryType.EPISODIC, "target episodic", 0.5);
        List<MemoryEntry> results = service.search("target", MemoryType.WORKING);
        assertEquals(1, results.size());
        assertEquals(MemoryType.WORKING, results.get(0).type());
    }

    @Test
    void searchEmptyWhenNoMatch() {
        manager.add(MemoryType.SEMANTIC, "nothing relevant", 0.5);
        assertTrue(service.search("xyz").isEmpty());
    }

    // ── summary ───────────────────────────────────────────────────────────────

    @Test
    void summaryContainsAllTypes() {
        Map<MemoryType, List<MemoryEntry>> s = service.summary();
        assertEquals(MemoryType.values().length, s.size());
    }

    @Test
    void summaryEntriesOrderedByImportance() {
        manager.add(MemoryType.WORKING, "low",  0.2);
        manager.add(MemoryType.WORKING, "high", 0.9);
        List<MemoryEntry> working = service.summary().get(MemoryType.WORKING);
        assertEquals(0.9, working.get(0).importance(), 0.001);
    }

    // ── stats ─────────────────────────────────────────────────────────────────

    @Test
    void statsReflectsAddedEntries() {
        manager.add(MemoryType.WORKING,  "W", 0.5);
        manager.add(MemoryType.EPISODIC, "E", 0.8);
        Map<String, Object> stats = service.stats();
        assertEquals(2, stats.get("total"));
        assertEquals(1, stats.get("working_count"));
        assertEquals(1, stats.get("episodic_count"));
    }

    @Test
    void statsAvgImportance() {
        manager.add(MemoryType.SEMANTIC, "A", 0.4);
        manager.add(MemoryType.SEMANTIC, "B", 0.6);
        Map<String, Object> stats = service.stats();
        assertEquals(0.5, (Double) stats.get("avg_importance"), 0.01);
    }

    // ── forget ────────────────────────────────────────────────────────────────

    @Test
    void forgetLruRemovesLeastRecentlyAccessed() throws InterruptedException {
        String old = manager.add(MemoryType.WORKING, "old", 0.5);
        Thread.sleep(5);
        manager.add(MemoryType.WORKING, "new", 0.5);
        List<MemoryEntry> forgotten = service.forget(ForgetStrategy.LRU, 1);
        assertEquals(1, forgotten.size());
        assertEquals(old, forgotten.get(0).id());
        assertTrue(manager.get(old).isEmpty());
    }

    @Test
    void forgetLowestImportanceRemovesSmallest() {
        manager.add(MemoryType.SEMANTIC, "high", 0.9);
        String low = manager.add(MemoryType.SEMANTIC, "low", 0.1);
        service.forget(ForgetStrategy.LOWEST_IMPORTANCE, 1);
        assertTrue(manager.get(low).isEmpty());
        assertEquals(1, manager.listAll().size());
    }

    @Test
    void forgetCountLimitsRemovals() {
        manager.add(MemoryType.SEMANTIC, "A", 0.1);
        manager.add(MemoryType.SEMANTIC, "B", 0.2);
        manager.add(MemoryType.SEMANTIC, "C", 0.3);
        service.forget(ForgetStrategy.LOWEST_IMPORTANCE, 2);
        assertEquals(1, manager.listAll().size());
    }

    // ── consolidate ───────────────────────────────────────────────────────────

    @Test
    void consolidatePromotesPerceptualToWorking() {
        manager.add(MemoryType.PERCEPTUAL, "hot stimulus", MemoryService.PERCEPTUAL_PROMOTE_THRESHOLD);
        List<MemoryEntry> promoted = service.consolidate();
        assertEquals(1, promoted.size());
        assertEquals(MemoryType.WORKING, promoted.get(0).type());
        assertEquals(0, manager.listByType(MemoryType.PERCEPTUAL).size());
        assertEquals(1, manager.listByType(MemoryType.WORKING).size());
    }

    @Test
    void consolidatePromotesWorkingFact() {
        manager.add(MemoryType.WORKING, "Java is a programming language.", MemoryService.WORKING_PROMOTE_THRESHOLD);
        List<MemoryEntry> promoted = service.consolidate();
        assertEquals(1, promoted.size());
        assertEquals(MemoryType.SEMANTIC, promoted.get(0).type());
    }

    @Test
    void consolidatePromotesWorkingEpisode() {
        manager.add(MemoryType.WORKING, "yesterday I deployed to prod", MemoryService.WORKING_PROMOTE_THRESHOLD);
        List<MemoryEntry> promoted = service.consolidate();
        assertEquals(1, promoted.size());
        assertEquals(MemoryType.EPISODIC, promoted.get(0).type());
    }

    @Test
    void consolidateLeavesLowImportanceAlone() {
        manager.add(MemoryType.PERCEPTUAL, "faint signal", 0.1);
        List<MemoryEntry> promoted = service.consolidate();
        assertTrue(promoted.isEmpty());
        assertEquals(1, manager.listByType(MemoryType.PERCEPTUAL).size());
    }

    // ── static helpers ────────────────────────────────────────────────────────

    @Test
    void looksLikeFactDetectsPatterns() {
        assertTrue(MemoryService.looksLikeFact("The sky is blue."));
        assertTrue(MemoryService.looksLikeFact("A cat is an animal"));
        assertTrue(MemoryService.looksLikeFact("Java is a language"));
        assertFalse(MemoryService.looksLikeFact("yesterday I deployed"));
    }

    @Test
    void parseDoubleHandlesInvalid() {
        assertEquals(0.5, MemoryService.parseDouble("bad", 0.5));
        assertEquals(0.7, MemoryService.parseDouble("0.7", 0.5));
    }
}