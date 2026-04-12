package com.helloagents.llm;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Centralized configuration for LLM clients.
 *
 * <p>Construct via {@link #fromEnv()} or the fluent builder:
 * <pre>
 *   // from environment variables / .env file
 *   LlmConfig config = LlmConfig.fromEnv();
 *
 *   // programmatic
 *   LlmConfig config = LlmConfig.builder()
 *           .apiKey("sk-...")
 *           .model("gpt-4o")
 *           .temperature(0.7)
 *           .maxTokens(2048L)
 *           .build();
 * </pre>
 *
 * <p>Environment variables:
 * <table>
 *   <tr><th>Variable</th><th>Required</th><th>Default</th></tr>
 *   <tr><td>{@code LLM_API_KEY}</td>          <td>yes</td><td>—</td></tr>
 *   <tr><td>{@code LLM_BASE_URL}</td>         <td>no</td> <td>{@value #DEFAULT_BASE_URL}</td></tr>
 *   <tr><td>{@code LLM_MODEL}</td>            <td>no</td> <td>{@value #DEFAULT_MODEL}</td></tr>
 *   <tr><td>{@code LLM_TEMPERATURE}</td>      <td>no</td> <td>not set (API default)</td></tr>
 *   <tr><td>{@code LLM_MAX_TOKENS}</td>       <td>no</td> <td>not set (API default)</td></tr>
 *   <tr><td>{@code LLM_TOP_P}</td>            <td>no</td> <td>not set (API default)</td></tr>
 *   <tr><td>{@code LLM_FREQUENCY_PENALTY}</td><td>no</td> <td>not set (API default)</td></tr>
 *   <tr><td>{@code LLM_PRESENCE_PENALTY}</td> <td>no</td> <td>not set (API default)</td></tr>
 * </table>
 */
public final class LlmConfig {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_MODEL    = "gpt-4o";

    private final String  apiKey;
    private final String  baseUrl;
    private final String  model;
    private final Double  temperature;
    private final Long    maxTokens;
    private final Double  topP;
    private final Double  frequencyPenalty;
    private final Double  presencePenalty;

    private LlmConfig(Builder builder) {
        this.apiKey           = builder.apiKey;
        this.baseUrl          = builder.baseUrl;
        this.model            = builder.model;
        this.temperature      = builder.temperature;
        this.maxTokens        = builder.maxTokens;
        this.topP             = builder.topP;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty  = builder.presencePenalty;
    }

    /**
     * Loads configuration from environment variables or a {@code .env} file.
     *
     * @throws IllegalArgumentException if {@code LLM_API_KEY} is absent or blank
     */
    public static LlmConfig fromEnv() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        return builder()
                .apiKey(dotenv.get("LLM_API_KEY"))
                .baseUrl(dotenv.get("LLM_BASE_URL"))
                .model(dotenv.get("LLM_MODEL"))
                .temperature(parseDouble(dotenv.get("LLM_TEMPERATURE")))
                .maxTokens(parseLong(dotenv.get("LLM_MAX_TOKENS")))
                .topP(parseDouble(dotenv.get("LLM_TOP_P")))
                .frequencyPenalty(parseDouble(dotenv.get("LLM_FREQUENCY_PENALTY")))
                .presencePenalty(parseDouble(dotenv.get("LLM_PRESENCE_PENALTY")))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // --- getters (null = not configured, API uses its own default) ------------

    public String apiKey()           { return apiKey;           }
    public String baseUrl()          { return baseUrl;          }
    public String model()            { return model;            }
    public Double temperature()      { return temperature;      }
    public Long   maxTokens()        { return maxTokens;        }
    public Double topP()             { return topP;             }
    public Double frequencyPenalty() { return frequencyPenalty; }
    public Double presencePenalty()  { return presencePenalty;  }

    @Override
    public String toString() {
        return "LlmConfig{baseUrl='" + baseUrl + "', model='" + model
                + "', temperature=" + temperature
                + ", maxTokens=" + maxTokens
                + ", topP=" + topP
                + ", frequencyPenalty=" + frequencyPenalty
                + ", presencePenalty=" + presencePenalty + "}";
    }

    // --- private helpers ------------------------------------------------------

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Double.parseDouble(value.strip()); }
        catch (NumberFormatException e) { return null; }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.parseLong(value.strip()); }
        catch (NumberFormatException e) { return null; }
    }

    // -------------------------------------------------------------------------

    public static final class Builder {

        private String apiKey;
        private String baseUrl          = DEFAULT_BASE_URL;
        private String model            = DEFAULT_MODEL;
        private Double temperature      = null;
        private Long   maxTokens        = null;
        private Double topP             = null;
        private Double frequencyPenalty = null;
        private Double presencePenalty  = null;

        private Builder() {}

        /** Required. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Optional — falls back to {@value LlmConfig#DEFAULT_BASE_URL} if null or blank. */
        public Builder baseUrl(String baseUrl) {
            if (baseUrl != null && !baseUrl.isBlank()) this.baseUrl = baseUrl;
            return this;
        }

        /** Optional — falls back to {@value LlmConfig#DEFAULT_MODEL} if null or blank. */
        public Builder model(String model) {
            if (model != null && !model.isBlank()) this.model = model;
            return this;
        }

        /** Sampling temperature [0.0, 2.0]. Null means use the API default. */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /** Maximum tokens to generate. Null means use the API default. */
        public Builder maxTokens(Long maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /** Nucleus sampling top-p [0.0, 1.0]. Null means use the API default. */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /** Frequency penalty [-2.0, 2.0]. Null means use the API default. */
        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        /** Presence penalty [-2.0, 2.0]. Null means use the API default. */
        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        /** @throws IllegalArgumentException if apiKey is null or blank */
        public LlmConfig build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException(
                        "LLM_API_KEY is required but was not provided");
            }
            return new LlmConfig(this);
        }
    }
}
