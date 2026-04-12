package com.helloagents.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * LLM client backed by the official OpenAI Java SDK.
 * Works with OpenAI and any OpenAI-compatible endpoint.
 *
 * <p>Configuration via environment variables (or .env file):
 * <ul>
 *   <li>{@code LLM_API_KEY} — required</li>
 *   <li>{@code LLM_BASE_URL} — optional, defaults to https://api.openai.com/v1</li>
 *   <li>{@code LLM_MODEL} — optional, defaults to gpt-4o</li>
 * </ul>
 */
public class OpenAiClient implements LlmClient {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4o";

    private final OpenAIClient client;
    private final String model;

    public OpenAiClient(String apiKey, String baseUrl, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key must not be empty");
        }
        String resolvedBaseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_BASE_URL;
        this.model = (model != null && !model.isBlank()) ? model : DEFAULT_MODEL;

        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(resolvedBaseUrl)
                .build();
    }

    /** Create from .env file or system environment variables. */
    public static OpenAiClient fromEnv() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        return new OpenAiClient(
                dotenv.get("LLM_API_KEY"),
                dotenv.get("LLM_BASE_URL"),
                dotenv.get("LLM_MODEL")
        );
    }

    @Override
    public String chat(List<Message> messages) {
        ChatCompletion completion = client.chat().completions().create(
                ChatCompletionCreateParams.builder()
                        .model(model)
                        .messages(toSdkParams(messages))
                        .build());

        return completion.choices().get(0).message().content().orElse("");
    }

    @Override
    public void stream(List<Message> messages, Consumer<String> onToken) {
        try (var response = client.chat().completions().createStreaming(
                ChatCompletionCreateParams.builder()
                        .model(model)
                        .messages(toSdkParams(messages))
                        .build())) {

            response.stream()
                    .flatMap(chunk -> chunk.choices().stream())
                    .map(ChatCompletionChunk.Choice::delta)
                    .map(delta -> delta.content().orElse(""))
                    .filter(token -> !token.isEmpty())
                    .forEach(onToken);
        }
    }

    private List<ChatCompletionMessageParam> toSdkParams(List<Message> messages) {
        return messages.stream()
                .map(m -> switch (m.role()) {
                    case "system" -> ChatCompletionMessageParam.ofSystem(
                            ChatCompletionSystemMessageParam.builder()
                                    .content(m.content())
                                    .build());
                    case "assistant" -> ChatCompletionMessageParam.ofAssistant(
                            ChatCompletionAssistantMessageParam.builder()
                                    .content(m.content())
                                    .build());
                    case "tool" -> ChatCompletionMessageParam.ofTool(
                            ChatCompletionToolMessageParam.builder()
                                    .toolCallId(m.toolCallId())
                                    .content(m.content())
                                    .build());
                    default -> ChatCompletionMessageParam.ofUser(
                            ChatCompletionUserMessageParam.builder()
                                    .content(m.content())
                                    .build());
                })
                .collect(Collectors.toList());
    }

    public String getModel() {
        return model;
    }
}