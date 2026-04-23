package com.helloagents.rag.tool;

import com.helloagents.llm.LlmClient;
import com.helloagents.rag.app.RagSystem;
import com.helloagents.rag.core.EmbeddingModel;
import com.helloagents.rag.core.KnowledgeBase;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工厂：创建并注册所有 RAG 工具（rag_add / rag_search / rag_ask） */
public class RagToolkit {

    private final RagSystem ragSystem;
    private final List<Tool> tools;

    public RagToolkit(EmbeddingModel embeddingModel, KnowledgeBase kb, LlmClient llm) {
        this.ragSystem = new RagSystem(embeddingModel, kb, llm);
        this.tools = List.of(
                new RagAddTool(ragSystem),
                new RagSearchTool(ragSystem),
                new RagAskTool(ragSystem)
        );
    }

    public void registerAll(ToolRegistry registry) {
        tools.forEach(registry::register);
    }

    public RagSystem getRagSystem() { return ragSystem; }
    public List<Tool> getTools()    { return tools; }

    /** 解析 key=value|key2=value2 格式的参数字符串 */
    static Map<String, String> parseParams(String input) {
        Map<String, String> map = new LinkedHashMap<>();
        if (input == null || input.isBlank()) return map;
        if (!input.contains("=")) { map.put("content", input.strip()); return map; }
        for (String pair : input.split("\\|")) {
            int eq = pair.indexOf('=');
            if (eq > 0) map.put(pair.substring(0, eq).strip(), pair.substring(eq + 1).strip());
        }
        return map;
    }
}