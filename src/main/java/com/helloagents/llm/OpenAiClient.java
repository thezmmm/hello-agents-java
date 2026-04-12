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

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * LLM client backed by the official OpenAI Java SDK.
 * Works with OpenAI and any OpenAI-compatible endpoint.
 *
 * <p>Preferred construction:
 * <pre>
 *   OpenAiClient client = new OpenAiClient(LlmConfig.fromEnv());
 *
 *   OpenAiClient client = new OpenAiClient(
 *           LlmConfig.builder().apiKey("sk-...").model("gpt-4o").build());
 * </pre>
 */
public class OpenAiClient implements LlmClient {

    private final OpenAIClient client;
    private final String model;

    public OpenAiClient(LlmConfig config) {
        this.model = config.model();
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .build();
    }

    /** Convenience constructor — delegates to {@link LlmConfig}. */
    public OpenAiClient(String apiKey, String baseUrl, String model) {
        this(LlmConfig.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .model(model)
                .build());
    }

    /** Creates a client from environment variables or a {@code .env} file. */
    public static OpenAiClient fromEnv() {
        return new OpenAiClient(LlmConfig.fromEnv());
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