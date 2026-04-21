package com.helloagents.memory;

import com.helloagents.llm.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Working memory — active task context with bounded capacity and automatic TTL expiry.
 *
 * <p>Pure in-memory; contents are lost on restart — by design, matching the
 * temporary nature of working memory. Two complementary cleanup mechanisms apply:
 * <ul>
 *   <li><b>Capacity eviction</b>: when the store is full, the oldest entry is dropped.</li>
 *   <li><b>TTL sweep</b>: entries older than {@code ttlMillis} are lazily removed before
 *       every read or write.</li>
 * </ul>
 *
 * <p>Also serves as a drop-in replacement for {@code ConversationMemory}: use
 * {@link #addMessage(Message)} / {@link #getMessages()} to manage chat history.
 * Messages are stored as ordinary entries with {@code role} (and optionally
 * {@code tool_call_id}) in metadata, so they participate in the same TTL and
 * capacity limits as other working-memory entries.
 */
public class WorkingMemory extends InMemoryStore {

    public static final int  DEFAULT_CAPACITY   = 10;
    public static final long DEFAULT_TTL_MILLIS = 30 * 60 * 1000L; // 30 minutes
    public static final long NO_TTL             = 0L;

    private final int  capacity;
    private final long ttlMillis;

    public WorkingMemory() {
        this(DEFAULT_CAPACITY, DEFAULT_TTL_MILLIS);
    }

    public WorkingMemory(int capacity) {
        this(capacity, DEFAULT_TTL_MILLIS);
    }

    public WorkingMemory(int capacity, long ttlMillis) {
        this.capacity  = capacity;
        this.ttlMillis = ttlMillis;
    }

    // ── MemoryStore overrides (TTL-aware) ────────────────────────────────────

    @Override
    public List<MemoryEntry> listAll() {
        sweepExpired();
        return super.listAll();
    }

    @Override
    public int size() {
        sweepExpired();
        return super.size();
    }

    // ── add ───────────────────────────────────────────────────────────────────

    public String add(String content, double importance) {
        return add(content, importance, Map.of());
    }

    public String add(String content, double importance, Map<String, String> metadata) {
        sweepExpired();
        if (super.size() >= capacity) evictOldest();
        String id  = newId();
        long   now = System.currentTimeMillis();
        save(new MemoryEntry(id, MemoryType.WORKING, content, clamp(importance), now, now, 0, metadata));
        return id;
    }

    // ── conversation support ──────────────────────────────────────────────────

    /**
     * Store a chat message as a working-memory entry.
     * The message role (and tool_call_id if present) are persisted in metadata.
     */
    public void addMessage(Message message) {
        Map<String, String> meta = new HashMap<>();
        meta.put("role", message.role());
        if (message.toolCallId() != null) {
            meta.put("tool_call_id", message.toolCallId());
        }
        add(message.content() != null ? message.content() : "", 0.5, meta);
    }

    /**
     * Reconstruct the conversation history from working-memory entries that carry a
     * {@code role} metadata key, in insertion order.
     */
    public List<Message> getMessages() {
        return listAll().stream()
                .filter(e -> e.metadata().containsKey("role"))
                .map(e -> new Message(
                        e.metadata().get("role"),
                        e.content(),
                        e.metadata().get("tool_call_id")))
                .toList();
    }

    /** Remove all conversation messages (entries that carry a {@code role} key). */
    public void clearMessages() {
        listAll().stream()
                .filter(e -> e.metadata().containsKey("role"))
                .map(MemoryEntry::id)
                .forEach(this::delete);
    }

    // ── TTL sweep ─────────────────────────────────────────────────────────────

    private void sweepExpired() {
        if (ttlMillis <= 0) return;
        long cutoff = System.currentTimeMillis() - ttlMillis;
        List<String> expired = super.listAll().stream()
                .filter(e -> e.createdAt() < cutoff)
                .map(MemoryEntry::id)
                .toList();
        expired.forEach(this::delete);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void evictOldest() {
        super.listAll().stream().findFirst().ifPresent(e -> delete(e.id()));
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}