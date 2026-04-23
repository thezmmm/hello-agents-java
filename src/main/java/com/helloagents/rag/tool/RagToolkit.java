package com.helloagents.rag.tool;

import com.helloagents.llm.LlmClient;
import com.helloagents.rag.app.RagManager;
import com.helloagents.rag.app.RagQA;
import com.helloagents.rag.app.RagSearch;
import com.helloagents.rag.core.DocumentStore;
import com.helloagents.rag.core.EmbeddingModel;
import com.helloagents.rag.core.VectorStore;
import com.helloagents.rag.pipeline.IndexPipeline;
import com.helloagents.rag.pipeline.QueryPipeline;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolRegistry;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** 工厂：创建并注册所有 RAG 工具（rag_add / rag_search / rag_ask） */
public class RagToolkit {

    private final RagManager manager;
    private final RagSearch search;
    private final RagQA qa;
    private final List<Tool> tools;

    public RagToolkit(EmbeddingModel embeddingModel, VectorStore vectorStore,
                      DocumentStore documentStore, LlmClient llm) {
        IndexPipeline indexPipeline = new IndexPipeline(embeddingModel, vectorStore, documentStore);
        QueryPipeline queryPipeline = new QueryPipeline(embeddingModel, vectorStore);

        this.manager = new RagManager(indexPipeline, documentStore, vectorStore);
        this.search  = new RagSearch(queryPipeline);
        this.qa      = new RagQA(queryPipeline, llm);

        this.tools = List.of(
                new RagAddTool(manager),
                new RagSearchTool(search),
                new RagAskTool(qa)
        );
    }

    public void registerAll(ToolRegistry registry) {
        tools.forEach(registry::register);
    }

    public RagManager getManager() { return manager; }
    public RagSearch  getSearch()  { return search; }
    public RagQA      getQA()      { return qa; }
    public List<Tool> getTools()   { return tools; }

    /** 解析 key=value|key2=value2 格式的参数字符串 */
    static Map<String, String> parseParams(String input) {
        Map<String, String> map = new LinkedHashMap<>();
        if (input == null || input.isBlank()) return map;
        // 支持两种格式：纯文本（无 key=）视为 content
        if (!input.contains("=")) {
            map.put("content", input.strip());
            return map;
        }
        for (String pair : input.split("\\|")) {
            int eq = pair.indexOf('=');
            if (eq > 0) map.put(pair.substring(0, eq).strip(), pair.substring(eq + 1).strip());
        }
        return map;
    }
}