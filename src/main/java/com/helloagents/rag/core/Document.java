package com.helloagents.rag.core;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Document(
        String id,
        String source,
        String content,
        Map<String, String> metadata,
        Instant createdAt,
        DocumentStatus status
) {
    public static Document of(String source, String content) {
        return new Document(newId(), source, content, Map.of(), Instant.now(), DocumentStatus.PENDING);
    }

    public static Document of(String source, String content, Map<String, String> metadata) {
        return new Document(newId(), source, content, Map.copyOf(metadata), Instant.now(), DocumentStatus.PENDING);
    }

    public Document withStatus(DocumentStatus newStatus) {
        return new Document(id, source, content, metadata, createdAt, newStatus);
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}