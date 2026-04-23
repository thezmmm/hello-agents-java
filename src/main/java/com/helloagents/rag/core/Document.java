package com.helloagents.rag.core;

import java.util.Map;
import java.util.UUID;

public record Document(
        String id,
        String source,
        String content,
        Map<String, String> metadata
) {
    public static Document of(String source, String content) {
        return new Document(UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                source, content, Map.of());
    }

    public static Document of(String source, String content, Map<String, String> metadata) {
        return new Document(UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                source, content, Map.copyOf(metadata));
    }
}