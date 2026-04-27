package com.helloagents.rag.app;

import com.helloagents.rag.core.Document;
import com.helloagents.rag.core.EmbeddingModel;
import com.helloagents.rag.core.KnowledgeBase;
import com.helloagents.rag.core.SearchResult;
import com.helloagents.rag.document.TextSplitter;
import com.helloagents.rag.pipeline.IndexPipeline;
import com.helloagents.rag.pipeline.QueryPipeline;
import com.helloagents.rag.retrieval.Retriever;

import java.nio.file.Path;
import java.util.List;

/**
 * RAG 系统统一门面，整合文档管理与语义检索。
 *
 * <pre>
 * var kb  = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
 * var rag = new RagSystem(embeddingModel, kb);
 *
 * rag.addDocument("readme.txt", content);
 * rag.addFile(Path.of("report.pdf"));
 * List&lt;SearchResult&gt; hits = rag.search("JVM 内存模型");
 * </pre>
 */
public class RagSystem {

    private final KnowledgeBase  kb;
    private final IndexPipeline  indexPipeline;
    private final QueryPipeline  queryPipeline;

    public RagSystem(EmbeddingModel embeddingModel, KnowledgeBase kb) {
        this.kb            = kb;
        this.indexPipeline = new IndexPipeline(embeddingModel, kb);
        this.queryPipeline = new QueryPipeline(embeddingModel, kb);
    }

    public RagSystem(EmbeddingModel embeddingModel, KnowledgeBase kb, TextSplitter splitter) {
        this.kb            = kb;
        this.indexPipeline = new IndexPipeline(embeddingModel, kb, splitter);
        this.queryPipeline = new QueryPipeline(embeddingModel, kb);
    }

    // ── 管理 ─────────────────────────────────────────────────────────────────

    /** 索引文本内容，返回文档 ID */
    public String addDocument(String source, String content) {
        return indexPipeline.index(source, content);
    }

    /** 索引本地文件（PDF、Word、Excel、HTML 等），返回文档 ID */
    public String addFile(Path filePath) {
        return indexPipeline.indexFile(filePath);
    }

    /** 删除文档及其所有 chunk */
    public boolean removeDocument(String documentId) {
        return indexPipeline.remove(documentId);
    }

    public List<Document> listDocuments() { return kb.listDocuments(); }

    public int documentCount() { return kb.documentCount(); }

    public int chunkCount()    { return kb.chunkCount(); }

    /** 清空整个知识库 */
    public void clear() { kb.clear(); }

    // ── 检索 ─────────────────────────────────────────────────────────────────

    public List<SearchResult> search(String query, int topK) {
        return queryPipeline.query(query, topK);
    }

    public List<SearchResult> search(String query) {
        return queryPipeline.query(query, Retriever.DEFAULT_TOP_K);
    }
}