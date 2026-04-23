package com.helloagents.rag.tool;

import com.helloagents.rag.app.RagSystem;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

import java.nio.file.Path;

/**
 * Tool: rag_add
 * 文本内容：source=<名称>|content=<文本>
 * 本地文件：file=<路径>（支持 PDF/Word/Excel/HTML 等）
 */
public class RagAddTool implements Tool {

    private final RagSystem rag;

    public RagAddTool(RagSystem rag) { this.rag = rag; }

    @Override public String name() { return "rag_add"; }

    @Override
    public String description() {
        return "Add a document to the RAG knowledge base. " +
               "Text: source=<name>|content=<text>  File: file=<path>";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
                ToolParameter.Param.optional("file",    "Local file path (PDF, DOCX, XLSX, HTML, ...)", "string"),
                ToolParameter.Param.optional("source",  "Document name or identifier", "string"),
                ToolParameter.Param.optional("content", "Plain text content", "string")
        );
    }

    @Override
    public String execute(String input) {
        var params = RagToolkit.parseParams(input);
        String file = params.get("file");
        if (file != null && !file.isBlank()) {
            try {
                Path path = Path.of(file.strip());
                String docId = rag.addFile(path);
                return "File indexed. id=%s source=%s total_chunks=%d"
                        .formatted(docId, path.getFileName(), rag.chunkCount());
            } catch (Exception e) {
                return "Error indexing file: " + e.getMessage();
            }
        }
        String content = params.get("content");
        if (content == null || content.isBlank()) return "Error: provide file=<path> or content=<text>";
        String source = params.getOrDefault("source", "unknown");
        String docId  = rag.addDocument(source, content);
        return "Document added. id=%s source=%s total_chunks=%d"
                .formatted(docId, source, rag.chunkCount());
    }
}