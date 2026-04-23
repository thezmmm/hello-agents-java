package com.helloagents.rag.tool;

import com.helloagents.rag.app.RagSystem;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

/** Tool: rag_ask — 检索增强问答 */
public class RagAskTool implements Tool {

    private final RagSystem rag;

    public RagAskTool(RagSystem rag) { this.rag = rag; }

    @Override public String name() { return "rag_ask"; }

    @Override
    public String description() {
        return "Ask a question answered by the RAG knowledge base (retrieval + LLM). Input: question=<question>|topk=3";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
                ToolParameter.Param.required("question", "The question to answer", "string"),
                ToolParameter.Param.optional("topk",     "Number of context chunks (default 3)", "number")
        );
    }

    @Override
    public String execute(String input) {
        var params = RagToolkit.parseParams(input);
        String question = params.get("question");
        if (question == null || question.isBlank()) return "Error: question is required";
        int topK = parseIntOrDefault(params.get("topk"), 3);
        return rag.ask(question, topK);
    }

    private static int parseIntOrDefault(String s, int def) {
        try { return s != null ? Integer.parseInt(s.strip()) : def; }
        catch (NumberFormatException e) { return def; }
    }
}