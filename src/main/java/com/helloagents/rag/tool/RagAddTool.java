package com.helloagents.rag.tool;

import com.helloagents.rag.app.RagManager;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;

import java.nio.file.Path;

/**
 * Tool: rag_add
 *
 * 两种输入格式：
 *  文本内容：source=<名称>|content=<文本>
 *  本地文件：file=<路径>（支持 PDF、Word、Excel、HTML 等，由 Apache Tika 解析）
 */
public class RagAddTool implements Tool {

    private final RagManager manager;

    public RagAddTool(RagManager manager) {
        this.manager = manager;
    }

    @Override
    public String name() { return "rag_add"; }

    @Override
    public String description() {
        return "Add a document to the RAG knowledge base. " +
               "Text: source=<name>|content=<text>  File: file=<path> (PDF/Word/Excel/HTML/etc.)";
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

        // 优先处理文件路径
        String file = params.get("file");
        if (file != null && !file.isBlank()) {
            try {
                Path path = Path.of(file.strip());
                String docId = manager.addFile(path);
                return String.format("File indexed. id=%s source=%s total_chunks=%d",
                        docId, path.getFileName(), manager.chunkCount());
            } catch (Exception e) {
                return "Error indexing file: " + e.getMessage();
            }
        }

        // 文本内容
        String source  = params.getOrDefault("source", "unknown");
        String content = params.get("content");
        if (content == null || content.isBlank()) return "Error: provide file=<path> or content=<text>";
        String docId = manager.addDocument(source, content);
        return String.format("Document added. id=%s source=%s total_chunks=%d",
                docId, source, manager.chunkCount());
    }
}