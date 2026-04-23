package com.helloagents.rag.tool;

import com.helloagents.rag.app.RagSystem;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

import java.util.stream.Collectors;

/** Tool: rag_search — 语义搜索，返回相关段落 */
public class RagSearchTool implements Tool {

    private final RagSystem rag;

    public RagSearchTool(RagSystem rag) { this.rag = rag; }

    @Override public String name() { return "rag_search"; }

    @Override
    public String description() {
        return "Semantic search in the RAG knowledge base. Input: query=<question>|topk=5";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
                ToolParameter.Param.required("query", "Search query", "string"),
                ToolParameter.Param.optional("topk",  "Number of results (default 3)", "number")
        );
    }

    @Override
    public String execute(String input) {
        var params = RagToolkit.parseParams(input);
        String query = params.get("query");
        if (query == null || query.isBlank()) return "Error: query is required";
        int topK = parseIntOrDefault(params.get("topk"), 3);
        var results = rag.search(query, topK);
        if (results.isEmpty()) return "No results found for: " + query;
        return results.stream()
                .map(r -> "[%.3f] %s".formatted(r.score(), r.content()))
                .collect(Collectors.joining("\n---\n"));
    }

    private static int parseIntOrDefault(String s, int def) {
        try { return s != null ? Integer.parseInt(s.strip()) : def; }
        catch (NumberFormatException e) { return def; }
    }
}
