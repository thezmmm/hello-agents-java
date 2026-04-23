package com.helloagents.rag.tool;

import com.helloagents.rag.app.RagSearch;
import com.helloagents.rag.core.SearchResult;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool: rag_search
 * 输入格式: query=<问题>|topk=5
 */
public class RagSearchTool implements Tool {

    private final RagSearch search;

    public RagSearchTool(RagSearch search) {
        this.search = search;
    }

    @Override
    public String name() { return "rag_search"; }

    @Override
    public String description() {
        return "Semantic search in the RAG knowledge base. Input: query=<question>|topk=5";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
                ToolParameter.Param.required("query", "Search query", "string"),
                ToolParameter.Param.optional("topk", "Number of results (default 3)", "number")
        );
    }

    @Override
    public String execute(String input) {
        var params = RagToolkit.parseParams(input);
        String query = params.get("query");
        if (query == null || query.isBlank()) return "Error: query is required";
        int topK = parseIntOrDefault(params.get("topk"), 3);

        List<SearchResult> results = search.search(query, topK);
        if (results.isEmpty()) return "No results found for: " + query;

        return results.stream()
                .map(r -> String.format("[%.3f] %s", r.score(), r.content()))
                .collect(Collectors.joining("\n---\n"));
    }

    private int parseIntOrDefault(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.strip()); } catch (NumberFormatException e) { return def; }
    }
}