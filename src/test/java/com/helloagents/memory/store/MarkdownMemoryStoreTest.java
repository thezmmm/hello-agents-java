package com.helloagents.memory.store;

import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.memory.core.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownMemoryStoreTest {

    @TempDir Path tempDir;

    private MarkdownMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new MarkdownMemoryStore(tempDir);
    }

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    void saveAndGetRoundTrip() {
        MemoryEntry e = entry("abc12345", MemoryType.USER, "User prefers tabs.",
                "Tabs preference", Map.of("name", "prefer_tabs", "session_id", "s1"));
        store.save(e);
        MemoryEntry loaded = store.get("abc12345").orElseThrow();
        assertEquals(e.id(),          loaded.id());
        assertEquals(e.type(),        loaded.type());
        assertEquals(e.content(),     loaded.content());
        assertEquals(e.description(), loaded.description());
        assertEquals(e.createdAt(),   loaded.createdAt());
        assertEquals(e.accessCount(), loaded.accessCount());
        assertEquals("prefer_tabs",   loaded.metadata().get("name"));
        assertEquals("s1",            loaded.metadata().get("session_id"));
    }

    @Test
    void getMissingReturnsEmpty() {
        assertTrue(store.get("nonexistent").isEmpty());
    }

    @Test
    void deleteRemovesFile() {
        store.save(entry("del01", MemoryType.FEEDBACK, "content", "", Map.of()));
        assertTrue(store.delete("del01"));
        assertTrue(store.get("del01").isEmpty());
    }

    @Test
    void deleteMissingReturnsFalse() {
        assertFalse(store.delete("ghost"));
    }

    @Test
    void listAllReturnsAllSavedEntries() {
        store.save(entry("id1", MemoryType.USER,     "U", "", Map.of()));
        store.save(entry("id2", MemoryType.FEEDBACK, "F", "", Map.of()));
        store.save(entry("id3", MemoryType.PROJECT,  "P", "", Map.of()));
        assertEquals(3, store.listAll().size());
    }

    @Test
    void clearDeletesAllFiles() {
        store.save(entry("c1", MemoryType.REFERENCE, "R", "", Map.of()));
        store.save(entry("c2", MemoryType.USER,      "U", "", Map.of()));
        store.clear();
        assertEquals(0, store.size());
    }

    @Test
    void saveOverwritesExistingEntry() {
        MemoryEntry original = entry("upd1", MemoryType.USER, "old content", "", Map.of());
        store.save(original);
        MemoryEntry updated = entry("upd1", MemoryType.USER, "new content", "", Map.of());
        store.save(updated);
        assertEquals("new content", store.get("upd1").orElseThrow().content());
    }

    // ── parse / serialize ─────────────────────────────────────────────────────

    @Test
    void serializeProducesExpectedFormat() {
        MemoryEntry e = entry("abc12345", MemoryType.USER,
                "The user explicitly prefers tabs over spaces when editing source files.",
                "User prefers tabs for indentation",
                Map.of("name", "prefer_tabs"));
        String text = MarkdownMemoryStore.serialize(e);

        assertTrue(text.startsWith("---\n"));
        assertTrue(text.contains("id: abc12345\n"));
        assertTrue(text.contains("name: prefer_tabs\n"));
        assertTrue(text.contains("description: User prefers tabs for indentation\n"));
        assertTrue(text.contains("type: user\n"));
        assertTrue(text.contains("The user explicitly prefers tabs over spaces"));
    }

    @Test
    void parseThenSerializeIsIdempotent() {
        MemoryEntry original = entry("idem1", MemoryType.FEEDBACK, "No mock DB.",
                "Avoid mocking the database", Map.of("name", "no_mock_db", "session_id", "sess"));
        String serialized = MarkdownMemoryStore.serialize(original);
        MemoryEntry parsed = MarkdownMemoryStore.parse(serialized);
        assertEquals(original.id(),          parsed.id());
        assertEquals(original.type(),        parsed.type());
        assertEquals(original.content(),     parsed.content());
        assertEquals(original.description(), parsed.description());
        assertEquals(original.metadata().get("name"),       parsed.metadata().get("name"));
        assertEquals(original.metadata().get("session_id"), parsed.metadata().get("session_id"));
    }

    @Test
    void parseHandlesMissingOptionalFields() {
        String minimal = """
                ---
                id: min01
                type: project
                ---
                Minimal content.
                """;
        MemoryEntry e = MarkdownMemoryStore.parse(minimal);
        assertEquals("min01",             e.id());
        assertEquals(MemoryType.PROJECT,  e.type());
        assertEquals("Minimal content.",  e.content());
        assertEquals("",                  e.description());
        assertEquals(0L,                  e.createdAt());
    }

    @Test
    void parseThrowsOnMissingFrontmatter() {
        assertThrows(IllegalArgumentException.class,
                () -> MarkdownMemoryStore.parse("no frontmatter here"));
    }

    @Test
    void parseThrowsOnUnclosedFrontmatter() {
        String unclosed = "---\nid: x\ntype: user\n(no closing ---)";
        assertThrows(IllegalArgumentException.class, () -> MarkdownMemoryStore.parse(unclosed));
    }

    @Test
    void parseThrowsOnMissingId() {
        String noId = "---\ntype: user\n---\ncontent";
        assertThrows(IllegalArgumentException.class, () -> MarkdownMemoryStore.parse(noId));
    }

    @Test
    void parseHandlesWindowsLineEndings() {
        String crlf = "---\r\nid: win01\r\ntype: feedback\r\n---\r\nWindows content.\r\n";
        MemoryEntry e = MarkdownMemoryStore.parse(crlf);
        assertEquals("win01",            e.id());
        assertEquals(MemoryType.FEEDBACK, e.type());
        assertEquals("Windows content.", e.content());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static MemoryEntry entry(String id, MemoryType type, String content,
                                     String description, Map<String, String> metadata) {
        long now = System.currentTimeMillis();
        return new MemoryEntry(id, type, content, description, now, now, 0, metadata);
    }
}