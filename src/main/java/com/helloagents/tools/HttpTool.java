package com.helloagents.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * HTTP fetch tool — retrieves the full content of a specific URL.
 *
 * <p>Complements {@link WebSearchTool}: use {@code web_search} to discover URLs,
 * then {@code http_fetch} to read a specific page in full.
 *
 * <p>Response body is always truncated to {@value #MAX_RESPONSE_CHARS} characters
 * to avoid overflowing the LLM context window.
 */
public class HttpTool implements Tool {

    private static final int      MAX_RESPONSE_CHARS  = 8000;
    private static final int      MAX_ERROR_BODY_CHARS = 300;  // non-2xx body preview
    private static final Duration TIMEOUT             = Duration.ofSeconds(15);
    private static final Set<String> ALLOWED_METHODS  = Set.of("GET", "POST");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36 Edg/147.0.0.0";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Abstracts the actual HTTP call for testability.
     * Returns "[HTTP <status>]\n<body>" so tests can assert on the status line.
     */
    @FunctionalInterface
    interface Requester {
        String request(String url, String method, Map<String, String> headers, String body)
                throws IOException, InterruptedException;
    }

    private final Requester requester;

    /** Production constructor — uses a real {@link HttpClient}. */
    public HttpTool() {
        this(HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                // Follow HTTP→HTTPS and same-protocol redirects; block HTTPS→HTTP downgrades.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /** Package-private — allows injecting a custom HttpClient (e.g. for testing). */
    HttpTool(HttpClient httpClient) {
        this(buildRequester(httpClient));
    }

    /** Internal constructor used by both public constructors and tests. */
    HttpTool(Requester requester) {
        this.requester = requester;
    }

    private static Requester buildRequester(HttpClient httpClient) {
        return (url, method, headers, body) -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT);

            // Apply user headers first so they can intentionally override the default User-Agent.
            headers.forEach(builder::setHeader);
            // Fall back to browser UA only when user hasn't supplied one (case-insensitive check).
            boolean hasUserAgent = headers.keySet().stream()
                    .anyMatch(k -> k.equalsIgnoreCase("User-Agent"));
            if (!hasUserAgent) {
                builder.setHeader("User-Agent", USER_AGENT);
            }

            if ("POST".equals(method)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            } else {
                builder.GET();
            }

            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String responseBody = response.body();

            // For non-2xx, return a concise error instead of a full HTML error page.
            if (status < 200 || status >= 300) {
                String preview = truncate(responseBody, MAX_ERROR_BODY_CHARS);
                return "[HTTP " + status + " ERROR]\n" + preview;
            }
            return "[HTTP " + status + "]\n" + responseBody;
        };
    }

    // ── Tool interface ─────────────────────────────────────────────────────────

    @Override
    public String name() {
        return "http_fetch";
    }

    @Override
    public String description() {
        return """
                Fetch the full content of a specific URL and return the response body \
                (truncated to 2000 characters). \
                Best suited for JSON APIs and plain-text pages; HTML pages may contain noisy markup. \
                Use this tool when: \
                (1) you found a URL via web_search and the snippet is too short to answer the question; \
                (2) the user explicitly provides a URL to read or analyze; \
                (3) you need to call a public REST API and read its JSON response. \
                Do NOT use for general web searches — use web_search instead. \
                Do NOT guess URLs — only fetch URLs from search results or user input.""";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
                ToolParameter.Param.required("url",
                        "Full URL to fetch, must start with https:// or http://", "string"),
                ToolParameter.Param.optional("method",
                        "HTTP method: GET (default) or POST", "string"),
                ToolParameter.Param.optional("body",
                        "Request body for POST requests (JSON string or plain text)", "string"),
                ToolParameter.Param.optional("headers",
                        "Extra HTTP headers as a JSON object string, e.g. {\"Accept\": \"application/json\"}",
                        "string")
        );
    }

    @Override
    public String execute(Map<String, String> params) {
        String url = params.getOrDefault("url", "").strip();
        if (url.isBlank()) {
            return "Error: 'url' is required.";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Error: URL must start with http:// or https://, got: " + url;
        }

        String method = params.getOrDefault("method", "GET").strip().toUpperCase();
        if (!ALLOWED_METHODS.contains(method)) {
            return "Error: method must be GET or POST, got: " + method;
        }

        String body = params.get("body");

        String headersRaw = params.get("headers");
        Map<String, String> headers;
        try {
            headers = parseHeaders(headersRaw);
        } catch (IllegalArgumentException e) {
            return "Error: invalid headers JSON — " + e.getMessage()
                    + ". Provide headers as a JSON object, e.g. {\"Authorization\":\"Bearer token\"}";
        }

        try {
            String response = requester.request(url, method, headers, body);
            return truncate(response, MAX_RESPONSE_CHARS);
        } catch (IllegalArgumentException e) {
            return "Error: invalid URL — " + e.getMessage();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: request failed — " + e.getMessage();
        }
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private static Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) return Map.of();
        try {
            JsonNode node = MAPPER.readTree(headersJson);
            if (!node.isObject()) {
                throw new IllegalArgumentException("headers must be a JSON object, got: " + node.getNodeType());
            }
            Map<String, String> result = new HashMap<>();
            node.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asText()));
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.isBlank()) return "(empty response)";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n[... truncated at " + maxChars + " characters]";
    }
}