package com.helloagents.rag.document.parser;

import com.helloagents.rag.core.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser();

    // ── supports ──────────────────────────────────────────────────────────

    @Test
    void supports_knownDocumentFormats() {
        assertTrue(parser.supports("report.pdf"));
        assertTrue(parser.supports("notes.docx"));
        assertTrue(parser.supports("data.xlsx"));
        assertTrue(parser.supports("slides.pptx"));
        assertTrue(parser.supports("page.html"));
        assertTrue(parser.supports("readme.md"));
        assertTrue(parser.supports("records.csv"));
        assertTrue(parser.supports("config.json"));
    }

    @Test
    void supports_caseInsensitive() {
        assertTrue(parser.supports("REPORT.PDF"));
        assertTrue(parser.supports("Notes.DOCX"));
    }

    @Test
    void supports_unknownExtension() {
        assertFalse(parser.supports("binary.exe"));
        assertFalse(parser.supports("archive.rar"));
    }

    @Test
    void supports_noExtension() {
        assertFalse(parser.supports("README"));
        assertFalse(parser.supports("Makefile"));
    }

    // ── parse(source, content) ────────────────────────────────────────────

    @Test
    void parse_plainText_contentPreserved() {
        Document doc = parser.parse("notes.txt", "Hello World");
        assertEquals("notes.txt", doc.source());
        assertTrue(doc.content().contains("Hello World"));
        assertEquals("tika", doc.metadata().get("parser"));
    }

    @Test
    void parse_html_stripsTagsExtractsText() {
        String html = "<html><body><h1>Title</h1><p>Some content here.</p></body></html>";
        Document doc = parser.parse("page.html", html);
        assertFalse(doc.content().contains("<h1>"), "HTML tags should be stripped");
        assertTrue(doc.content().contains("Title"));
        assertTrue(doc.content().contains("Some content here"));
    }

    @Test
    void parse_empty_returnsDocument() {
        assertDoesNotThrow(() -> {
            Document doc = parser.parse("empty.txt", "");
            assertNotNull(doc);
            assertEquals("empty.txt", doc.source());
        });
    }

    @Test
    void parse_sourceIsPreserved() {
        Document doc = parser.parse("my-doc.txt", "some content");
        assertEquals("my-doc.txt", doc.source());
    }

    // ── parseFile(Path) ───────────────────────────────────────────────────

    @Test
    void parseFile_plainText(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("hello.txt");
        Files.writeString(file, "Apache Tika is great for document parsing.");

        Document doc = parser.parseFile(file);

        assertEquals(file.toString(), doc.source());
        assertTrue(doc.content().contains("Apache Tika"));
        assertEquals("tika", doc.metadata().get("parser"));
        assertEquals("hello.txt", doc.metadata().get("source_file"));
    }

    @Test
    void parseFile_html_stripsTagsExtractsText(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("page.html");
        Files.writeString(file, """
                <html>
                  <head><title>Test Page</title></head>
                  <body><p>Main content of the page.</p></body>
                </html>
                """);

        Document doc = parser.parseFile(file);

        assertFalse(doc.content().contains("<p>"), "HTML tags should be removed");
        assertTrue(doc.content().contains("Main content of the page"));
    }

    @Test
    void parseFile_csv(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.csv");
        Files.writeString(file, "name,age\nAlice,30\nBob,25");

        Document doc = parser.parseFile(file);

        assertNotNull(doc.content());
        assertFalse(doc.content().isBlank());
    }

    @Test
    void parseFile_markdownTreatedAsText(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("readme.md");
        Files.writeString(file, "# Title\n\nSome **bold** text and a [link](http://example.com).");

        Document doc = parser.parseFile(file);

        assertNotNull(doc.content());
        assertTrue(doc.content().contains("Title") || doc.content().contains("bold"));
    }

    @Test
    void parseFile_metadataContainsContentType(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("note.txt");
        Files.writeString(file, "content");

        Document doc = parser.parseFile(file);

        assertNotNull(doc.metadata().get("content_type"));
    }

    @Test
    void parseFile_nonexistent_throwsTikaParseException() {
        Path missing = Path.of("/nonexistent/path/file.txt");
        assertThrows(TikaDocumentParser.TikaParseException.class,
                () -> parser.parseFile(missing));
    }

    @Test
    void parseFile_idIsGenerated(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("doc.txt");
        Files.writeString(file, "content");

        Document doc = parser.parseFile(file);

        assertNotNull(doc.id());
        assertFalse(doc.id().isBlank());
    }
}