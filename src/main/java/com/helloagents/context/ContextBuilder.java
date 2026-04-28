package com.helloagents.context;

import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.MemoryType;
import com.helloagents.rag.app.RagSystem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds an assembled context string via the four-stage GSSC pipeline.
 *
 * <p>Construction wires up the stable data sources (memory, RAG).
 * Each {@code build()} call supplies the per-request context.
 *
 * <pre>
 *   ContextBuilder builder = new ContextBuilder(config)
 *       .withMemory(memory)
 *       .withRag(rag, 5);
 *
 *   String ctx = builder.build(
 *       "用户的问题",
 *       "You are a helpful assistant.",
 *       history,
 *       List.of());
 * </pre>
 */
public final class ContextBuilder {

    private final ContextConfig config;

    // ── Data sources (wired at construction, reused across builds) ────────────
    private MemoryService memory;
    private MemoryType[]  memoryTypes      = new MemoryType[0];
    private int           memoryLimit      = Integer.MAX_VALUE;
    private double        memoryMinImportance = 0.0;
    private RagSystem     rag;
    private int           ragTopK          = 5;
    private double        ragMinScore      = 0.0;

    public ContextBuilder(ContextConfig config) {
        this.config = config;
    }

    public ContextBuilder withMemory(MemoryService memory, MemoryType... types) {
        this.memory      = memory;
        this.memoryTypes = types;
        return this;
    }

    public ContextBuilder withMemoryFilter(int limit, double minImportance) {
        this.memoryLimit         = limit;
        this.memoryMinImportance = minImportance;
        return this;
    }

    public ContextBuilder withRag(RagSystem rag) {
        this.rag     = rag;
        return this;
    }

    public ContextBuilder withRagFilter(int topK, double minScore) {
        this.ragTopK = topK;
        this.ragMinScore = minScore;
        return this;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    public String build(
            String userQuery,
            String systemInstructions,
            List<String> conversationHistory,
            List<ContextPacket> customPackets) {
        List<ContextPacket> gathered   = gather(userQuery, systemInstructions, conversationHistory, customPackets);
        List<ContextPacket> selected   = select(gathered);
        List<ContextPacket> structured = structure(selected);
        List<ContextPacket> compressed = compress(structured);
        return assemble(compressed);
    }

    /** Convenience overload — only user query, no extra context. */
    public String build(String userQuery) {
        return build(userQuery, null, List.of(), List.of());
    }

    // ── Stage 1: Gather ───────────────────────────────────────────────────────

    private List<ContextPacket> gather(
            String userQuery,
            String systemInstructions,
            List<String> conversationHistory,
            List<ContextPacket> customPackets) {
        List<ContextPacket> all = new ArrayList<>();

        // systemInstructions: EPOCH timestamp → always sorted to front
        if (systemInstructions != null && !systemInstructions.isBlank()) {
            all.add(ContextPacket.of(systemInstructions)
                    .withRelevance(1.0)
                    .withCreatedAt(Instant.EPOCH)
                    .withTokenEstimate(estimateTokens(systemInstructions))
                    .build());
        }

        // conversationHistory: ascending timestamps, relevance grows with recency (0.5 → 0.8)
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            int n = conversationHistory.size();
            long base = Instant.now().toEpochMilli() - n * 1_000L;
            for (int i = 0; i < n; i++) {
                String turn = conversationHistory.get(i);
                double rel = 0.5 + 0.3 * ((double) i / Math.max(1, n - 1));
                all.add(ContextPacket.of(turn)
                        .withRelevance(rel)
                        .withCreatedAt(Instant.ofEpochMilli(base + (long) i * 1_000))
                        .withTokenEstimate(estimateTokens(turn))
                        .build());
            }
        }

        // memory: search with userQuery, apply limit and minImportance
        if (memory != null && userQuery != null && !userQuery.isBlank()) {
            memory.search(userQuery, memoryLimit, memoryMinImportance, memoryTypes).forEach(entry ->
                    all.add(ContextPacket.of(entry.content())
                            .withRelevance(entry.importance())
                            .withCreatedAt(Instant.ofEpochMilli(entry.createdAt()))
                            .withTokenEstimate(estimateTokens(entry.content()))
                            .build()));
        }

        // RAG: retrieve top-k chunks with userQuery, filter by minScore
        if (rag != null && userQuery != null && !userQuery.isBlank()) {
            rag.search(userQuery, ragTopK, ragMinScore).forEach(result ->
                    all.add(ContextPacket.of(result.content())
                            .withRelevance(result.score())
                            .withTokenEstimate(estimateTokens(result.content()))
                            .build()));
        }

        // customPackets: fill missing token estimates
        if (customPackets != null) {
            customPackets.stream().map(this::withEstimateIfAbsent).forEach(all::add);
        }

        return all.stream()
                .sorted(Comparator.comparing(ContextPacket::createdAt))
                .collect(Collectors.toList());
    }

    // ── Stage 2: Select ───────────────────────────────────────────────────────

    private List<ContextPacket> select(List<ContextPacket> packets) {
        if (packets.isEmpty()) return packets;

        long minEpoch = packets.stream()
                .mapToLong(p -> p.createdAt().toEpochMilli()).min().orElse(0L);
        long maxEpoch = packets.stream()
                .mapToLong(p -> p.createdAt().toEpochMilli()).max().orElse(minEpoch);
        long range = maxEpoch - minEpoch;

        return packets.stream()
                .filter(p -> p.relevanceScore() >= config.minRelevance())
                .sorted(Comparator.comparingDouble(
                        (ContextPacket p) -> compositeScore(p, minEpoch, range)).reversed())
                .collect(Collectors.toList());
    }

    private double compositeScore(ContextPacket p, long minEpoch, long range) {
        double recency = range == 0 ? 1.0
                : (double) (p.createdAt().toEpochMilli() - minEpoch) / range;
        return config.recencyWeight()   * recency
             + config.relevanceWeight() * p.relevanceScore();
    }

    // ── Stage 3: Structure ────────────────────────────────────────────────────

    private List<ContextPacket> structure(List<ContextPacket> packets) {
        int budget = config.availableTokens();
        List<ContextPacket> result = new ArrayList<>();
        int used = 0;
        for (ContextPacket p : packets) {
            int tokens = tokenEstimate(p);
            if (used + tokens > budget) break;
            result.add(p);
            used += tokens;
        }
        return result;
    }

    // ── Stage 4: Compress ─────────────────────────────────────────────────────

    private List<ContextPacket> compress(List<ContextPacket> packets) {
        if (!config.enableCompression()) return packets;

        int perPacketLimit = Math.max(64, (int) (config.availableTokens() * 0.2));
        return packets.stream()
                .map(p -> truncate(p, perPacketLimit))
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int tokenEstimate(ContextPacket p) {
        return Math.max(1, p.tokenEstimate());
    }

    /**
     * CJK characters count as 1 token each; other characters use a 4-chars-per-token ratio.
     */
    private static int estimateTokens(String content) {
        if (content == null || content.isEmpty()) return 0;
        int cjk = 0;
        for (int i = 0; i < content.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(content.charAt(i));
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA) {
                cjk++;
            }
        }
        return Math.max(1, cjk + (content.length() - cjk) / 4);
    }

    private ContextPacket withEstimateIfAbsent(ContextPacket p) {
        if (p.tokenEstimate() > 0) return p;
        return ContextPacket.of(p.content())
                .withRelevance(p.relevanceScore())
                .withCreatedAt(p.createdAt())
                .withTokenEstimate(estimateTokens(p.content()))
                .withMetadata(p.metadata())
                .build();
    }

    private static ContextPacket truncate(ContextPacket p, int maxTokens) {
        int maxChars = maxTokens * 4;
        if (p.content().length() <= maxChars) return p;
        String truncated = p.content().substring(0, maxChars) + "...";
        return ContextPacket.of(truncated)
                .withRelevance(p.relevanceScore())
                .withTokenEstimate(maxTokens)
                .withCreatedAt(p.createdAt())
                .withMetadata(p.metadata())
                .build();
    }

    private static String assemble(List<ContextPacket> packets) {
        return packets.stream()
                .map(ContextPacket::format)
                .collect(Collectors.joining("\n"));
    }
}