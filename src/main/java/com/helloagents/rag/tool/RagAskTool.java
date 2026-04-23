package com.helloagents.rag.tool;

import com.helloagents.rag.app.RagQA;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

/**
 * Tool: rag_ask
 * 输入格式: question=<问题>|topk=3
 */
public class RagAskTool implements Tool {

    private final RagQA qa;

    public RagAskTool(RagQA qa) {
        this.qa = qa;
    }

    @Override
    public String name() { return "rag_ask"; }

    @Override
    public String description() {
        return "Ask a question answered by the RAG knowledge base (retrieval + LLM). Input: question=<question>|topk=3";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
                ToolParameter.Param.required("question", "The question to answer", "string"),
                ToolParameter.Param.optional("topk", "Number of context chunks (default 3)", "number")
        );
    }

    @Override
    public String execute(String input) {
        var params = RagToolkit.parseParams(input);
        String question = params.get("question");
        if (question == null || question.isBlank()) return "Error: question is required";
        int topK = parseIntOrDefault(params.get("topk"), 3);
        return qa.ask(question, topK);
    }

    private int parseIntOrDefault(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.strip()); } catch (NumberFormatException e) { return def; }
    }
}