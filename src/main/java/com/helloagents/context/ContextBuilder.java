package com.helloagents.context;

import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.MemoryType;
import com.helloagents.rag.app.RagSystem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        List<ContextPacket> selected   = select(gathered, userQuery);
        List<ContextPacket> compressed = compress(selected);
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
                    .withMetadata(Map.of("type", "system_instruction"))
                    .build());
        }

        // conversationHistory: ascending timestamps, fixed relevance 0.6
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            int n = conversationHistory.size();
            long base = Instant.now().toEpochMilli() - n * 1_000L;
            for (int i = 0; i < n; i++) {
                String turn = conversationHistory.get(i);
                all.add(ContextPacket.of(turn)
                        .withRelevance(0.6)
                        .withCreatedAt(Instant.ofEpochMilli(base + (long) i * 1_000))
                        .withTokenEstimate(estimateTokens(turn))
                        .build());
            }
        }

        // memory: relevance defaults to 0.5 so select recalculates via user query
        if (memory != null && userQuery != null && !userQuery.isBlank()) {
            memory.search(userQuery, memoryLimit, memoryMinImportance, memoryTypes).forEach(entry ->
                    all.add(ContextPacket.of(entry.content())
                            .withRelevance(0.5)
                            .withCreatedAt(Instant.ofEpochMilli(entry.createdAt()))
                            .withTokenEstimate(estimateTokens(entry.content()))
                            .build()));
        }

        // RAG: relevance defaults to 0.5 so select recalculates via user query
        if (rag != null && userQuery != null && !userQuery.isBlank()) {
            rag.search(userQuery, ragTopK, ragMinScore).forEach(result ->
                    all.add(ContextPacket.of(result.content())
                            .withRelevance(0.5)
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
    // Separates system packets (always included), scores and greedy-fills the rest.

    private List<ContextPacket> select(List<ContextPacket> packets, String userQuery) {
        List<ContextPacket> systemPackets = packets.stream()
                .filter(p -> "system_instruction".equals(p.metadata().get("type")))
                .collect(Collectors.toList());
        List<ContextPacket> otherPackets = packets.stream()
                .filter(p -> !"system_instruction".equals(p.metadata().get("type")))
                .collect(Collectors.toList());

        int systemTokens    = systemPackets.stream().mapToInt(ContextBuilder::tokenEstimate).sum();
        int remainingTokens = config.availableTokens() - systemTokens;

        if (remainingTokens <= 0) return systemPackets;

        String q = userQuery != null ? userQuery : "";
        List<Map.Entry<Double, ContextPacket>> scored = new ArrayList<>();
        for (ContextPacket p : otherPackets) {
            double relevance = p.relevanceScore() == 0.5
                    ? calculateRelevance(p.content(), q)
                    : p.relevanceScore();
            double recency = calculateRecency(p.createdAt());
            double score   = config.relevanceWeight() * relevance
                           + config.recencyWeight()   * recency;
            if (relevance >= config.minRelevance()) {
                scored.add(Map.entry(score, p));
            }
        }
        scored.sort(Map.Entry.<Double, ContextPacket>comparingByKey().reversed());

        List<ContextPacket> selected = new ArrayList<>(systemPackets);
        int used = systemTokens;
        for (Map.Entry<Double, ContextPacket> e : scored) {
            int tokens = ContextBuilder.tokenEstimate(e.getValue());
            if (used + tokens <= config.availableTokens()) {
                selected.add(e.getValue());
                used += tokens;
            }
        }
        return selected;
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

    /** Jaccard similarity between content words and query words. */
    private static double calculateRelevance(String content, String query) {
        if (query.isBlank()) return 0.0;
        Set<String> contentWords = new HashSet<>(Arrays.asList(content.toLowerCase().split("\\s+")));
        Set<String> queryWords   = new HashSet<>(Arrays.asList(query.toLowerCase().split("\\s+")));
        Set<String> intersection = new HashSet<>(contentWords);
        intersection.retainAll(queryWords);
        Set<String> union = new HashSet<>(contentWords);
        union.addAll(queryWords);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /** Exponential decay recency: stays high within 24 h, decays gradually after. */
    private static double calculateRecency(Instant timestamp) {
        double ageHours = (Instant.now().toEpochMilli() - timestamp.toEpochMilli()) / 3_600_000.0;
        double score = Math.exp(-0.1 * ageHours / 24.0);
        return Math.max(0.1, Math.min(1.0, score));
    }

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