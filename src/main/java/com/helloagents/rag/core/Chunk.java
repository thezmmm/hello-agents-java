package com.helloagents.rag.core;

import java.util.Map;
import java.util.UUID;

public record Chunk(
        String id,
        String documentId,
        String content,
        int index,
        float[] embedding,
        Map<String, String> metadata
) {
    public static Chunk of(String documentId, String content, int index) {
        return new Chunk(UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                documentId, content, index, null, Map.of());
    }

    public Chunk withEmbedding(float[] embedding) {
        return new Chunk(id, documentId, content, index, embedding, metadata);
    }
}