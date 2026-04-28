package com.helloagents.memory;

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
        String id = service.remember(MemoryType.PROJECT, "Auth rewrite is compliance-driven.");
        assertNotNull(id);
        assertTrue(manager.get(id).isPresent());
        assertEquals(MemoryType.PROJECT, manager.get(id).get().type());
    }

    @Test
    void rememberShortcutDefaultsToFeedback() {
        String id = service.remember("quick note");
        assertEquals(MemoryType.FEEDBACK, manager.get(id).get().type());
    }

    @Test
    void rememberAttachesSessionIdAndTimestamp() {
        String id = service.remember(MemoryType.FEEDBACK, "correction");
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

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    void searchFindsMatchingContent() {
        manager.add(MemoryType.USER, "User prefers tabs");
        manager.add(MemoryType.USER, "User prefers spaces");
        List<MemoryEntry> results = service.search("tabs");
        assertEquals(1, results.size());
        assertTrue(results.get(0).content().contains("tabs"));
    }

    @Test
    void searchRestrictedToType() {
        manager.add(MemoryType.USER,     "target user pref");
        manager.add(MemoryType.FEEDBACK, "target correction");
        List<MemoryEntry> results = service.search("target", MemoryType.USER);
        assertEquals(1, results.size());
        assertEquals(MemoryType.USER, results.get(0).type());
    }

    @Test
    void searchEmptyWhenNoMatch() {
        manager.add(MemoryType.REFERENCE, "Linear board URL");
        assertTrue(service.search("xyz").isEmpty());
    }

    // ── summary ───────────────────────────────────────────────────────────────

    @Test
    void summaryContainsAllPersistentTypes() {
        Map<MemoryType, List<MemoryEntry>> s = service.summary();
        assertEquals(MemoryType.values().length, s.size());
        for (MemoryType t : MemoryType.values()) {
            assertTrue(s.containsKey(t));
        }
    }

    @Test
    void summaryContainsAddedEntries() {
        manager.add(MemoryType.FEEDBACK, "low");
        manager.add(MemoryType.FEEDBACK, "high");
        assertEquals(2, service.summary().get(MemoryType.FEEDBACK).size());
    }

    // ── stats ─────────────────────────────────────────────────────────────────

    @Test
    void statsReflectsAddedEntries() {
        manager.add(MemoryType.USER,    "U");
        manager.add(MemoryType.PROJECT, "P");
        Map<String, Object> stats = service.stats();
        assertEquals(2, stats.get("total"));
        assertEquals(1, stats.get("user_count"));
        assertEquals(1, stats.get("project_count"));
    }

    // ── buildIndex ────────────────────────────────────────────────────────────

    @Test
    void buildIndexFormatsEntries() {
        service.remember(MemoryType.USER,     "User prefers tabs.", "prefer_tabs",         "User prefers tabs", null);
        service.remember(MemoryType.FEEDBACK, "No mock DB.",        "no_mock_db",          "Avoid mock DB",     null);
        service.remember(MemoryType.PROJECT,  "Compliance driven.", "compliance_rewrite",  "Auth is compliance-driven", null);

        String index = service.buildIndex();
        assertTrue(index.contains("- prefer_tabs: User prefers tabs [user]"));
        assertTrue(index.contains("- no_mock_db: Avoid mock DB [feedback]"));
        assertTrue(index.contains("- compliance_rewrite: Auth is compliance-driven [project]"));
    }

    @Test
    void buildIndexFallsBackToIdAndFirstLine() {
        service.remember(MemoryType.REFERENCE, "https://grafana/dashboard");
        String index = service.buildIndex();
        assertTrue(index.contains("[reference]"));
        assertTrue(index.contains("https://grafana/dashboard"));
    }

    @Test
    void buildIndexEmptyReturnsPlaceholder() {
        assertEquals("(no memories saved)", service.buildIndex());
    }

    // ── static helpers ────────────────────────────────────────────────────────

    @Test
    void parseDoubleHandlesInvalid() {
        assertEquals(0.5, MemoryService.parseDouble("bad", 0.5));
        assertEquals(0.7, MemoryService.parseDouble("0.7", 0.5));
    }

    @Test
    void parseTypesReturnsCorrectTypes() {
        MemoryType[] types = MemoryService.parseTypes("user,feedback");
        assertEquals(2, types.length);
        assertEquals(MemoryType.USER,     types[0]);
        assertEquals(MemoryType.FEEDBACK, types[1]);
    }
}