package com.helloagents.rag.document.parser;

import com.helloagents.rag.core.Document;
import com.helloagents.rag.document.DocumentParser;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Apache Tika 的通用文档解析器。
 *
 * 支持格式：PDF、Word/Excel/PowerPoint（.doc/.docx/.xls/.xlsx/.ppt/.pptx）、
 * HTML/XML、纯文本、CSV、JSON、图片（EXIF 元数据；OCR 需安装 Tesseract）等。
 *
 * 推荐使用 parseFile(Path) 直接传入文件路径，以获得完整的格式支持。
 * parse(source, content) 处理已是文本的内容直通场景。
 */
public class TikaDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED = Set.of(
            "pdf",
            "doc", "docx", "odt", "rtf",
            "xls", "xlsx", "ods", "csv",
            "ppt", "pptx", "odp",
            "html", "htm", "xhtml", "xml",
            "txt", "md", "markdown", "rst", "json",
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp",
            "zip", "epub"
    );

    private final Tika tika;

    public TikaDocumentParser() {
        this.tika = new Tika();
        this.tika.setMaxStringLength(-1); // 不限制提取长度
    }

    /**
     * 直接解析文件（推荐）：Tika 自动检测格式并提取纯文本。
     */
    public Document parseFile(Path filePath) {
        try (InputStream stream = Files.newInputStream(filePath)) {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filePath.getFileName().toString());
            String content = tika.parseToString(stream, metadata);
            return Document.of(filePath.toString(), content.strip(), buildMeta(metadata, filePath));
        } catch (IOException | TikaException e) {
            throw new TikaParseException("Failed to parse: " + filePath, e);
        }
    }

    /**
     * 解析已为文本的内容（如从网络获取的 HTML 字符串）。
     * Tika 负责去除标签、提取正文。
     */
    @Override
    public Document parse(String source, String content) {
        try {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, source);
            String parsed = tika.parseToString(
                    new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    metadata);
            return Document.of(source, parsed.strip(), Map.of("parser", "tika", "source_type", "text"));
        } catch (IOException | TikaException e) {
            // 解析失败时直通原始文本
            return Document.of(source, content, Map.of("parser", "tika-passthrough"));
        }
    }

    @Override
    public boolean supports(String source) {
        int dot = source.lastIndexOf('.');
        if (dot < 0) return false;
        return SUPPORTED.contains(source.substring(dot + 1).toLowerCase());
    }

    private static Map<String, String> buildMeta(Metadata metadata, Path filePath) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("parser", "tika");
        meta.put("source_file", filePath.getFileName().toString());
        String title = metadata.get(TikaCoreProperties.TITLE);
        if (title != null && !title.isBlank()) meta.put("title", title);
        String author = metadata.get(TikaCoreProperties.CREATOR);
        if (author != null && !author.isBlank()) meta.put("author", author);
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType != null) meta.put("content_type", contentType);
        String created = metadata.get(TikaCoreProperties.CREATED);
        if (created != null) meta.put("created", created);
        return Map.copyOf(meta);
    }

    public static class TikaParseException extends RuntimeException {
        public TikaParseException(String message, Throwable cause) { super(message, cause); }
    }
}