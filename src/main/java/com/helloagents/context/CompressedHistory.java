package com.helloagents.context;

import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Conversation history with rolling LLM summarization.
 *
 * <p>Maintains a [summary] + [recent turns] structure. When {@code recent} exceeds
 * {@code maxRecentTokens}, the oldest turns are evicted and folded into the summary
 * via an LLM call. Subsequent compressions update the existing summary rather than
 * starting from scratch, so the summary accumulates incrementally.
 *
 * <pre>
 *   CompressedHistory h = new CompressedHistory(llm);
 *   h.add(Message.user("hi"));
 *   h.add(Message.assistant("hello"));
 *   // ... more turns ...
 *   List&lt;Message&gt; forLlm = h.toMessages();  // [summary_system_msg?] + [recent]
 * </pre>
 */
public final class CompressedHistory {

    private static final int DEFAULT_MAX_RECENT_TOKENS  = 2000;
    private static final int DEFAULT_KEEP_RECENT_TOKENS = 800;

    private final LlmClient llm;
    private final int maxRecentTokens;
    private final int keepRecentTokens;

    private String summary;
    private final List<Message> recent = new ArrayList<>();
    private int consumed = 0;

    public CompressedHistory(LlmClient llm) {
        this(llm, DEFAULT_MAX_RECENT_TOKENS, DEFAULT_KEEP_RECENT_TOKENS);
    }

    public CompressedHistory(LlmClient llm, int maxRecentTokens, int keepRecentTokens) {
        this.llm = llm;
        this.maxRecentTokens  = maxRecentTokens;
        this.keepRecentTokens = keepRecentTokens;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void add(Message message) {
        recent.add(message);
        maybeCompress();
    }

    /**
     * Syncs from the full conversation history. Only messages beyond the last
     * sync point are processed, so callers can safely pass the entire history
     * on every invocation without causing duplicates.
     */
    public void sync(List<Message> allMessages) {
        if (allMessages.size() <= consumed) return;
        recent.addAll(allMessages.subList(consumed, allMessages.size()));
        consumed = allMessages.size();
        maybeCompress();
    }

    /** Returns the current rolling summary text, or {@code null} if no compression has occurred. */
    public String getSummary() {
        return summary;
    }

    /** Returns the recent messages kept verbatim (no summary message included). */
    public List<Message> getRecentMessages() {
        return Collections.unmodifiableList(recent);
    }

    public void clear() {
        recent.clear();
        summary = null;
        consumed = 0;
    }

    public int recentSize() {
        return recent.size();
    }

    public boolean hasSummary() {
        return summary != null;
    }

    // ── Compression ───────────────────────────────────────────────────────────

    private void maybeCompress() {
        if (estimateTokens(recent) <= maxRecentTokens) return;

        List<List<Message>> turns = groupIntoTurns(recent);
        if (turns.size() <= 1) return;

        // Walk newest-to-oldest accumulating tokens; stop when budget exhausted.
        // Everything before keepFrom gets evicted.
        int used = 0;
        int keepFrom = turns.size();
        for (int i = turns.size() - 1; i >= 0; i--) {
            int t = estimateTokens(turns.get(i));
            if (used + t <= keepRecentTokens) {
                used += t;
                keepFrom = i;
            } else {
                break;
            }
        }
        // keepFrom == turns.size(): no turn fit in budget — keep at least the newest turn
        // keepFrom == 0: all turns fit — must evict at least 1 (the oldest)
        if (keepFrom >= turns.size()) keepFrom = turns.size() - 1;
        if (keepFrom == 0)            keepFrom = 1;

        List<Message> toEvict = flatten(turns.subList(0, keepFrom));
        summary = doCompress(toEvict);
        recent.subList(0, toEvict.size()).clear();
    }

    private String doCompress(List<Message> toEvict) {
        String dialogue = formatForSummary(toEvict);
        String prompt = summary == null
                ? "Summarize the following conversation concisely, preserving key information and conclusions:\n\n" + dialogue
                : "Existing summary:\n" + summary
                        + "\n\nThe conversation continued with:\n" + dialogue
                        + "\n\nUpdate the summary to incorporate the new exchanges. Keep it concise.";
        return llm.chat(prompt);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Groups a flat message list into [user, assistant] turn pairs.
     * An incomplete trailing user message forms a single-element turn.
     */
    private static List<List<Message>> groupIntoTurns(List<Message> messages) {
        List<List<Message>> turns = new ArrayList<>();
        int i = 0;
        while (i < messages.size()) {
            List<Message> turn = new ArrayList<>();
            if ("user".equals(messages.get(i).role())) {
                turn.add(messages.get(i++));
            }
            if (i < messages.size() && "assistant".equals(messages.get(i).role())) {
                turn.add(messages.get(i++));
            }
            if (!turn.isEmpty()) {
                turns.add(turn);
            } else {
                i++;  // skip unexpected role
            }
        }
        return turns;
    }

    private static List<Message> flatten(List<List<Message>> turns) {
        List<Message> result = new ArrayList<>();
        for (List<Message> turn : turns) result.addAll(turn);
        return result;
    }

    private static String formatForSummary(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            if (msg.content() != null) {
                sb.append(msg.role()).append(": ").append(msg.content()).append('\n');
            }
        }
        return sb.toString();
    }

    private static int estimateTokens(List<Message> messages) {
        return messages.stream().mapToInt(m -> estimateTokens(m.content())).sum();
    }

    /** CJK characters count as 1 token each; non-CJK words count as 1.3 tokens each. */
    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjk = 0;
        StringBuilder nonCjk = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA) {
                cjk++;
            } else {
                nonCjk.append(c);
            }
        }
        long words = Arrays.stream(nonCjk.toString().split("\\s+"))
                .filter(w -> !w.isEmpty()).count();
        return Math.max(1, (int) (cjk + words * 1.3));
    }
}