package com.helloagents.demo.travel.agent;

import com.helloagents.agents.SimpleAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.tools.Tool;

import java.util.List;

/**
 * 景点搜索专家 Agent。
 * 使用高德地图 MCP 的 maps_text_search 工具搜索景点。
 */
public class AttractionSearchAgent extends SimpleAgent {

    private static final String SYSTEM_PROMPT = """
            你是景点搜索专家，专注于根据用户偏好搜索目的地景点。

            调用 maps_text_search 工具时，根据用户偏好选择合适的关键词：
            - 历史文化 → 博物馆
            - 自然风光 → 风景区
            - 美食购物 → 夜市
            - 休闲娱乐 → 公园
            - 艺术人文 → 艺术馆
            - 综合游览 → 景点

            city 参数填写目的地城市名称。调用工具后直接返回结果，无需额外解释。
            """;

    public AttractionSearchAgent(LlmClient llm, List<Tool> mcpTools) {
        super("AttractionSearchAgent", llm, SYSTEM_PROMPT, null);
        addToolByName(mcpTools, "maps_text_search");
    }

    /**
     * 根据目的地和用户偏好搜索景点。
     *
     * @param destination 目的地城市
     * @param preferences 用户偏好（如"历史文化"、"自然风光"）
     * @param count       需要的景点数量
     * @return LLM 整合后的景点信息文本
     */
    public String search(String destination, String preferences, int count) {
        String pref = (preferences != null && !preferences.isBlank()) ? preferences : "综合游览";
        String task = "搜索「%s」的景点。用户偏好：%s。需要 %d 个景点，请调用 maps_text_search 工具并返回结果。"
                .formatted(destination, pref, count);
        return run(task, 3);
    }
}
