package com.helloagents.context;

import java.time.Instant;
import java.util.Map;

/**
 * An immutable unit of context that can be injected into an agent's prompt.
 *
 * <p>Each packet carries the actual content, its origin (source), a relevance
 * score used for ranking, an estimated token count, and arbitrary metadata.
 *
 * <p>Create packets via the static factory methods:
 * <pre>
 *   ContextPacket p = ContextPacket.of("memory", "Paris is the capital of France.")
 *                                   .withRelevance(0.92)
 *                                   .withTokenEstimate(8)
 *                                   .build();
 * </pre>
 */
public final class ContextPacket {

    private final String content;
    private final double relevanceScore;
    private final int tokenEstimate;
    private final Instant createdAt;
    private final Map<String, String> metadata;

    private ContextPacket(Builder b) {
        this.content       = b.content;
        this.relevanceScore = b.relevanceScore;
        this.tokenEstimate = b.tokenEstimate;
        this.createdAt     = b.createdAt;
        this.metadata      = Map.copyOf(b.metadata);
    }

    // --- factory -----------------------------------------------------------

    /** Starts building a packet from the given content text. */
    public static Builder of(String content) {
        return new Builder(content);
    }

    // --- accessors ---------------------------------------------------------

    public String content()        { return content; }
    public double relevanceScore() { return relevanceScore; }
    public int    tokenEstimate()  { return tokenEstimate; }
    public Instant createdAt()     { return createdAt; }
    public Map<String, String> metadata() { return metadata; }

    /** Formats this packet for injection into an LLM prompt. */
    public String format() {
        return content;
    }

    @Override
    public String toString() {
        return "ContextPacket{relevance=%.2f, tokens=%d, content='%s'}"
                .formatted(relevanceScore, tokenEstimate, content);
    }

    // --- builder -----------------------------------------------------------

    public static final class Builder {

        private final String content;
        private double relevanceScore = 1.0;
        private int    tokenEstimate  = 0;
        private Instant createdAt     = Instant.now();
        private Map<String, String> metadata = Map.of();

        private Builder(String content) {
            this.content = content;
        }

        public Builder withRelevance(double score) {
            this.relevanceScore = score;
            return this;
        }

        public Builder withTokenEstimate(int tokens) {
            this.tokenEstimate = tokens;
            return this;
        }

        public Builder withCreatedAt(Instant instant) {
            this.createdAt = instant;
            return this;
        }

        public Builder withMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ContextPacket build() {
            return new ContextPacket(this);
        }
    }
}