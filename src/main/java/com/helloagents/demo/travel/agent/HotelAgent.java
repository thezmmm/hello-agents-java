package com.helloagents.demo.travel.agent;

import com.helloagents.agents.SimpleAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.tools.Tool;

import java.util.List;

/**
 * 酒店推荐专家 Agent。
 * 使用高德地图 MCP 的 maps_text_search 工具搜索酒店。
 */
public class HotelAgent extends SimpleAgent {

    private static final String SYSTEM_PROMPT = """
            你是酒店推荐专家，专注于搜索目的地的酒店信息。
            按照任务中指定的关键词调用 maps_text_search 工具，直接返回工具结果，无需额外解释。
            """;

    public HotelAgent(LlmClient llm, List<Tool> mcpTools) {
        super("HotelAgent", llm, SYSTEM_PROMPT, null);
        addToolByName(mcpTools, "maps_text_search");
    }

    /**
     * 根据目的地和预算偏好搜索酒店。
     *
     * @param destination 目的地城市
     * @param hotelPreference 用户住宿偏好（如"经济型"、"豪华型"）
     * @return 酒店推荐信息文本
     */
    public String search(String destination, String hotelPreference) {
        String keyword = resolveKeyword(hotelPreference);
        String task = "请调用 maps_text_search 工具，keywords 参数填「%s」，city 参数填「%s」，返回结果。"
                .formatted(keyword, destination);
        return run(task, 3);
    }

    private static String resolveKeyword(String hotelPreference) {
        if (hotelPreference == null || hotelPreference.isBlank()) return "商务酒店";
        return switch (hotelPreference) {
            case "经济型", "经济", "budget" -> "经济型酒店";
            case "豪华型", "豪华", "luxury" -> "五星级酒店";
            default                         -> "商务酒店";
        };
    }
}
