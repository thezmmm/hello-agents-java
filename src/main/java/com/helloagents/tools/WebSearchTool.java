package com.helloagents.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;

/**
 * Web search tool powered by the Tavily Search API.
 *
 * <p>Always requests {@code include_answer=true} so Tavily's own LLM-generated
 * summary is included at the top of the output — agents can use it directly
 * without reading every snippet.
 *
 * <p>Environment variable: {@code TAVILY_API_KEY}
 *
 * <p>Usage:
 * <pre>
 *   WebSearchTool tool = WebSearchTool.fromEnv();
 *   String result = tool.execute(Map.of(
 *       "query", "latest AI news",
 *       "topic", "news",
 *       "time_range", "week"
 *   ));
 * </pre>
 */
public class WebSearchTool implements Tool {

    static final String TAVILY_ENDPOINT = "https://api.tavily.com/search";

    private static final int    DEFAULT_MAX_RESULTS = 5;
    private static final double MIN_SCORE           = 0.3;   // skip low-relevance results
    private static final int    MAX_CONTENT_CHARS   = 300;   // per result snippet
    private static final int    MAX_TOTAL_CHARS     = 2000;  // total output guard

    private static final Set<String> VALID_TOPICS      = Set.of("general", "news", "finance");
    private static final Set<String> VALID_TIME_RANGES = Set.of("day", "week", "month", "year");
    private static final Set<String> VALID_DEPTHS      = Set.of("fast", "basic", "advanced");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Abstracts the HTTP call so tests can inject a mock without needing Mockito.
     * Takes a JSON request body string and returns the JSON response body string.
     */
    @FunctionalInterface
    interface HttpFetcher {
        String fetch(String requestBody) throws IOException, InterruptedException;
    }

    private final HttpFetcher fetcher;

    /** Public constructor — uses the real Tavily API. */
    public WebSearchTool(String apiKey) {
        this(apiKey, HttpClient.newHttpClient());
    }

    /** Package-private — allows injecting a custom HttpClient (e.g. for testing). */
    WebSearchTool(String apiKey, HttpClient httpClient) {
        this(body -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TAVILY_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        });
    }

    /** Internal constructor — used by the public constructors and tests. */
    WebSearchTool(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * Creates a {@code WebSearchTool} by reading {@code TAVILY_API_KEY} from the
     * environment or a local {@code .env} file.
     *
     * @throws IllegalArgumentException if the key is absent or blank
     */
    public static WebSearchTool fromEnv() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String key = dotenv.get("TAVILY_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "TAVILY_API_KEY is required but was not set. " +
                    "Add it to your .env file or environment.");
        }
        return new WebSearchTool(key);
    }

    // ── Tool interface ─────────────────────────────────────────────────────────

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "Search the web for current information. Returns a direct answer plus source snippets.";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
                ToolParameter.Param.required("query",
                        "Search keywords or question", "string"),
                ToolParameter.Param.optional("max_results",
                        "Number of source results (1-20, default 5)", "number"),
                ToolParameter.Param.optional("topic",
                        "Search topic: 'general' (default), 'news', or 'finance'", "string"),
                ToolParameter.Param.optional("time_range",
                        "Temporal filter: 'day', 'week', 'month', or 'year'", "string"),
                ToolParameter.Param.optional("search_depth",
                        "Quality vs speed: 'fast', 'basic' (default), or 'advanced'", "string")
        );
    }

    @Override
    public String execute(Map<String, String> params) {
        String query = params.getOrDefault("query", "").strip();
        if (query.isBlank()) {
            return "Error: 'query' is required.";
        }

        int    maxResults   = parseIntParam(params.get("max_results"), DEFAULT_MAX_RESULTS, 1, 20);
        String topic        = validated(params.get("topic"),       VALID_TOPICS,      "general");
        String timeRange    = validated(params.get("time_range"),  VALID_TIME_RANGES, null);
        String searchDepth  = validated(params.get("search_depth"), VALID_DEPTHS,     "basic");

        String requestBody = buildRequestBody(query, maxResults, topic, timeRange, searchDepth);
        try {
            String responseBody = fetcher.fetch(requestBody);
            return parseResponse(responseBody);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: search request failed — " + e.getMessage();
        }
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private String buildRequestBody(String query, int maxResults,
                                    String topic, String timeRange, String searchDepth) {
        try {
            var node = MAPPER.createObjectNode();
            node.put("query", query);
            node.put("max_results", maxResults);
            node.put("search_depth", searchDepth);
            node.put("topic", topic);
            // Always request Tavily's own answer — far cleaner than raw snippets for agents.
            node.put("include_answer", true);
            if (timeRange != null) {
                node.put("time_range", timeRange);
            }
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"query\":" + MAPPER.createObjectNode().textNode(query)
                    + ",\"max_results\":" + maxResults
                    + ",\"search_depth\":\"" + searchDepth + "\""
                    + ",\"topic\":\"" + topic + "\""
                    + ",\"include_answer\":true}";
        }
    }

    private String parseResponse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);

            if (root.has("detail")) {
                return "Error: " + root.get("detail").asText();
            }

            var sb = new StringBuilder();

            // Tavily's pre-generated answer — show first when present.
            String answer = textOf(root, "answer");
            if (!answer.isBlank()) {
                sb.append("Answer: ").append(answer).append("\n\n");
            }

            JsonNode results = root.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return sb.length() > 0 ? sb.toString().stripTrailing() : "No results found.";
            }

            sb.append("Sources:\n");
            int i = 1;
            for (JsonNode result : results) {
                // Skip low-relevance results to reduce noise.
                double score = result.has("score") ? result.get("score").asDouble(0) : 1.0;
                if (score < MIN_SCORE) continue;

                String title   = textOf(result, "title");
                String url     = textOf(result, "url");
                String content = truncate(textOf(result, "content"), MAX_CONTENT_CHARS);

                sb.append(i++).append(". [").append(title).append("] ").append(url).append('\n');
                if (!content.isBlank()) {
                    sb.append("   ").append(content).append('\n');
                }

                if (sb.length() >= MAX_TOTAL_CHARS) break;
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            return "Error: could not parse search response — " + e.getMessage();
        }
    }

    // ── static utilities ───────────────────────────────────────────────────────

    private static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "…";
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null ? n.asText("") : "";
    }

    private static int parseIntParam(String value, int defaultVal, int min, int max) {
        if (value == null || value.isBlank()) return defaultVal;
        try {
            int n = Integer.parseInt(value.strip());
            return Math.max(min, Math.min(max, n));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /** Returns the value if it's in the allowed set, otherwise the fallback. */
    private static String validated(String value, Set<String> allowed, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String v = value.strip().toLowerCase();
        return allowed.contains(v) ? v : fallback;
    }
}