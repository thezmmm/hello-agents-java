package com.helloagents.context;

/**
 * Immutable configuration for context assembly.
 *
 * <pre>
 *   ContextConfig config = ContextConfig.builder()
 *       .maxTokens(4096)
 *       .reserveRatio(0.1)
 *       .minRelevance(0.5)
 *       .enableCompression(true)
 *       .recencyWeight(0.4)
 *       .relevanceWeight(0.6)
 *       .build();
 * </pre>
 */
public final class ContextConfig {

    private static final int    DEFAULT_MAX_TOKENS       = 4096;
    private static final double DEFAULT_RESERVE_RATIO    = 0.2;
    private static final double DEFAULT_MIN_RELEVANCE    = 0.1;
    private static final boolean DEFAULT_COMPRESSION     = true;
    private static final double DEFAULT_RECENCY_WEIGHT   = 0.3;
    private static final double DEFAULT_RELEVANCE_WEIGHT = 0.7;

    private final int     maxTokens;
    private final double  reserveRatio;
    private final double  minRelevance;
    private final boolean enableCompression;
    private final double  recencyWeight;
    private final double  relevanceWeight;

    private ContextConfig(Builder b) {
        this.maxTokens         = b.maxTokens;
        this.reserveRatio      = b.reserveRatio;
        this.minRelevance      = b.minRelevance;
        this.enableCompression = b.enableCompression;
        this.recencyWeight     = b.recencyWeight;
        this.relevanceWeight   = b.relevanceWeight;
    }

    // --- factory -----------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static ContextConfig defaults() { return builder().build(); }

    // --- accessors ---------------------------------------------------------

    /** Maximum total token budget for context. */
    public int maxTokens() { return maxTokens; }

    /** Fraction of {@link #maxTokens} reserved for system instructions (0.0-1.0). */
    public double reserveRatio() { return reserveRatio; }

    /** Effective token budget available for context packets. */
    public int availableTokens() { return (int) (maxTokens * (1.0 - reserveRatio)); }

    /** Packets with relevance below this threshold are excluded (0.0-1.0). */
    public double minRelevance() { return minRelevance; }

    /** Whether context compression is enabled. */
    public boolean enableCompression() { return enableCompression; }

    /** Weight applied to recency when scoring packets (0.0-1.0). */
    public double recencyWeight() { return recencyWeight; }

    /** Weight applied to relevance when scoring packets (0.0-1.0). */
    public double relevanceWeight() { return relevanceWeight; }

    @Override
    public String toString() {
        return "ContextConfig{maxTokens=%d, reserveRatio=%.2f, minRelevance=%.2f, compression=%b, recencyWeight=%.2f, relevanceWeight=%.2f}"
                .formatted(maxTokens, reserveRatio, minRelevance,
                           enableCompression, recencyWeight, relevanceWeight);
    }

    // --- builder -----------------------------------------------------------

    public static final class Builder {

        private int     maxTokens         = DEFAULT_MAX_TOKENS;
        private double  reserveRatio      = DEFAULT_RESERVE_RATIO;
        private double  minRelevance      = DEFAULT_MIN_RELEVANCE;
        private boolean enableCompression = DEFAULT_COMPRESSION;
        private double  recencyWeight     = DEFAULT_RECENCY_WEIGHT;
        private double  relevanceWeight   = DEFAULT_RELEVANCE_WEIGHT;

        private Builder() {}

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder reserveRatio(double ratio) {
            this.reserveRatio = ratio;
            return this;
        }

        public Builder minRelevance(double minRelevance) {
            this.minRelevance = minRelevance;
            return this;
        }

        public Builder enableCompression(boolean enable) {
            this.enableCompression = enable;
            return this;
        }

        public Builder recencyWeight(double weight) {
            this.recencyWeight = weight;
            return this;
        }

        public Builder relevanceWeight(double weight) {
            this.relevanceWeight = weight;
            return this;
        }

        public ContextConfig build() {
            return new ContextConfig(this);
        }
    }
}