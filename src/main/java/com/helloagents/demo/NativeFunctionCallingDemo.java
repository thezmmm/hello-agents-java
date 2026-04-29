package com.helloagents.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helloagents.llm.LlmConfig;
import com.helloagents.tools.CalculatorTool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * 验证 OpenAI native function calling 的原始 HTTP 交互。
 *
 * <p>目的：观察 API 响应里的 tool_calls 字段结构，
 * 确认工具调用信息是结构化 JSON 字段，而不是混在 content 文本里。
 *
 * <p>运行：
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.NativeFunctionCallingDemo"
 * </pre>
 */
public class NativeFunctionCallingDemo {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient   HTTP   = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        LlmConfig config = LlmConfig.fromEnv();
        String endpoint = config.baseUrl().stripTrailing() + "/chat/completions";

        String question = "请计算 (123 + 456) * 7 的值";

        // ── 第一次请求：携带 tools 定义 ────────────────────────────────────
        String firstBody = """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "user", "content": "%s"}
                  ],
                  "tools": [
                    {
                      "type": "function",
                      "function": {
                        "name": "calculator",
                        "description": "计算数学表达式，支持 +、-、*、/、^ 和括号",
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "expression": {
                              "type": "string",
                              "description": "要计算的数学表达式，如 (2+3)*4"
                            }
                          },
                          "required": ["expression"]
                        }
                      }
                    }
                  ]
                }
                """.formatted(config.model(), question);

        section("第一次请求（携带 tools 定义）");
        System.out.println("请求 body:\n" + firstBody);

        JsonNode resp1 = post(endpoint, config.apiKey(), firstBody);

        System.out.println("响应 JSON（完整）:");
        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(resp1));

        // ── 解析 tool_calls ────────────────────────────────────────────────
        JsonNode choice      = resp1.path("choices").get(0);
        String finishReason  = choice.path("finish_reason").asText();
        JsonNode assistantMsg = choice.path("message");

        System.out.println("\nfinish_reason = \"" + finishReason + "\"");
        System.out.println("content       = " + assistantMsg.path("content"));

        JsonNode toolCalls = assistantMsg.path("tool_calls");
        if (toolCalls.isMissingNode() || toolCalls.isEmpty()) {
            System.out.println("\n没有 tool_calls，模型直接回答了：" + assistantMsg.path("content").asText());
            return;
        }

        JsonNode call    = toolCalls.get(0);
        String callId    = call.path("id").asText();
        String funcName  = call.path("function").path("name").asText();
        String argsJson  = call.path("function").path("arguments").asText();

        System.out.println("\n>>> tool_calls 字段解析结果:");
        System.out.println("    id              = " + callId);
        System.out.println("    function.name   = " + funcName);
        System.out.println("    function.arguments = " + argsJson);

        // ── 本地执行工具 ───────────────────────────────────────────────────
        JsonNode callArgs   = MAPPER.readTree(argsJson);
        String expression   = callArgs.path("expression").asText();
        String toolResult   = new CalculatorTool().execute(Map.of("expression", expression));

        System.out.println("\n本地执行: " + expression + " = " + toolResult);

        // ── 第二次请求：把工具结果作为 tool 角色消息发回 ──────────────────
        String assistantMsgJson = MAPPER.writeValueAsString(assistantMsg);
        String secondBody = """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "user", "content": "%s"},
                    %s,
                    {"role": "tool", "tool_call_id": "%s", "content": "%s"}
                  ],
                  "tools": [
                    {
                      "type": "function",
                      "function": {
                        "name": "calculator",
                        "description": "计算数学表达式",
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "expression": {"type": "string"}
                          },
                          "required": ["expression"]
                        }
                      }
                    }
                  ]
                }
                """.formatted(config.model(), question, assistantMsgJson, callId, toolResult);

        section("第二次请求（带工具执行结果）");
        System.out.println("请求 body:\n" + secondBody);

        JsonNode resp2 = post(endpoint, config.apiKey(), secondBody);

        System.out.println("响应 JSON（完整）:");
        System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(resp2));

        String finalAnswer = resp2.path("choices").get(0)
                .path("message").path("content").asText();

        section("模型最终回答");
        System.out.println(finalAnswer);
    }

    private static JsonNode post(String url, String apiKey, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        String responseBody = HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
        return MAPPER.readTree(responseBody);
    }

    private static void section(String title) {
        System.out.println("\n" + "═".repeat(55));
        System.out.println("  " + title);
        System.out.println("═".repeat(55) + "\n");
    }
}