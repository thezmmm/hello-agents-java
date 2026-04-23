package com.helloagents.rag.core;

import java.util.List;

public interface VectorStore {

    void save(Chunk chunk);

    List<SearchResult> search(float[] queryVector, int topK);

    boolean delete(String chunkId);

    void deleteByDocument(String documentId);

    List<Chunk> listAll();

    int size();
}