package com.helloagents.demo.travel.agent;

import com.helloagents.agents.SimpleAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.tools.Tool;

import java.util.List;

/**
 * 天气查询专家 Agent。
 * 使用高德地图 MCP 的 maps_weather 工具查询天气。
 */
public class WeatherQueryAgent extends SimpleAgent {

    private static final String SYSTEM_PROMPT = """
            你是天气查询专家，专注于查询城市的天气预报。
            接收城市名称后，调用 maps_weather 工具查询天气，直接返回工具结果，无需额外解释。
            """;

    public WeatherQueryAgent(LlmClient llm, List<Tool> mcpTools) {
        super("WeatherQueryAgent", llm, SYSTEM_PROMPT, null);
        addToolByName(mcpTools, "maps_weather");
    }

    /**
     * 查询指定城市和日期范围的天气预报。
     *
     * @param destination 目的地城市
     * @param startDate   出发日期（YYYY-MM-DD）
     * @param endDate     返回日期（YYYY-MM-DD）
     * @return 天气预报文本
     */
    public String query(String destination, String startDate, String endDate) {
        String task = "查询「%s」从 %s 到 %s 的天气预报，请调用 maps_weather 工具并返回结果。"
                .formatted(destination, startDate, endDate);
        return run(task, 3);
    }
}
