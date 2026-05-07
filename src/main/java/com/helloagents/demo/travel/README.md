# 智能旅行助手

hello-agents-java Demo —— 基于多 Agent + 高德地图 MCP 的全栈旅行规划应用。

## 功能

- **智能行程规划**：输入目的地、日期、预算，四个专家 Agent 协作生成完整行程
- **地图可视化**：地图标注景点位置，绘制每日游览路线
- **实时推理日志**：流式展示各 Agent 的工作进度与工具调用结果

## 架构

```
前端 (React + TypeScript + Vite)
  └── SSE 流式接收

后端 (Spring Boot)
  └── TravelOrchestrator（编排器）
        ├── AttractionSearchAgent  景点搜索专家
        │     └── maps_text_search（高德 MCP）
        ├── WeatherQueryAgent      天气查询专家
        │     └── maps_weather（高德 MCP）
        ├── HotelAgent             酒店推荐专家
        │     └── maps_text_search（高德 MCP）
        └── PlannerAgent           行程规划专家（纯 LLM，无工具）

  高德地图 MCP Server（@amap/amap-maps-mcp-server，本地 npx 子进程）
```

**编排流程**：
1. 启动高德 MCP 子进程，`listTools()` 一次获取全部工具
2. 将工具分发给前三个 Agent，依次运行收集数据
3. MCP 连接关闭（数据收集已完成）
4. PlannerAgent 整合三份报告，输出 JSON 行程计划

## 前置条件

| 条件 | 说明 |
|------|------|
| Node.js | `npx` 可用，用于启动高德 MCP 子进程 |
| `AMAP_MAPS_API_KEY` | 高德地图 API Key，从 [lbs.amap.com](https://lbs.amap.com) 申请 |
| LLM API Key | 通过 `.env` 或环境变量配置（参考根目录说明） |

## 启动

**配置 API Key**（二选一）：

```bash
# 方式一：环境变量（推荐）
export AMAP_MAPS_API_KEY=your_amap_key

# 方式二：Spring 配置文件
# src/main/resources/application-local.properties
amap.api.key=your_amap_key
```

**启动后端**（项目根目录执行）：

```bash
mvn exec:java \
  -Dexec.mainClass="com.helloagents.demo.travel.TravelAssistantApp" \
  -Dspring.profiles.active=local
# 服务启动在 http://localhost:8090
```

**启动前端**：

```bash
cd src/main/java/com/helloagents/demo/travel/travel-frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

## 目录结构

```
travel/
├── TravelAssistantApp.java          Spring Boot 启动类
├── config/TravelAgentConfig.java    Bean 配置 + CORS
├── controller/TravelController.java REST 接口 + SSE
├── agent/
│   ├── TravelOrchestrator.java      多 Agent 编排器，管理 MCP 生命周期
│   ├── AttractionSearchAgent.java   景点搜索专家（maps_text_search）
│   ├── WeatherQueryAgent.java       天气查询专家（maps_weather）
│   ├── HotelAgent.java              酒店推荐专家（maps_text_search）
│   └── PlannerAgent.java            行程规划专家（纯 LLM）
├── model/                           7 个数据模型（record）
└── travel-frontend/                 React 前端
    ├── src/components/              TravelForm / StreamLog / MapView / ItineraryPanel / BudgetPanel
    ├── src/hooks/useTravelPlan.ts   SSE 状态管理
    └── src/api/travelApi.ts         后端请求封装
```

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/travel/plan` | 提交规划请求，返回 SSE 流 |
| `GET`  | `/api/travel/health` | 健康检查 |

**请求体**：

```json
{
  "destination": "北京",
  "startDate": "2026-06-01",
  "endDate": "2026-06-04",
  "budget": "标准",
  "preferences": "历史文化"
}
```

**SSE 事件类型**：

| 类型 | 含义 |
|------|------|
| `thinking` | Agent 阶段提示 |
| `tool_result` | 某个 Agent 完成后的结果摘要 |
| `token` | PlannerAgent 流式输出的文本片段 |
| `complete` | 规划完成，携带完整 `TravelPlan` JSON |
| `error` | 异常信息 |
