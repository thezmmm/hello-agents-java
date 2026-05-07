package com.helloagents.demo.travel.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helloagents.demo.travel.model.SseEvent;
import com.helloagents.demo.travel.model.TravelPlan;
import com.helloagents.demo.travel.model.TravelRequest;
import com.helloagents.llm.LlmClient;
import com.helloagents.mcp.McpClientConnector;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 多 Agent 旅行规划编排器。
 *
 * <p>通过高德地图 MCP Server（{@code @amap/amap-maps-mcp-server}）获取地图工具，
 * 并驱动四个专注型 Agent：
 * <ol>
 *   <li>{@link AttractionSearchAgent} — 使用 maps_text_search 搜索景点</li>
 *   <li>{@link WeatherQueryAgent}     — 使用 maps_weather 查询天气</li>
 *   <li>{@link HotelAgent}            — 使用 maps_text_search 搜索酒店</li>
 *   <li>{@link PlannerAgent}          — 整合以上信息，生成完整行程 JSON（无工具）</li>
 * </ol>
 *
 * <p>前三个 Agent 各自独立持有一个 MCP 连接，并行运行；PlannerAgent 仅依赖 LLM。
 */
public class TravelOrchestrator {

    private static final String      AMAP_MCP_PACKAGE = "@amap/amap-maps-mcp-server";
    private static final ObjectMapper MAPPER           = new ObjectMapper();

    private final LlmClient llm;
    private final String    amapKey;

    public TravelOrchestrator(LlmClient llm, String amapKey) {
        this.llm     = llm;
        this.amapKey = amapKey;
    }

    /**
     * 编排四个 Agent 完成旅行规划，将进度以 {@link SseEvent} 形式推送给调用方。
     * 前三个 Agent 并行执行，PlannerAgent 等待它们全部完成后再运行。
     */
    public void plan(TravelRequest request, Consumer<SseEvent> eventSink) {
        long days  = calculateDays(request.startDate(), request.endDate());
        int  count = (int) days * 3;

        eventSink.accept(new SseEvent("thinking", "三位专家 Agent 正在并行工作，请稍候...", null));

        // ── 前三个 Agent 并行运行，各自独立持有 MCP 连接 ──────────────────────
        ExecutorService subTasks = Executors.newFixedThreadPool(3);
        String attractionInfo, weatherInfo, hotelInfo;
        try {
            CompletableFuture<String> attrF = CompletableFuture.supplyAsync(() -> {
                try (McpClientConnector mcp = openAmapMcp()) {
                    eventSink.accept(new SseEvent("thinking", "景点搜索专家正在根据您的偏好搜索景点...", null));
                    String result = new AttractionSearchAgent(llm, mcp.listTools())
                            .search(request.destination(), request.preferences(), count);
                    eventSink.accept(new SseEvent("tool_result", "[景点搜索专家] 已完成景点搜索",
                            preview(result, 300)));
                    return result;
                }
            }, subTasks);

            CompletableFuture<String> weatherF = CompletableFuture.supplyAsync(() -> {
                try (McpClientConnector mcp = openAmapMcp()) {
                    eventSink.accept(new SseEvent("thinking", "天气查询专家正在获取旅行期间天气预报...", null));
                    String result = new WeatherQueryAgent(llm, mcp.listTools())
                            .query(request.destination(), request.startDate(), request.endDate());
                    eventSink.accept(new SseEvent("tool_result", "[天气查询专家] 已完成天气查询",
                            preview(result, 300)));
                    return result;
                }
            }, subTasks);

            CompletableFuture<String> hotelF = CompletableFuture.supplyAsync(() -> {
                try (McpClientConnector mcp = openAmapMcp()) {
                    eventSink.accept(new SseEvent("thinking", "酒店推荐专家正在根据您的住宿偏好搜索酒店...", null));
                    String result = new HotelAgent(llm, mcp.listTools())
                            .search(request.destination(), request.hotelPreference());
                    eventSink.accept(new SseEvent("tool_result", "[酒店推荐专家] 已完成酒店推荐",
                            preview(result, 300)));
                    return result;
                }
            }, subTasks);

            CompletableFuture.allOf(attrF, weatherF, hotelF).join();
            attractionInfo = attrF.join();
            weatherInfo    = weatherF.join();
            hotelInfo      = hotelF.join();

        } catch (CompletionException e) {
            throw new RuntimeException("子 Agent 执行失败: " + e.getCause().getMessage(), e.getCause());
        } finally {
            subTasks.shutdown();
        }

        // ── PlannerAgent 整合并生成行程（无需 MCP）─────────────────────────────
        eventSink.accept(new SseEvent("thinking", "行程规划专家正在整合信息，生成完整行程...", null));
        StringBuilder planText = new StringBuilder();

        new PlannerAgent(llm).stream(
                buildPlanTask(request, days, attractionInfo, weatherInfo, hotelInfo),
                token -> {
                    planText.append(token);
                    if (!token.isBlank()) {
                        eventSink.accept(new SseEvent("token", token, null));
                    }
                });

        TravelPlan plan = extractTravelPlan(planText.toString());
        eventSink.accept(new SseEvent("complete", "行程规划完成！", plan));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private McpClientConnector openAmapMcp() {
        if (amapKey != null && !amapKey.isBlank()) {
            return McpClientConnector.stdio("npx",
                    Map.of("AMAP_MAPS_API_KEY", amapKey),
                    "-y", AMAP_MCP_PACKAGE);
        }
        return McpClientConnector.stdio("npx", "-y", AMAP_MCP_PACKAGE);
    }

    private String buildPlanTask(TravelRequest req, long days,
                                  String attractionInfo, String weatherInfo, String hotelInfo) {
        return """
                请根据以下各专家收集的信息，为用户规划 %d 天的完整旅行计划：

                【用户原始需求】
                - 目的地：%s
                - 出发日期：%s
                - 返回日期：%s
                - 住宿偏好：%s
                - 个人偏好：%s

                【景点搜索专家的报告】
                %s

                【天气查询专家的报告】
                %s

                【酒店推荐专家的报告】
                %s

                请整合以上信息，生成完整的 JSON 旅行计划。days 数组必须包含完整的 %d 天，每天安排 2-3 个不重复的景点。
                """.formatted(
                days,
                req.destination(), req.startDate(), req.endDate(),
                req.hotelPreference() != null ? req.hotelPreference() : "舒适型",
                req.preferences()     != null ? req.preferences()     : "无特殊要求",
                attractionInfo, weatherInfo, hotelInfo,
                days
        );
    }

    private static long calculateDays(String startDate, String endDate) {
        try {
            return Math.max(1, ChronoUnit.DAYS.between(
                    LocalDate.parse(startDate), LocalDate.parse(endDate)));
        } catch (Exception e) {
            return 3;
        }
    }

    private static String preview(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private TravelPlan extractTravelPlan(String response) {
        try {
            int start = response.indexOf("```json");
            int end   = response.lastIndexOf("```");
            if (start >= 0 && end > start + 7) {
                return MAPPER.readValue(response.substring(start + 7, end).strip(), TravelPlan.class);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
