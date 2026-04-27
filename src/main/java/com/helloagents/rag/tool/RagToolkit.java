package com.helloagents.rag.tool;

import com.helloagents.llm.LlmClient;
import com.helloagents.rag.app.RagSystem;
import com.helloagents.rag.core.EmbeddingModel;
import com.helloagents.rag.core.KnowledgeBase;
import com.helloagents.tools.Tool;
import com.helloagents.tools.Toolkit;
import com.helloagents.tools.ToolRegistry;

import java.util.List;

/** 工厂：创建并注册所有 RAG 工具（rag_add / rag_search / rag_ask） */
public class RagToolkit implements Toolkit {

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

    @Override public String name()        { return "rag"; }
    @Override public String description() { return "Tools for adding documents, searching, and answering questions via retrieval-augmented generation"; }

    public RagSystem getRagSystem()        { return ragSystem; }
    @Override public List<Tool> getTools() { return tools; }

}