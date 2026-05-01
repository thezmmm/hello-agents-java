package com.helloagents.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {

    // ── fixtures ───────────────────────────────────────────────────────────────

    private static final String MOCK_RESPONSE = """
            {
              "query": "Java agents",
              "answer": "Java agents are software components that reason and act autonomously.",
              "results": [
                {
                  "title": "Building AI Agents in Java",
                  "url": "https://example.com/java-agents",
                  "content": "A comprehensive guide to building AI-native agents using Java 17.",
                  "score": 0.95
                },
                {
                  "title": "ReAct Pattern Explained",
                  "url": "https://example.com/react-pattern",
                  "content": "How the Reason + Act loop works in practice.",
                  "score": 0.82
                },
                {
                  "title": "Low Quality Result",
                  "url": "https://example.com/low",
                  "content": "Not very relevant.",
                  "score": 0.1
                }
              ]
            }
            """;

    private static final String NO_ANSWER_RESPONSE = """
            {
              "query": "Java agents",
              "results": [
                {
                  "title": "Only Title",
                  "url": "https://example.com/only",
                  "content": "Some content.",
                  "score": 0.9
                }
              ]
            }
            """;

    private static final String EMPTY_RESPONSE = """
            {"query": "nothing", "results": []}
            """;

    private static final String ERROR_RESPONSE = """
            {"detail": "Invalid API key"}
            """;

    // ── helpers ───────────────────────────────────────────────────────────────

    private WebSearchTool mockTool(String fixedResponse) {
        return new WebSearchTool(body -> fixedResponse);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void showsTavilyAnswerAtTopWhenPresent() {
        String result = mockTool(MOCK_RESPONSE).execute(Map.of("query", "Java agents"));
        assertTrue(result.startsWith("Answer:"), "Should lead with Tavily's answer");
        assertTrue(result.contains("Java agents are software"), "Should include answer text");
    }

    @Test
    void parsesResultsIntoNumberedList() {
        String result = mockTool(MOCK_RESPONSE).execute(Map.of("query", "Java agents"));
        assertTrue(result.contains("Sources:"), "Should have Sources section");
        assertTrue(result.contains("1. [Building AI Agents in Java]"), "Missing first result title");
        assertTrue(result.contains("https://example.com/java-agents"), "Missing first result URL");
        assertTrue(result.contains("A comprehensive guide"), "Missing first result content");
        assertTrue(result.contains("2. [ReAct Pattern Explained]"), "Missing second result title");
    }

    @Test
    void filtersOutLowScoreResults() {
        String result = mockTool(MOCK_RESPONSE).execute(Map.of("query", "Java agents"));
        assertFalse(result.contains("Low Quality Result"),
                "Result with score < 0.3 should be filtered out");
    }

    @Test
    void worksWithoutAnswerField() {
        String result = mockTool(NO_ANSWER_RESPONSE).execute(Map.of("query", "Java agents"));
        assertFalse(result.startsWith("Answer:"), "Should not show Answer section when absent");
        assertTrue(result.contains("1. [Only Title]"), "Should still show source results");
    }

    @Test
    void returnsNoResultsMessageForEmptyArray() {
        String result = mockTool(EMPTY_RESPONSE).execute(Map.of("query", "nothing"));
        assertEquals("No results found.", result);
    }

    @Test
    void returnsApiErrorFromDetailField() {
        String result = mockTool(ERROR_RESPONSE).execute(Map.of("query", "anything"));
        assertTrue(result.startsWith("Error:"), "Expected error prefix");
        assertTrue(result.contains("Invalid API key"), "Expected API key error message");
    }

    @Test
    void requiresQueryParam() {
        String result = mockTool(MOCK_RESPONSE).execute(Map.of());
        assertTrue(result.startsWith("Error:"), "Expected error when query is missing");
    }

    @Test
    void blankQueryIsRejected() {
        String result = mockTool(MOCK_RESPONSE).execute(Map.of("query", "   "));
        assertTrue(result.startsWith("Error:"), "Expected error for blank query");
    }

    @Test
    void includeAnswerIsAlwaysSentInRequestBody() {
        var capturedBody = new String[1];
        new WebSearchTool(body -> { capturedBody[0] = body; return MOCK_RESPONSE; })
                .execute(Map.of("query", "test"));
        assertTrue(capturedBody[0].contains("\"include_answer\":true"),
                "include_answer must always be sent, got: " + capturedBody[0]);
    }

    @Test
    void maxResultsParamIsForwardedInRequestBody() {
        var capturedBody = new String[1];
        new WebSearchTool(body -> { capturedBody[0] = body; return MOCK_RESPONSE; })
                .execute(Map.of("query", "test", "max_results", "2"));
        assertTrue(capturedBody[0].contains("\"max_results\":2"),
                "Request body should contain max_results=2, got: " + capturedBody[0]);
    }

    @Test
    void defaultMaxResultsIsUsedWhenNotSpecified() {
        var capturedBody = new String[1];
        new WebSearchTool(body -> { capturedBody[0] = body; return MOCK_RESPONSE; })
                .execute(Map.of("query", "test"));
        assertTrue(capturedBody[0].contains("\"max_results\":5"),
                "Default max_results should be 5, got: " + capturedBody[0]);
    }

    @Test
    void timeRangeParamIsForwardedWhenProvided() {
        var capturedBody = new String[1];
        new WebSearchTool(body -> { capturedBody[0] = body; return MOCK_RESPONSE; })
                .execute(Map.of("query", "test", "time_range", "week"));
        assertTrue(capturedBody[0].contains("\"time_range\":\"week\""),
                "time_range should be forwarded, got: " + capturedBody[0]);
    }

    @Test
    void timeRangeNotSentWhenAbsent() {
        var capturedBody = new String[1];
        new WebSearchTool(body -> { capturedBody[0] = body; return MOCK_RESPONSE; })
                .execute(Map.of("query", "test"));
        assertFalse(capturedBody[0].contains("time_range"),
                "time_range should not appear when not specified, got: " + capturedBody[0]);
    }

    @Test
    void invalidTopicFallsBackToGeneral() {
        var capturedBody = new String[1];
        new WebSearchTool(body -> { capturedBody[0] = body; return MOCK_RESPONSE; })
                .execute(Map.of("query", "test", "topic", "garbage"));
        assertTrue(capturedBody[0].contains("\"topic\":\"general\""),
                "Invalid topic should fall back to 'general', got: " + capturedBody[0]);
    }

    @Test
    void newsTopicIsForwardedCorrectly() {
        var capturedBody = new String[1];
        new WebSearchTool(body -> { capturedBody[0] = body; return MOCK_RESPONSE; })
                .execute(Map.of("query", "AI news", "topic", "news"));
        assertTrue(capturedBody[0].contains("\"topic\":\"news\""),
                "topic=news should be forwarded, got: " + capturedBody[0]);
    }

    @Test
    void maxResultsIsClamped() {
        var capturedBody = new String[1];
        new WebSearchTool(body -> { capturedBody[0] = body; return MOCK_RESPONSE; })
                .execute(Map.of("query", "test", "max_results", "99"));
        assertTrue(capturedBody[0].contains("\"max_results\":20"),
                "max_results should be clamped to 20, got: " + capturedBody[0]);
    }

    @Test
    void toolMetadata() {
        WebSearchTool tool = mockTool(MOCK_RESPONSE);
        assertEquals("web_search", tool.name());
        assertFalse(tool.description().isBlank());
        assertFalse(tool.parameters().isEmpty());
    }

    @Test
    void handlesMalformedJson() {
        String result = mockTool("not valid json {{{").execute(Map.of("query", "test"));
        assertTrue(result.startsWith("Error:"), "Expected error on malformed JSON");
    }

    @Test
    void httpExceptionIsHandledGracefully() {
        WebSearchTool tool = new WebSearchTool(body -> {
            throw new java.io.IOException("Connection refused");
        });
        String result = tool.execute(Map.of("query", "test"));
        assertTrue(result.startsWith("Error:"), "Expected error on network failure");
        assertTrue(result.contains("Connection refused"), "Expected original error message");
    }
}