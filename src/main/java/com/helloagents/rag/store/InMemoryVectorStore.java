package com.helloagents.rag.store;

import com.helloagents.rag.core.Chunk;
import com.helloagents.rag.core.SearchResult;
import com.helloagents.rag.core.VectorStore;

import java.util.*;
import java.util.stream.Collectors;

/** 内存向量库，使用余弦相似度暴力线性扫描，适合万级以内文档 */
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, Chunk> store = new LinkedHashMap<>();

    @Override
    public void save(Chunk chunk) {
        store.put(chunk.id(), chunk);
    }

    @Override
    public List<SearchResult> search(float[] queryVector, int topK) {
        return store.values().stream()
                .filter(c -> c.embedding() != null)
                .map(c -> new SearchResult(c, cosineSimilarity(queryVector, c.embedding())))
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public boolean delete(String chunkId) {
        return store.remove(chunkId) != null;
    }

    @Override
    public void deleteByDocument(String documentId) {
        store.values().removeIf(c -> documentId.equals(c.documentId()));
    }

    @Override
    public List<Chunk> listAll() {
        return List.copyOf(store.values());
    }

    @Override
    public int size() {
        return store.size();
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("Vector dimension mismatch");
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}