package com.helloagents.context;

import com.helloagents.rag.core.EmbeddingModel;
import com.helloagents.rag.embedding.OpenAiEmbeddingModel;
import io.github.cdimascio.dotenv.Dotenv;

/**
 * Immutable configuration for context assembly.
 *
 * <pre>
 *   // from .env / environment variables
 *   ContextConfig config = ContextConfig.fromEnv();
 *
 *   // programmatic
 *   ContextConfig config = ContextConfig.builder()
 *       .maxTokens(4096)
 *       .embeddingModel(myModel)
 *       .build();
 * </pre>
 *
 * <p>Environment variables:
 * <table>
 *   <tr><th>Variable</th><th>Default</th></tr>
 *   <tr><td>CONTEXT_MAX_TOKENS</td>         <td>4096</td></tr>
 *   <tr><td>CONTEXT_RESERVE_RATIO</td>      <td>0.2</td></tr>
 *   <tr><td>CONTEXT_MIN_RELEVANCE</td>      <td>0.1</td></tr>
 *   <tr><td>CONTEXT_ENABLE_COMPRESSION</td> <td>true</td></tr>
 *   <tr><td>CONTEXT_RECENCY_WEIGHT</td>     <td>0.3</td></tr>
 *   <tr><td>CONTEXT_RELEVANCE_WEIGHT</td>   <td>0.7</td></tr>
 *   <tr><td>EMBEDDING_MODEL / LLM_API_KEY</td><td>—（optional，absent = Jaccard fallback）</td></tr>
 * </table>
 */
public final class ContextConfig {

    private static final int    DEFAULT_MAX_TOKENS       = 4096;
    private static final double DEFAULT_RESERVE_RATIO    = 0.2;
    private static final double DEFAULT_MIN_RELEVANCE    = 0.1;
    private static final boolean DEFAULT_COMPRESSION     = true;
    private static final double DEFAULT_RECENCY_WEIGHT   = 0.3;
    private static final double DEFAULT_RELEVANCE_WEIGHT = 0.7;

    private final int            maxTokens;
    private final double         reserveRatio;
    private final double         minRelevance;
    private final boolean        enableCompression;
    private final double         recencyWeight;
    private final double         relevanceWeight;
    private final EmbeddingModel embeddingModel;

    private ContextConfig(Builder b) {
        this.maxTokens         = b.maxTokens;
        this.reserveRatio      = b.reserveRatio;
        this.minRelevance      = b.minRelevance;
        this.enableCompression = b.enableCompression;
        this.recencyWeight     = b.recencyWeight;
        this.relevanceWeight   = b.relevanceWeight;
        this.embeddingModel    = b.embeddingModel;
    }

    // ── factory ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static ContextConfig defaults() { return builder().build(); }

    /**
     * Loads all parameters from environment variables / {@code .env}.
     * The embedding model is created via {@link OpenAiEmbeddingModel#fromEnv()};
     * if the API key is absent the field is silently left null (Jaccard fallback).
     */
    public static ContextConfig fromEnv() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        EmbeddingModel model = null;
        try {
            model = OpenAiEmbeddingModel.fromEnv();
        } catch (Exception ignored) {
            // No API key configured — embedding-based relevance disabled
        }

        return builder()
                .maxTokens(parseInt(dotenv.get("CONTEXT_MAX_TOKENS"), DEFAULT_MAX_TOKENS))
                .reserveRatio(parseDouble(dotenv.get("CONTEXT_RESERVE_RATIO"), DEFAULT_RESERVE_RATIO))
                .minRelevance(parseDouble(dotenv.get("CONTEXT_MIN_RELEVANCE"), DEFAULT_MIN_RELEVANCE))
                .enableCompression(parseBool(dotenv.get("CONTEXT_ENABLE_COMPRESSION"), DEFAULT_COMPRESSION))
                .recencyWeight(parseDouble(dotenv.get("CONTEXT_RECENCY_WEIGHT"), DEFAULT_RECENCY_WEIGHT))
                .relevanceWeight(parseDouble(dotenv.get("CONTEXT_RELEVANCE_WEIGHT"), DEFAULT_RELEVANCE_WEIGHT))
                .embeddingModel(model)
                .build();
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public int     maxTokens()        { return maxTokens;         }
    public double  reserveRatio()     { return reserveRatio;      }
    public int     availableTokens()  { return (int) (maxTokens * (1.0 - reserveRatio)); }
    public double  minRelevance()     { return minRelevance;      }
    public boolean enableCompression(){ return enableCompression; }
    public double  recencyWeight()    { return recencyWeight;     }
    public double  relevanceWeight()  { return relevanceWeight;   }

    /** Returns the configured embedding model, or {@code null} if not set (Jaccard fallback). */
    public EmbeddingModel embeddingModel() { return embeddingModel; }

    @Override
    public String toString() {
        return "ContextConfig{maxTokens=%d, reserveRatio=%.2f, minRelevance=%.2f, compression=%b, recencyWeight=%.2f, relevanceWeight=%.2f, embedding=%s}"
                .formatted(maxTokens, reserveRatio, minRelevance, enableCompression,
                           recencyWeight, relevanceWeight,
                           embeddingModel != null ? embeddingModel.modelInfo().modelId() : "none");
    }

    // ── builder ───────────────────────────────────────────────────────────────

    public static final class Builder {

        private int            maxTokens         = DEFAULT_MAX_TOKENS;
        private double         reserveRatio      = DEFAULT_RESERVE_RATIO;
        private double         minRelevance      = DEFAULT_MIN_RELEVANCE;
        private boolean        enableCompression = DEFAULT_COMPRESSION;
        private double         recencyWeight     = DEFAULT_RECENCY_WEIGHT;
        private double         relevanceWeight   = DEFAULT_RELEVANCE_WEIGHT;
        private EmbeddingModel embeddingModel    = null;

        private Builder() {}

        public Builder maxTokens(int maxTokens)               { this.maxTokens = maxTokens; return this; }
        public Builder reserveRatio(double ratio)             { this.reserveRatio = ratio; return this; }
        public Builder minRelevance(double minRelevance)      { this.minRelevance = minRelevance; return this; }
        public Builder enableCompression(boolean enable)      { this.enableCompression = enable; return this; }
        public Builder recencyWeight(double weight)           { this.recencyWeight = weight; return this; }
        public Builder relevanceWeight(double weight)         { this.relevanceWeight = weight; return this; }
        public Builder embeddingModel(EmbeddingModel model)   { this.embeddingModel = model; return this; }

        public ContextConfig build() { return new ContextConfig(this); }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value.strip()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Double.parseDouble(value.strip()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static boolean parseBool(String value, boolean fallback) {
        if (value == null || value.isBlank()) return fallback;
        return Boolean.parseBoolean(value.strip());
    }
}