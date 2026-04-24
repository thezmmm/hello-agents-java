package com.helloagents.memory;

import com.helloagents.llm.Message;
import com.helloagents.memory.core.MemoryEntry;
import com.helloagents.memory.core.MemoryType;
import com.helloagents.memory.store.WorkingMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkingMemoryTest {

    // ── basic add / get ───────────────────────────────────────────────────────

    @Test
    void addReturnsId() {
        WorkingMemory mem = new WorkingMemory();
        String id = mem.add("task A", 0.8);
        assertNotNull(id);
        assertEquals(8, id.length());
    }

    @Test
    void getReturnsEntry() {
        WorkingMemory mem = new WorkingMemory();
        String id = mem.add("task A", 0.8);
        assertTrue(mem.get(id).isPresent());
        assertEquals("task A", mem.get(id).get().content());
        assertEquals(MemoryType.WORKING, mem.get(id).get().type());
    }

    @Test
    void importanceIsClamped() {
        WorkingMemory mem = new WorkingMemory();
        String id1 = mem.add("over", 2.5);
        String id2 = mem.add("under", -1.0);
        assertEquals(1.0, mem.get(id1).get().importance());
        assertEquals(0.0, mem.get(id2).get().importance());
    }

    @Test
    void listAllOrderedByInsertion() {
        WorkingMemory mem = new WorkingMemory();
        mem.add("A", 0.5);
        mem.add("B", 0.5);
        List<MemoryEntry> all = mem.listAll();
        assertEquals("A", all.get(0).content());
        assertEquals("B", all.get(1).content());
    }

    // ── capacity eviction ─────────────────────────────────────────────────────

    @Test
    void evictsOldestWhenFull() {
        WorkingMemory mem = new WorkingMemory(3, WorkingMemory.NO_TTL);
        String first = mem.add("A", 0.5);
        mem.add("B", 0.5);
        mem.add("C", 0.5);
        mem.add("D", 0.5);
        assertEquals(3, mem.size());
        assertTrue(mem.get(first).isEmpty());
    }

    @Test
    void deleteReducesSize() {
        WorkingMemory mem = new WorkingMemory();
        String id = mem.add("task", 0.5);
        assertTrue(mem.delete(id));
        assertEquals(0, mem.size());
    }

    @Test
    void clearEmptiesStore() {
        WorkingMemory mem = new WorkingMemory();
        mem.add("A", 0.5);
        mem.add("B", 0.5);
        mem.clear();
        assertEquals(0, mem.size());
    }

    // ── TTL expiry ────────────────────────────────────────────────────────────

    @Test
    void expiredEntryRemovedOnListAll() throws InterruptedException {
        WorkingMemory mem = new WorkingMemory(10, 50); // 50 ms TTL
        mem.add("transient", 0.5);
        assertEquals(1, mem.listAll().size());
        Thread.sleep(80);
        assertEquals(0, mem.listAll().size());
    }

    @Test
    void expiredEntryRemovedOnSize() throws InterruptedException {
        WorkingMemory mem = new WorkingMemory(10, 50);
        mem.add("transient", 0.5);
        Thread.sleep(80);
        assertEquals(0, mem.size());
    }

    @Test
    void nonExpiredEntryStillPresent() throws InterruptedException {
        WorkingMemory mem = new WorkingMemory(10, 500); // 500 ms TTL
        mem.add("durable", 0.5);
        Thread.sleep(50);
        assertEquals(1, mem.listAll().size());
    }

    @Test
    void noTtlNeverExpires() throws InterruptedException {
        WorkingMemory mem = new WorkingMemory(10, WorkingMemory.NO_TTL);
        mem.add("forever", 0.5);
        Thread.sleep(50);
        assertEquals(1, mem.listAll().size());
    }

    @Test
    void ttlSweepBeforeCapacityCheck() throws InterruptedException {
        WorkingMemory mem = new WorkingMemory(2, 50); // capacity 2, 50 ms TTL
        mem.add("old-1", 0.5);
        mem.add("old-2", 0.5);
        Thread.sleep(80); // both expire
        // adding two more should not trigger eviction (expired ones are swept first)
        String id1 = mem.add("new-1", 0.5);
        String id2 = mem.add("new-2", 0.5);
        assertEquals(2, mem.size());
        assertTrue(mem.get(id1).isPresent());
        assertTrue(mem.get(id2).isPresent());
    }

    // ── conversation support ──────────────────────────────────────────────────

    @Test
    void addMessageStoresRoleInMetadata() {
        WorkingMemory mem = new WorkingMemory();
        mem.addMessage(Message.user("hello"));
        MemoryEntry entry = mem.listAll().get(0);
        assertEquals("hello", entry.content());
        assertEquals("user", entry.metadata().get("role"));
    }

    @Test
    void getMessagesReconstructsHistory() {
        WorkingMemory mem = new WorkingMemory();
        mem.addMessage(Message.system("You are helpful."));
        mem.addMessage(Message.user("hi"));
        mem.addMessage(Message.assistant("hello!"));
        List<Message> msgs = mem.getMessages();
        assertEquals(3, msgs.size());
        assertEquals("system",    msgs.get(0).role());
        assertEquals("user",      msgs.get(1).role());
        assertEquals("assistant", msgs.get(2).role());
        assertEquals("hi",        msgs.get(1).content());
    }

    @Test
    void toolMessagePreservesToolCallId() {
        WorkingMemory mem = new WorkingMemory();
        mem.addMessage(Message.tool("call-42", "result"));
        Message restored = mem.getMessages().get(0);
        assertEquals("tool",    restored.role());
        assertEquals("result",  restored.content());
        assertEquals("call-42", restored.toolCallId());
    }

    @Test
    void clearMessagesRemovesOnlyConversationEntries() {
        WorkingMemory mem = new WorkingMemory();
        mem.add("context note", 0.8);        // not a message
        mem.addMessage(Message.user("hi"));
        mem.addMessage(Message.assistant("hey"));
        mem.clearMessages();
        assertEquals(1, mem.size());
        assertEquals("context note", mem.listAll().get(0).content());
        assertTrue(mem.getMessages().isEmpty());
    }

    @Test
    void getMessagesSkipsNonMessageEntries() {
        WorkingMemory mem = new WorkingMemory();
        mem.add("internal note", 0.5);
        mem.addMessage(Message.user("question"));
        List<Message> msgs = mem.getMessages();
        assertEquals(1, msgs.size());
        assertEquals("question", msgs.get(0).content());
    }
}