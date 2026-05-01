package com.helloagents.tools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpToolTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private HttpTool mockTool(String fixedResponse) {
        return new HttpTool((url, method, headers, body) -> fixedResponse);
    }

    private HttpTool capturingTool(String[] capturedUrl, String[] capturedMethod,
                                   Map<String, String>[] capturedHeaders, String[] capturedBody,
                                   String response) {
        return new HttpTool((url, method, headers, body) -> {
            capturedUrl[0]     = url;
            capturedMethod[0]  = method;
            capturedHeaders[0] = headers;
            capturedBody[0]    = body;
            return response;
        });
    }

    // ── metadata ──────────────────────────────────────────────────────────────

    @Test
    void toolMetadata() {
        HttpTool tool = mockTool("[HTTP 200]\nok");
        assertEquals("http_fetch", tool.name());
        assertFalse(tool.description().isBlank());
        assertFalse(tool.parameters().isEmpty());
    }

    @Test
    void descriptionMentionsWebSearch() {
        // Guides the model to use web_search for discovery, http_fetch for reading.
        assertTrue(new HttpTool((url, method, headers, body) -> "").description().contains("web_search"),
                "Description should tell the model when NOT to use this tool");
    }

    // ── URL validation ────────────────────────────────────────────────────────

    @Test
    void requiresUrlParam() {
        String result = mockTool("[HTTP 200]\nok").execute(Map.of());
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void rejectsBlankUrl() {
        String result = mockTool("[HTTP 200]\nok").execute(Map.of("url", "  "));
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void rejectsNonHttpUrl() {
        String result = mockTool("[HTTP 200]\nok").execute(Map.of("url", "ftp://example.com"));
        assertTrue(result.startsWith("Error:"), "ftp:// should be rejected");
    }

    @Test
    void acceptsHttpsUrl() {
        String result = mockTool("[HTTP 200]\nhello").execute(Map.of("url", "https://example.com"));
        assertFalse(result.startsWith("Error:"));
    }

    @Test
    void acceptsHttpUrl() {
        String result = mockTool("[HTTP 200]\nhello").execute(Map.of("url", "http://example.com"));
        assertFalse(result.startsWith("Error:"));
    }

    // ── method handling ───────────────────────────────────────────────────────

    @Test
    void defaultMethodIsGet() {
        var capturedMethod = new String[1];
        @SuppressWarnings("unchecked")
        var capturedHeaders = new Map[1];
        var tool = capturingTool(new String[1], capturedMethod, capturedHeaders, new String[1], "[HTTP 200]\nok");
        tool.execute(Map.of("url", "https://example.com"));
        assertEquals("GET", capturedMethod[0]);
    }

    @Test
    void postMethodIsForwarded() {
        var capturedMethod = new String[1];
        @SuppressWarnings("unchecked")
        var capturedHeaders = new Map[1];
        var tool = capturingTool(new String[1], capturedMethod, capturedHeaders, new String[1], "[HTTP 200]\nok");
        tool.execute(Map.of("url", "https://example.com", "method", "POST", "body", "{}"));
        assertEquals("POST", capturedMethod[0]);
    }

    @Test
    void methodIsCaseInsensitive() {
        var capturedMethod = new String[1];
        @SuppressWarnings("unchecked")
        var capturedHeaders = new Map[1];
        var tool = capturingTool(new String[1], capturedMethod, capturedHeaders, new String[1], "[HTTP 200]\nok");
        tool.execute(Map.of("url", "https://example.com", "method", "post"));
        assertEquals("POST", capturedMethod[0]);
    }

    @Test
    void rejectsUnsupportedMethod() {
        String result = mockTool("[HTTP 200]\nok")
                .execute(Map.of("url", "https://example.com", "method", "DELETE"));
        assertTrue(result.startsWith("Error:"));
    }

    // ── body and headers ──────────────────────────────────────────────────────

    @Test
    void bodyIsForwardedOnPost() {
        var capturedBody = new String[1];
        @SuppressWarnings("unchecked")
        var capturedHeaders = new Map[1];
        var tool = capturingTool(new String[1], new String[1], capturedHeaders, capturedBody, "[HTTP 200]\nok");
        tool.execute(Map.of("url", "https://example.com", "method", "POST", "body", "{\"key\":\"value\"}"));
        assertEquals("{\"key\":\"value\"}", capturedBody[0]);
    }

    @Test
    void headersAreParsedFromJsonString() {
        @SuppressWarnings("unchecked")
        var capturedHeaders = new Map[1];
        var tool = capturingTool(new String[1], new String[1], capturedHeaders, new String[1], "[HTTP 200]\nok");
        tool.execute(Map.of(
                "url", "https://example.com",
                "headers", "{\"Authorization\":\"Bearer token\",\"Accept\":\"application/json\"}"
        ));
        assertNotNull(capturedHeaders[0]);
        assertEquals("Bearer token", capturedHeaders[0].get("Authorization"));
        assertEquals("application/json", capturedHeaders[0].get("Accept"));
    }

    @Test
    void malformedHeadersJsonReturnsError() {
        String result = mockTool("[HTTP 200]\nok")
                .execute(Map.of("url", "https://example.com", "headers", "not-json"));
        assertTrue(result.startsWith("Error:"), "Malformed headers JSON should return an error");
        assertTrue(result.contains("headers"), "Error message should mention 'headers'");
    }

    @Test
    void headersAsArrayReturnsError() {
        String result = mockTool("[HTTP 200]\nok")
                .execute(Map.of("url", "https://example.com", "headers", "[\"not\",\"an\",\"object\"]"));
        assertTrue(result.startsWith("Error:"), "Non-object headers JSON should return an error");
    }

    // ── response handling ─────────────────────────────────────────────────────

    @Test
    void responseIsReturnedAsIs() {
        String result = mockTool("[HTTP 200]\n{\"status\":\"ok\"}").execute(Map.of("url", "https://example.com"));
        assertTrue(result.contains("[HTTP 200]"));
        assertTrue(result.contains("{\"status\":\"ok\"}"));
    }

    @Test
    void longResponseIsTruncated() {
        String longBody = "x".repeat(3000);
        String result = mockTool("[HTTP 200]\n" + longBody).execute(Map.of("url", "https://example.com"));
        assertTrue(result.contains("[... truncated"), "Response should mention truncation");
        assertTrue(result.length() <= 2100, "Truncated response should not be much longer than 2000 chars");
    }

    @Test
    void shortResponseIsNotTruncated() {
        String response = "[HTTP 200]\nhello world";
        String result = mockTool(response).execute(Map.of("url", "https://example.com"));
        assertEquals(response, result, "Short response should pass through unchanged");
    }

    @Test
    void emptyResponseBodyHandledGracefully() {
        String result = mockTool("").execute(Map.of("url", "https://example.com"));
        assertEquals("(empty response)", result);
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    void nonSuccessStatusIsLabelledAsError() {
        String result = mockTool("[HTTP 404 ERROR]\nNot Found").execute(Map.of("url", "https://example.com"));
        assertTrue(result.contains("404"), "Should include status code");
    }

    @Test
    void networkErrorIsHandledGracefully() {
        HttpTool tool = new HttpTool((url, method, headers, body) -> {
            throw new IOException("Connection refused");
        });
        String result = tool.execute(Map.of("url", "https://example.com"));
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("Connection refused"));
    }
}