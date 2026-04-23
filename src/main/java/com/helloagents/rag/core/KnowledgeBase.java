package com.helloagents.rag.core;

import java.util.List;
import java.util.Optional;

/**
 * 知识库：统一封装 {@link DocumentStore} 和 {@link VectorStore}，
 * 是两个存储的唯一入口。
 *
 * IndexPipeline、QueryPipeline、RagSystem 均只依赖此类，
 * 不再直接持有 store 实例，消除重复依赖。
 */
public class KnowledgeBase {

    private final DocumentStore documentStore;
    private final VectorStore vectorStore;

    public KnowledgeBase(DocumentStore documentStore, VectorStore vectorStore) {
        this.documentStore = documentStore;
        this.vectorStore = vectorStore;
    }

    // ── 文档操作 ─────────────────────────────────────────────────────────────

    public void saveDocument(Document doc) { documentStore.save(doc); }

    public Optional<Document> getDocument(String id) { return documentStore.get(id); }

    public boolean updateDocumentStatus(String id, DocumentStatus status) {
        return documentStore.updateStatus(id, status);
    }

    public boolean deleteDocument(String id) { return documentStore.delete(id); }

    public List<Document> listDocuments() { return documentStore.listAll(); }

    public List<Document> listByStatus(DocumentStatus status) {
        return documentStore.listByStatus(status);
    }

    public int documentCount() { return documentStore.size(); }

    // ── Chunk 操作 ────────────────────────────────────────────────────────────

    public void saveChunk(Chunk chunk) { vectorStore.save(chunk); }

    public List<SearchResult> searchChunks(float[] queryVector, int topK) {
        return vectorStore.search(queryVector, topK);
    }

    public void deleteChunksByDocument(String documentId) {
        vectorStore.deleteByDocument(documentId);
    }

    public int chunkCount() { return vectorStore.size(); }

    // ── 组合操作 ──────────────────────────────────────────────────────────────

    /** 删除文档及其所有 chunk */
    public boolean remove(String documentId) {
        vectorStore.deleteByDocument(documentId);
        return documentStore.delete(documentId);
    }

    /** 清空整个知识库（所有状态的文档） */
    public void clear() {
        for (DocumentStatus status : DocumentStatus.values()) {
            documentStore.listByStatus(status).forEach(d -> {
                vectorStore.deleteByDocument(d.id());
                documentStore.delete(d.id());
            });
        }
    }
}