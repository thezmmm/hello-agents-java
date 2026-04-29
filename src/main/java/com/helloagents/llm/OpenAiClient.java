package com.helloagents.llm;

import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * LLM client backed by the official OpenAI Java SDK.
 * Works with OpenAI and any OpenAI-compatible endpoint.
 */
public class OpenAiClient implements LlmClient {

    private final OpenAIClient client;
    private final LlmConfig    config;

    public OpenAiClient(LlmConfig config) {
        this.config = config;
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .build();
    }

    public OpenAiClient(String apiKey, String baseUrl, String model) {
        this(LlmConfig.builder().apiKey(apiKey).baseUrl(baseUrl).model(model).build());
    }

    public static OpenAiClient fromEnv() {
        return new OpenAiClient(LlmConfig.fromEnv());
    }

    // --- chat / stream (no tools) --------------------------------------------

    @Override
    public String chat(List<Message> messages) {
        ChatCompletion completion = client.chat().completions().create(buildParams(messages));
        return completion.choices().get(0).message().content().orElse("");
    }

    @Override
    public void stream(List<Message> messages, Consumer<String> onToken) {
        try (var response = client.chat().completions().createStreaming(buildParams(messages))) {
            response.stream()
                    .flatMap(chunk -> chunk.choices().stream())
                    .map(ChatCompletionChunk.Choice::delta)
                    .map(delta -> delta.content().orElse(""))
                    .filter(s -> !s.isEmpty())
                    .forEach(onToken);
        }
    }

    // --- chat / stream (with tools) ------------------------------------------

    @Override
    public LlmResponse chat(List<Message> messages, List<Tool> tools) {
        ChatCompletion completion = client.chat().completions().create(buildParams(messages, tools));
        return toLlmResponse(completion);
    }

    @Override
    public LlmResponse stream(List<Message> messages, List<Tool> tools, Consumer<String> onToken) {
        Map<Integer, ToolCallAcc> accMap = new LinkedHashMap<>();
        StringBuilder contentBuf = new StringBuilder();

        try (var response = client.chat().completions().createStreaming(buildParams(messages, tools))) {
            response.stream()
                    .flatMap(chunk -> chunk.choices().stream())
                    .forEach(choice -> {
                        var delta = choice.delta();

                        delta.content().filter(s -> !s.isEmpty()).ifPresent(s -> {
                            contentBuf.append(s);
                            onToken.accept(s);
                        });

                        delta.toolCalls().ifPresent(tcs -> tcs.forEach(tc -> {
                            int idx = (int) tc.index();
                            ToolCallAcc acc = accMap.computeIfAbsent(idx, i -> new ToolCallAcc());
                            tc.id().ifPresent(id -> acc.id = id);
                            tc.function().ifPresent(fn -> {
                                fn.name().ifPresent(name -> acc.name = name);
                                fn.arguments().ifPresent(args -> acc.arguments += args);
                            });
                        }));
                    });
        }

        if (!accMap.isEmpty()) {
            List<FunctionCall> calls = accMap.values().stream()
                    .map(a -> new FunctionCall(a.id, a.name, a.arguments))
                    .toList();
            return LlmResponse.ofToolCalls(calls);
        }
        return LlmResponse.ofContent(contentBuf.toString());
    }

    // --- param building ------------------------------------------------------

    private ChatCompletionCreateParams buildParams(List<Message> messages) {
        var builder = ChatCompletionCreateParams.builder()
                .model(config.model())
                .messages(toSdkMessages(messages));
        applyConfig(builder);
        return builder.build();
    }

    private ChatCompletionCreateParams buildParams(List<Message> messages, List<Tool> tools) {
        var builder = ChatCompletionCreateParams.builder()
                .model(config.model())
                .messages(toSdkMessages(messages))
                .tools(toSdkTools(tools));
        applyConfig(builder);
        return builder.build();
    }

    private void applyConfig(ChatCompletionCreateParams.Builder b) {
        if (config.temperature()      != null) b.temperature(config.temperature());
        if (config.maxTokens()        != null) b.maxCompletionTokens(config.maxTokens());
        if (config.topP()             != null) b.topP(config.topP());
        if (config.frequencyPenalty() != null) b.frequencyPenalty(config.frequencyPenalty());
        if (config.presencePenalty()  != null) b.presencePenalty(config.presencePenalty());
    }

    private List<ChatCompletionMessageParam> toSdkMessages(List<Message> messages) {
        return messages.stream()
                .map(m -> switch (m.role()) {
                    case "system" -> ChatCompletionMessageParam.ofSystem(
                            ChatCompletionSystemMessageParam.builder()
                                    .content(m.content())
                                    .build());

                    case "assistant" -> {
                        var b = ChatCompletionAssistantMessageParam.builder();
                        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                            // assistant turn that requested tool calls — replay tool_calls in history
                            b.toolCalls(m.toolCalls().stream()
                                    .map(tc -> ChatCompletionMessageToolCall.builder()
                                            .id(tc.id())
                                            .function(ChatCompletionMessageToolCall.Function.builder()
                                                    .name(tc.name())
                                                    .arguments(tc.arguments())
                                                    .build())
                                            .build())
                                    .toList());
                        } else {
                            b.content(m.content() != null ? m.content() : "");
                        }
                        yield ChatCompletionMessageParam.ofAssistant(b.build());
                    }

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

    private List<ChatCompletionTool> toSdkTools(List<Tool> tools) {
        return tools.stream().map(t -> {
            var properties = new LinkedHashMap<String, Object>();
            var required   = new ArrayList<String>();
            for (ToolParameter.Param p : t.parameters().list()) {
                var prop = new LinkedHashMap<String, Object>();
                prop.put("type", p.type());
                prop.put("description", p.description());
                properties.put(p.name(), prop);
                if (p.required()) required.add(p.name());
            }
            var schema = new LinkedHashMap<String, Object>();
            schema.put("type", "object");
            schema.put("properties", properties);
            if (!required.isEmpty()) schema.put("required", required);

            return ChatCompletionTool.builder()
                    .function(FunctionDefinition.builder()
                            .name(t.name())
                            .description(t.description())
                            .parameters(JsonValue.from(schema))
                            .build())
                    .build();
        }).toList();
    }

    // --- response parsing ----------------------------------------------------

    private LlmResponse toLlmResponse(ChatCompletion completion) {
        var msg = completion.choices().get(0).message();
        var toolCallsOpt = msg.toolCalls();
        if (toolCallsOpt.isPresent() && !toolCallsOpt.get().isEmpty()) {
            List<FunctionCall> calls = toolCallsOpt.get().stream()
                    .map(tc -> new FunctionCall(
                            tc.id(),
                            tc.function().name(),
                            tc.function().arguments()))
                    .toList();
            return LlmResponse.ofToolCalls(calls);
        }
        return LlmResponse.ofContent(msg.content().orElse(""));
    }

    // -------------------------------------------------------------------------

    private static final class ToolCallAcc {
        String id        = "";
        String name      = "";
        String arguments = "";
    }

    public String getModel() {
        return config.model();
    }
}
