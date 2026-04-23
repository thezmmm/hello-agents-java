package com.helloagents.rag.pipeline;

import com.helloagents.rag.core.*;
import com.helloagents.rag.document.TextSplitter;
import com.helloagents.rag.document.parser.TikaDocumentParser;
import com.helloagents.rag.document.splitter.FixedSizeSplitter;

import java.nio.file.Path;
import java.util.List;

/**
 * 索引管道：解析 → 分块 → 向量化 → 存储。
 * 所有存储操作通过 {@link KnowledgeBase} 统一完成。
 */
public class IndexPipeline {

    private final EmbeddingModel embeddingModel;
    private final KnowledgeBase kb;
    private final TextSplitter splitter;
    private final TikaDocumentParser tikaParser;

    public IndexPipeline(EmbeddingModel embeddingModel, KnowledgeBase kb) {
        this(embeddingModel, kb, new FixedSizeSplitter());
    }

    public IndexPipeline(EmbeddingModel embeddingModel, KnowledgeBase kb, TextSplitter splitter) {
        this.embeddingModel = embeddingModel;
        this.kb = kb;
        this.splitter = splitter;
        this.tikaParser = new TikaDocumentParser();
    }

    /** 索引文本内容，返回文档 ID */
    public String index(String source, String content) {
        Document document = tikaParser.supports(source)
                ? tikaParser.parse(source, content)
                : Document.of(source, content);
        return saveAndEmbed(document);
    }

    /** 直接索引文件，Apache Tika 自动识别格式并提取文本 */
    public String indexFile(Path filePath) {
        return saveAndEmbed(tikaParser.parseFile(filePath));
    }

    /** 删除文档及其所有 chunk */
    public boolean remove(String documentId) {
        return kb.remove(documentId);
    }

    private String saveAndEmbed(Document document) {
        kb.saveDocument(document); // PENDING
        try {
            List<String> chunkTexts = splitter.split(document.content());
            List<Embedding> embeddings = embeddingModel.embedBatch(chunkTexts);
            for (int i = 0; i < chunkTexts.size(); i++) {
                kb.saveChunk(Chunk.of(document.id(), chunkTexts.get(i), i)
                        .withEmbedding(embeddings.get(i).vector()));
            }
            kb.updateDocumentStatus(document.id(), DocumentStatus.INDEXED);
        } catch (Exception e) {
            kb.updateDocumentStatus(document.id(), DocumentStatus.FAILED);
            throw e;
        }
        return document.id();
    }
}