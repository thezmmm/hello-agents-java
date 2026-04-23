package com.helloagents.rag.core;

public record SearchResult(
        Chunk chunk,
        double score
) {
    public String documentId() {
        return chunk.documentId();
    }

    public String content() {
        return chunk.content();
    }
}