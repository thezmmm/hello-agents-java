package com.helloagents.rag.retrieval;

import com.helloagents.rag.core.SearchResult;

import java.util.List;
import java.util.Map;

public interface Retriever {

    /** 接口级默认值，各实现可在构造时覆盖 */
    int DEFAULT_TOP_K = 3;

    /**
     * 带过滤条件的检索。
     *
     * <p>filter 支持的 key：
     * <ul>
     *   <li>{@code "documentId"} — 只检索指定文档的 chunk</li>
     *   <li>其他 key — 匹配 chunk 元数据中对应字段</li>
     * </ul>
     * 多个条件取 AND。空 Map 表示不过滤。
     */
    List<SearchResult> retrieve(String query, int topK, Map<String, String> filter);

    default List<SearchResult> retrieve(String query, int topK) {
        return retrieve(query, topK, Map.of());
    }

    default List<SearchResult> retrieve(String query) {
        return retrieve(query, DEFAULT_TOP_K, Map.of());
    }
}