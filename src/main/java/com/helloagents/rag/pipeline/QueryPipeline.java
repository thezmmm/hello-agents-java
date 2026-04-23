package com.helloagents.rag.pipeline;

import com.helloagents.rag.core.EmbeddingModel;
import com.helloagents.rag.core.KnowledgeBase;
import com.helloagents.rag.core.SearchResult;
import com.helloagents.rag.retrieval.Retriever;
import com.helloagents.rag.retrieval.VectorRetriever;

import java.util.List;
import java.util.Map;

/**
 * 查询管道：校验 → 检索 → 相似度过滤。
 * 存储访问通过 {@link KnowledgeBase} 完成。
 */
public class QueryPipeline {

    private final Retriever retriever;

    public QueryPipeline(EmbeddingModel embeddingModel, KnowledgeBase kb) {
        this.retriever = new VectorRetriever(embeddingModel, kb);
    }

    public QueryPipeline(EmbeddingModel embeddingModel, KnowledgeBase kb,
                         int defaultTopK, double minScore) {
        this.retriever = new VectorRetriever(embeddingModel, kb, defaultTopK, minScore);
    }

    /** 注入自定义 Retriever（便于测试或替换混合检索实现） */
    public QueryPipeline(Retriever retriever) {
        this.retriever = retriever;
    }

    public List<SearchResult> query(String question) {
        return retriever.retrieve(question);
    }

    public List<SearchResult> query(String question, int topK) {
        return retriever.retrieve(question, topK);
    }

    public List<SearchResult> query(String question, int topK, Map<String, String> filter) {
        return retriever.retrieve(question, topK, filter);
    }
}