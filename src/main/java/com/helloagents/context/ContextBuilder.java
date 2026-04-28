package com.helloagents.context;

import com.helloagents.llm.Message;
import com.helloagents.memory.MemoryService;
import com.helloagents.rag.app.RagSystem;
import com.helloagents.rag.core.Embedding;
import com.helloagents.rag.core.EmbeddingModel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
    private MemoryService  memory;
    private RagSystem      rag;
    private int            ragTopK    = 5;
    private double         ragMinScore = 0.0;

    public ContextBuilder(ContextConfig config) {
        this.config = config;
    }

    public ContextBuilder withMemory(MemoryService memory) {
        this.memory = memory;
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
            List<Message> conversationHistory,
            List<ContextPacket> customPackets) {
        List<ContextPacket> gathered  = gather(userQuery, systemInstructions, conversationHistory, customPackets);
        List<ContextPacket> selected  = select(gathered, userQuery);
        String              structured = structure(selected, userQuery);
        return compress(structured);
    }

    /** Convenience overload — only user query, no extra context. */
    public String build(String userQuery) {
        return build(userQuery, null, List.of(), List.of());
    }

    // ── Stage 1: Gather ───────────────────────────────────────────────────────

    private List<ContextPacket> gather(
            String userQuery,
            String systemInstructions,
            List<Message> conversationHistory,
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

        // conversationHistory: ascending timestamps preserve order; role prefix retained in content
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            int n = conversationHistory.size();
            long base = Instant.now().toEpochMilli() - n * 1_000L;
            for (int i = 0; i < n; i++) {
                Message msg = conversationHistory.get(i);
                String content = msg.role() + ": " + msg.content();
                all.add(ContextPacket.of(content)
                        .withRelevance(0.6)
                        .withCreatedAt(Instant.ofEpochMilli(base + (long) i * 1_000))
                        .withTokenEstimate(estimateTokens(content))
                        .withMetadata(Map.of("type", "conversation"))
                        .build());
            }
        }

        // RAG: no explicit relevance → defaults to 0.5, select recalculates via user query
        if (rag != null && userQuery != null && !userQuery.isBlank()) {
            rag.search(userQuery, ragTopK, ragMinScore).forEach(result ->
                    all.add(ContextPacket.of(result.content())
                            .withTokenEstimate(estimateTokens(result.content()))
                            .withMetadata(Map.of("type", "rag_result"))
                            .build()));
        }

        // customPackets: preserve existing metadata; tag untyped packets as "custom"
        if (customPackets != null) {
            customPackets.stream()
                    .map(this::withEstimateIfAbsent)
                    .map(p -> p.metadata().containsKey("type") ? p : withType(p, "custom"))
                    .forEach(p -> all.add(p));
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

        int systemTokens    = systemPackets.stream().mapToInt(ContextBuilder::estimateTokens).sum();
        int remainingTokens = config.availableTokens() - systemTokens;

        if (remainingTokens <= 0) return systemPackets;

        String q = userQuery != null ? userQuery : "";
        Map<ContextPacket, Double> relevanceMap = computeRelevance(otherPackets, q);

        List<Map.Entry<Double, ContextPacket>> scored = new ArrayList<>();
        for (ContextPacket p : otherPackets) {
            double relevance = relevanceMap.get(p);
            double recency   = recencyScore(p.createdAt());
            double score     = config.relevanceWeight() * relevance
                             + config.recencyWeight()   * recency;
            if (relevance >= config.minRelevance()) {
                scored.add(Map.entry(score, p));
            }
        }
        scored.sort(Map.Entry.<Double, ContextPacket>comparingByKey().reversed());

        List<ContextPacket> selected = new ArrayList<>(systemPackets);
        int used = systemTokens;
        for (Map.Entry<Double, ContextPacket> e : scored) {
            int tokens = estimateTokens(e.getValue());
            if (used + tokens <= config.availableTokens()) {
                selected.add(e.getValue());
                used += tokens;
            }
        }
        return selected;
    }

    /**
     * Computes relevance scores for packets whose stored relevance == 0.5 (sentinel = needs calculation).
     * Uses vector cosine similarity when an EmbeddingModel is configured; falls back to Jaccard otherwise.
     * Packets with explicit relevance (≠ 0.5) are returned unchanged.
     */
    private Map<ContextPacket, Double> computeRelevance(List<ContextPacket> packets, String query) {
        Map<ContextPacket, Double> result = new IdentityHashMap<>();

        List<ContextPacket> needsCalc = packets.stream()
                .filter(p -> p.relevanceScore() == 0.5)
                .collect(Collectors.toList());

        if (!needsCalc.isEmpty() && !query.isBlank() && config.embeddingModel() != null) {
            // Batch-embed query + all candidates in one API call
            List<String> texts = new ArrayList<>();
            texts.add(query);
            needsCalc.forEach(p -> texts.add(p.content()));

            List<Embedding> embeddings = config.embeddingModel().embedBatch(texts);
            float[] queryVec = embeddings.get(0).vector();

            Map<ContextPacket, Double> vecScores = new IdentityHashMap<>();
            for (int i = 0; i < needsCalc.size(); i++) {
                vecScores.put(needsCalc.get(i), (double) cosineSimilarity(queryVec, embeddings.get(i + 1).vector()));
            }
            for (ContextPacket p : packets) {
                result.put(p, p.relevanceScore() == 0.5 ? vecScores.get(p) : p.relevanceScore());
            }
        } else {
            // Fallback: Jaccard keyword similarity
            for (ContextPacket p : packets) {
                result.put(p, p.relevanceScore() == 0.5
                        ? jaccardSimilarity(p.content(), query)
                        : p.relevanceScore());
            }
        }
        return result;
    }

    // ── Stage 4: Compress ─────────────────────────────────────────────────────
    // Operates on the assembled string; compresses section-by-section to stay within budget.

    private String compress(String context) {
        if (!config.enableCompression()) return context;

        int maxTokens = config.availableTokens();
        if (estimateTokens(context) <= maxTokens) return context;

        String[] sections = context.split("\n\n");
        List<String> result = new ArrayList<>();
        int total = 0;

        for (String section : sections) {
            int sectionTokens = estimateTokens(section);
            if (total + sectionTokens <= maxTokens) {
                result.add(section);
                total += sectionTokens;
            } else {
                int remaining = maxTokens - total;
                if (remaining > 50) {
                    result.add(truncateText(section, remaining) + "\n[... 内容已压缩 ...]");
                }
                break;
            }
        }

        return String.join("\n\n", result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0f;
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    /** Jaccard similarity between content words and query words (fallback when no EmbeddingModel). */
    private static double jaccardSimilarity(String content, String query) {
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
    private static double recencyScore(Instant timestamp) {
        double ageHours = (Instant.now().toEpochMilli() - timestamp.toEpochMilli()) / 3_600_000.0;
        double score = Math.exp(-0.1 * ageHours / 24.0);
        return Math.max(0.1, Math.min(1.0, score));
    }

    private static int estimateTokens(ContextPacket p) {
        return Math.max(1, p.tokenEstimate());
    }

    /**
     * CJK/kana characters count as 1 token each; non-CJK words count as 1.3 tokens each.
     */
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

    private static ContextPacket withType(ContextPacket p, String type) {
        Map<String, String> merged = new java.util.HashMap<>(p.metadata());
        merged.put("type", type);
        return ContextPacket.of(p.content())
                .withRelevance(p.relevanceScore())
                .withCreatedAt(p.createdAt())
                .withTokenEstimate(p.tokenEstimate())
                .withMetadata(merged)
                .build();
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

    private static String truncateText(String text, int maxTokens) {
        int total = estimateTokens(text);
        if (total == 0) return text;
        int maxChars = (int) ((double) text.length() / total * maxTokens);
        return text.substring(0, Math.min(maxChars, text.length()));
    }

    // ── Stage 3: Structure ────────────────────────────────────────────────────
    // Groups packets by type and assembles a sectioned prompt template.

    private String structure(List<ContextPacket> packets, String userQuery) {
        List<String> systemInstructions = new ArrayList<>();
        List<String> evidence           = new ArrayList<>();
        List<String> context            = new ArrayList<>();

        for (ContextPacket p : packets) {
            String type = p.metadata().getOrDefault("type", "general");
            if ("system_instruction".equals(type)) {
                systemInstructions.add(p.content());
            } else if ("rag_result".equals(type) || "knowledge".equals(type)) {
                evidence.add(p.content());
            } else {
                context.add(p.content());
            }
        }

        List<String> sections = new ArrayList<>();

        if (!systemInstructions.isEmpty()) {
            sections.add("[Role & Policies]\n" + String.join("\n", systemInstructions));
        }

        if (memory != null) {
            sections.add("[Memory]\n" + memory.buildIndex()
                    + "\n\n如需查看具体记忆内容，请使用 search_memory 工具检索。");
        }

        if (userQuery != null && !userQuery.isBlank()) {
            sections.add("[Task]\n" + userQuery);
        }

        if (!evidence.isEmpty()) {
            sections.add("[Evidence]\n" + String.join("\n---\n", evidence));
        }

        if (!context.isEmpty()) {
            sections.add("[Context]\n" + String.join("\n", context));
        }

        sections.add("[Output]\n请基于以上信息，提供准确、有据的回答。");

        return String.join("\n\n", sections);
    }
}