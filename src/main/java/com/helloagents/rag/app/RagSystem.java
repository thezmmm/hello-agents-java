package com.helloagents.rag.app;

import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;
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
import java.util.stream.Collectors;

/**
 * RAG 系统统一门面，整合文档管理、语义搜索、检索增强问答。
 *
 * <pre>
 * var kb  = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
 * var rag = new RagSystem(embeddingModel, kb, llm);
 *
 * rag.addDocument("readme.txt", content);
 * rag.addFile(Path.of("report.pdf"));
 * List&lt;SearchResult&gt; hits = rag.search("JVM 内存模型");
 * String answer = rag.ask("Java 的垃圾回收机制是什么？");
 * </pre>
 */
public class RagSystem {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant. Answer the user's question based ONLY on the provided context.
            If the context does not contain enough information, say so honestly.
            Do not make up information.""";

    private final KnowledgeBase kb;
    private final IndexPipeline indexPipeline;
    private final QueryPipeline queryPipeline;
    private final LlmClient llm;

    public RagSystem(EmbeddingModel embeddingModel, KnowledgeBase kb, LlmClient llm) {
        this.kb = kb;
        this.indexPipeline = new IndexPipeline(embeddingModel, kb);
        this.queryPipeline = new QueryPipeline(embeddingModel, kb);
        this.llm = llm;
    }

    public RagSystem(EmbeddingModel embeddingModel, KnowledgeBase kb,
                     LlmClient llm, TextSplitter splitter) {
        this.kb = kb;
        this.indexPipeline = new IndexPipeline(embeddingModel, kb, splitter);
        this.queryPipeline = new QueryPipeline(embeddingModel, kb);
        this.llm = llm;
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

    // ── 搜索 ─────────────────────────────────────────────────────────────────

    public List<SearchResult> search(String query, int topK) {
        return queryPipeline.query(query, topK);
    }

    public List<SearchResult> search(String query) {
        return queryPipeline.query(query);
    }

    // ── 问答 ─────────────────────────────────────────────────────────────────

    public String ask(String question, int topK) {
        List<SearchResult> results = search(question, topK);
        String userMessage = results.isEmpty()
                ? question
                : "Context:\n" + buildContext(results) + "\n\nQuestion: " + question;
        return llm.chat(List.of(Message.system(SYSTEM_PROMPT), Message.user(userMessage)));
    }

    public String ask(String question) {
        return ask(question, Retriever.DEFAULT_TOP_K);
    }

    private static String buildContext(List<SearchResult> results) {
        return results.stream()
                .map(r -> "[Score: %.3f] %s".formatted(r.score(), r.content()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}