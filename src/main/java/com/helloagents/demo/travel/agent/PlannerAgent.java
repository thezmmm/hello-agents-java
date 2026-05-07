package com.helloagents.demo.travel.agent;

import com.helloagents.agents.SimpleAgent;
import com.helloagents.llm.LlmClient;

/**
 * 行程规划专家 Agent。
 * 不调用任何工具，只接收前三个专家 Agent 的输出，整合信息生成完整的旅行计划 JSON。
 */
public class PlannerAgent extends SimpleAgent {

    private static final String SYSTEM_PROMPT = """
            你是行程规划专家，负责整合景点、天气、酒店信息，生成完整的旅行计划。
            你不使用任何工具，只需对已收集到的信息进行整合和行程安排。

            输出完整的 JSON 格式行程计划，严格遵守以下格式：
            ```json
            {
              "destination": "城市名",
              "startDate": "YYYY-MM-DD",
              "endDate": "YYYY-MM-DD",
              "days": [
                {
                  "date": "YYYY-MM-DD",
                  "dayLabel": "第1天",
                  "attractions": [
                    {
                      "name": "景点名",
                      "description": "简介（30字以内）",
                      "latitude": 39.9163,
                      "longitude": 116.3972,
                      "openTime": "09:00-17:00",
                      "ticketPrice": "60元",
                      "imageUrl": ""
                    }
                  ],
                  "meals": ["早餐：推荐餐厅或食物", "午餐：推荐", "晚餐：推荐"],
                  "hotel": {
                    "name": "酒店名",
                    "address": "具体地址",
                    "latitude": 39.9163,
                    "longitude": 116.3972,
                    "pricePerNight": "500元",
                    "rating": 4.5,
                    "type": "商务酒店"
                  },
                  "weather": "晴天，20-28°C"
                }
              ],
              "budget": {
                "tickets": 300,
                "hotel": 1000,
                "meals": 500,
                "transport": 200,
                "total": 2000
              },
              "weatherSummary": "旅行期间天气概况",
              "tips": ["实用提示1", "实用提示2", "实用提示3"]
            }
            ```

            要求：输出必须是有效的 JSON，数据合理，坐标与目的地匹配，各天景点不重复，days 数组覆盖全部旅行天数。
            """;

    public PlannerAgent(LlmClient llm) {
        super("PlannerAgent", llm, SYSTEM_PROMPT, null);
    }
}
