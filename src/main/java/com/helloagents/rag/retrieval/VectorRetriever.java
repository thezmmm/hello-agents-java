package com.helloagents.rag.retrieval;

import com.helloagents.rag.core.Chunk;
import com.helloagents.rag.core.EmbeddingModel;
import com.helloagents.rag.core.SearchResult;
import com.helloagents.rag.core.VectorStore;

import java.util.List;
import java.util.Map;

/**
 * 基于向量相似度的检索实现。
 *
 * 支持：
 *  - 可配置的 defaultTopK 和 minScore 阈值
 *  - 元数据过滤（documentId / chunk metadata 字段）
 *  - query 合法性校验
 */
public class VectorRetriever implements Retriever {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final int defaultTopK;
    private final double minScore;

    public VectorRetriever(EmbeddingModel embeddingModel, VectorStore vectorStore) {
        this(embeddingModel, vectorStore, DEFAULT_TOP_K, 0.0);
    }

    public VectorRetriever(EmbeddingModel embeddingModel, VectorStore vectorStore,
                           int defaultTopK, double minScore) {
        if (defaultTopK <= 0) throw new IllegalArgumentException("defaultTopK must be positive");
        if (minScore < 0 || minScore > 1) throw new IllegalArgumentException("minScore must be in [0, 1]");
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.defaultTopK = defaultTopK;
        this.minScore = minScore;
    }

    @Override
    public List<SearchResult> retrieve(String query, int topK, Map<String, String> filter) {
        if (query == null || query.isBlank()) return List.of();

        float[] queryVector = embeddingModel.embed(query).vector();

        return vectorStore.search(queryVector, topK).stream()
                .filter(r -> r.score() >= minScore)
                .filter(r -> matchesFilter(r.chunk(), filter))
                .toList();
    }

    @Override
    public List<SearchResult> retrieve(String query) {
        return retrieve(query, defaultTopK, Map.of());
    }

    private static boolean matchesFilter(Chunk chunk, Map<String, String> filter) {
        if (filter.isEmpty()) return true;
        for (var entry : filter.entrySet()) {
            String actual = entry.getKey().equals("documentId")
                    ? chunk.documentId()
                    : chunk.metadata().get(entry.getKey());
            if (!entry.getValue().equals(actual)) return false;
        }
        return true;
    }
}