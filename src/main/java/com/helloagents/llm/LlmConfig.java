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
 *           .baseUrl("https://api.openai.com/v1")
 *           .model("gpt-4o")
 *           .build();
 * </pre>
 *
 * <p>Environment variables:
 * <ul>
 *   <li>{@code LLM_API_KEY}  — required</li>
 *   <li>{@code LLM_BASE_URL} — optional, defaults to {@value #DEFAULT_BASE_URL}</li>
 *   <li>{@code LLM_MODEL}    — optional, defaults to {@value #DEFAULT_MODEL}</li>
 * </ul>
 */
public final class LlmConfig {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_MODEL    = "gpt-4o";

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    private LlmConfig(Builder builder) {
        this.apiKey  = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        this.model   = builder.model;
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
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String apiKey()  { return apiKey;  }
    public String baseUrl() { return baseUrl; }
    public String model()   { return model;   }

    @Override
    public String toString() {
        return "LlmConfig{baseUrl='" + baseUrl + "', model='" + model + "'}";
    }

    // -------------------------------------------------------------------------

    public static final class Builder {

        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private String model   = DEFAULT_MODEL;

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
